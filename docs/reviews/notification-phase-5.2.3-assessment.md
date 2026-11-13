# Phase 5.2.3 Technical Assessment & Implementation Design: Core Notification Event Processor

**Target Module**: `notification`  
**Dependencies**: `delivery.api`, `identity.api`  
**Status**: Technical Assessment (Read-Only)  
**Date**: November 2026

---

## 1. Verified Existing Contracts & Available Methods

### 1.1 Notification Domain Repositories & Entities
- **`NotificationConsumerStateRepository`**:
  - `acquireParcelLock(UUID parcelId)`: Executes `SELECT pg_advisory_xact_lock(hashtextextended(CAST(:parcelId AS text), 0))` to hold a transaction-level lock on the parcel key.
  - `findById(UUID parcelId)`: Loads the current watermark row (`NotificationConsumerState`).
  - `save(NotificationConsumerState state)`: Persists or updates the watermark.
- **`NotificationConsumerState`**:
  - Constructor: `NotificationConsumerState(UUID parcelId, int lastAppliedSequence, Instant updatedAt)`. Enforces $lastAppliedSequence \ge 1$.
  - Mutator: `advanceSequence(int sequence, Instant updatedAt)`. Enforces $sequence > lastAppliedSequence$.
  - `getId()`: Returns `parcelId`.
- **`NotificationPendingEventRepository`**:
  - `findByParcelIdAndSequence(UUID parcelId, int sequence)`: Exact lookup for buffered events.
  - `findByParcelIdOrderBySequenceAsc(UUID parcelId)`: Ordered traversal of buffered events.
  - `save(NotificationPendingEvent pendingEvent)`: Buffers out-of-order payloads.
  - `delete(NotificationPendingEvent pendingEvent)`: Removes buffered event after it is applied.
- **`NotificationRecordRepository`**:
  - `existsByEventIdAndChannelAndRecipientUserId(UUID eventId, NotificationChannel channel, UUID recipientUserId)`: Pre-check for idempotency.
  - `save(NotificationRecord record)`: Persists user-facing in-app notification.
- **`NotificationRecord`**:
  - Constructor: `NotificationRecord(eventId, recipientUserId, parcelId, trackingNumber, title, message, channel, deliveryStatus, createdAt)`.

### 1.2 Application Services & Parsers
- **`ParcelStatusEventParser`**:
  - `parse(String json)`: Validates and parses raw JSON strings into `ParcelStatusChanged`. Throws `EventParsingException` on invalid JSON, UUIDs, unsupported event types, or missing mandatory fields.
- **`NotificationTemplateResolver`**:
  - `resolve(ParcelStatusChanged event)`: Returns `Optional<NotificationContent>` containing rendered `title` and `message` for notifying statuses (`CREATED`, `PICKED_UP`, `OUT_FOR_DELIVERY`, `DELIVERY_ATTEMPT_FAILED`, `DELIVERED`, `RETURNED`, `CANCELLED`, `FAILED`), or `Optional.empty()` for non-notifying milestones (`AT_HUB`, `IN_TRANSIT`, `RETURNING`).

### 1.3 External Module APIs
- **`identity.api.UserDirectory`**:
  - `Optional<UserSummary> findUser(UUID id)`: Returns user profile with `enabled()` boolean flag.
- **`delivery.api.ParcelEvents`**:
  - `List<ParcelStatusChanged> eventsBetween(UUID parcelId, int fromSequence, int toSequence)`: Reconstructs events from Delivery history in ascending sequence order.

---

## 2. Proposed Processing Algorithm in Sequence Order

The processor entry point `process(ParcelStatusChanged event, String rawPayload)` executes within `@Transactional(isolation = Isolation.READ_COMMITTED)`:

```
                                    Start process(event, rawPayload)
                                                    │
                                                    ▼
                       Step 1: Acquire Lock: acquireParcelLock(event.data().parcelId())
                                                    │
                                                    ▼
                       Step 2: Read Consumer State: findById(event.data().parcelId())
                               lastApplied = state.map(getLastAppliedSequence).orElse(0)
                               expected = lastApplied + 1
                                                    │
                         ┌──────────────────────────┼──────────────────────────┐
                         ▼                          ▼                          ▼
               Case 1: S <= lastApplied      Case 2: S == expected      Case 3: S > expected
                (Duplicate / Replay)           (In-Order Event)            (Gap Detected)
                         │                          │                          │
                         ▼                          ▼                          ▼
                   Log debug &                Apply Event S              Buffer Event S in
                  return (No-op)            Advance Watermark         notification_pending_events
                                                    │                          │
                                                    │                          ▼
                                                    │               Call ParcelEvents.eventsBetween
                                                    │                    (expected, S - 1)
                                                    │                          │
                                                    │                 Complete & Continuous?
                                                    │                 ├── Yes: Apply gap events,
                                                    │                 │        advance watermark,
                                                    │                 │        apply Event S,
                                                    │                 │        advance watermark,
                                                    │                 │        delete pending S
                                                    │                 └── No:  Retain pending S,
                                                    │                          do not advance watermark
                                                    ▼
                                     Step 3: Drain Contiguous Pending
                                           Events in Buffer
                                        (Loop S+1, S+2, ...)
                                                    │
                                                    ▼
                                          Commit Transaction
```

### Detailed Execution Steps:

1. **Step 1 — Transaction & Concurrency Lock**:
   - Immediately call `consumerStateRepository.acquireParcelLock(parcelId)`.
   - Serializes all operations on this parcel across all threads and instances.
2. **Step 2 — Determine Expected Sequence**:
   - Query `consumerStateRepository.findById(parcelId)`.
   - If not found: `lastApplied = 0`, expected sequence is `1`.
   - If found: `lastApplied = state.getLastAppliedSequence()`, expected sequence is `lastApplied + 1`.
3. **Step 3 — Handle Event Cases**:
   - **Case 1: $S \le lastApplied$ (Duplicate / Replay)**:
     - Event was already processed or sequence watermark has advanced beyond it.
     - Also clean up any lingering pending event row for this `(parcelId, S)` if present (e.g. from an earlier out-of-order buffer).
     - Log debug message and return safely without updating records or state.
   - **Case 2: $S == expected$ (Direct In-Order Arrival)**:
     - Check whether an existing pending record exists for `(parcelId, S)` (e.g. previously buffered). If found, delete it so no pending event remains for $S$.
     - Apply event $S$ (evaluate recipient and create notification if applicable).
     - Update / create `NotificationConsumerState` with watermark $S$.
     - Proceed to Step 4 (Draining contiguous buffered events).
   - **Case 3: $S > expected$ (Sequence Gap Detected)**:
     - **Idempotent Pending-Event Insertion**: Check if a pending event record already exists for `(parcelId, S)` using `pendingEventRepo.findByParcelIdAndSequence(parcelId, S)`:
       - If already present: do not insert a duplicate (preventing `uq_notification_pending_events_parcel_sequence` violation). Retain the existing stored payload (idempotency rule: the original payload is canonical).
       - If absent: save a new `NotificationPendingEvent` with payload `rawPayload`, sequence $S$, and status `PENDING`.
     - **Attempt Gap Recovery via Delivery History**: Call `parcelEvents.eventsBetween(parcelId, expected, S - 1)`.
     - **Validate Recovery Result**:
       - Check that list size equals $(S - 1) - expected + 1$.
       - Check that elements are strictly sequential: for index $i$, `sequence == expected + i`.
       - If valid and complete:
         - **Atomic Application**: In the same database transaction:
           1. For each recovered event: apply notification logic, advance watermark, and explicitly delete any matching buffered pending record for `(parcelId, recoveredSeq)` to ensure no pending records remain for sequences at or below the newly advanced watermark.
           2. Apply event $S$: apply notification logic, advance watermark to $S$, and delete $S$ from `notification_pending_events`.
         - Proceed to Step 4.
       - If incomplete (e.g. Delivery history rows have not yet committed or gap cannot be bridged yet):
         - **Transaction Invariant**: Do **not** partially apply events. Leave watermark unchanged.
         - Event $S$ remains safely stored in `notification_pending_events`. Return cleanly so the transaction commits and RabbitMQ can acknowledge the message.
4. **Step 4 — Drain Contiguous Buffered Pending Events**:
   - Once the watermark has reached $W$, check for buffered events at $W + 1$:
     ```java
     while (true) {
         int nextSeq = currentWatermark + 1;
         Optional<NotificationPendingEvent> nextPending = pendingEventRepo.findByParcelIdAndSequence(parcelId, nextSeq);
         if (nextPending.isEmpty()) {
             break;
         }
         NotificationPendingEvent pending = nextPending.get();
         ParcelStatusChanged pendingEvent = parser.parse(pending.getPayload());

         // Buffered Payload Consistency Validation:
         if (!pending.getParcelId().equals(pendingEvent.data().parcelId())
                 || pending.getSequence() != pendingEvent.data().sequence()) {
             // Inconsistent / corrupted payload detected!
             // Mark pending record as UNRESOLVED and abort draining to avoid invalid watermark advancement.
             pending.markUnresolved();
             pendingEventRepo.save(pending);
             log.error("Corrupted pending event payload detected for parcel {} sequence {}. Quarantining.",
                     parcelId, nextSeq);
             break;
         }

         applyEvent(pendingEvent);
         advanceWatermark(pendingEvent.data().sequence(), pendingEvent.occurredAt());
         pendingEventRepo.delete(pending);
         currentWatermark = nextSeq;
     }
     ```

---

## 3. Recipient Resolution & Source Classification

The `applyEvent(ParcelStatusChanged event)` helper enforces strict business boundaries:

### 3.1 CUSTOMER Parcels (`source == ParcelSource.CUSTOMER`)
1. **Validation**:
   - `ownerCustomerId` must not be null. If null, throw `EventParsingException("CUSTOMER parcel event must have non-null ownerCustomerId")`.
2. **User Directory Lookup**:
   - Call `userDirectory.findUser(ownerCustomerId)`.
   - **User Exists & `user.enabled() == true`**:
     - Call `templateResolver.resolve(event)`.
     - If `Optional` contains `NotificationContent`:
       - Create `NotificationRecord` with:
         - `channel = NotificationChannel.IN_APP`
         - `deliveryStatus = NotificationDeliveryStatus.NOT_APPLICABLE`
         - `isRead = false`, `readAt = null`
         - `sentAt = null`, `failureReason = null`
       - Check `existsByEventIdAndChannelAndRecipientUserId` for safety.
       - Call `notificationRecordRepository.save(record)`.
   - **User Missing or `user.enabled() == false`**:
     - Log info/warning.
     - Do not create a `NotificationRecord`.
     - Event remains valid; watermark **still advances**.

### 3.2 MERCHANT Parcels (`source == ParcelSource.MERCHANT`)
- `ownerMerchantId` is an identifier for a merchant business, not a user account.
- **Strict Rule**: `ownerMerchantId` is **never passed** to `UserDirectory`.
- In this phase, no customer in-app notification is created.
- The event remains valid; watermark **still advances**.

---

## 4. Watermark & Initial State Rules

- **Database Constraint**: `notification_consumer_state` enforces `CHECK (last_applied_sequence >= 1)`.
- **Handling First Event (`sequence == 1`)**:
  - Handled in memory using `0` as sentinel.
  - Upon applying sequence 1, instantiate:
    ```java
    NotificationConsumerState state = new NotificationConsumerState(parcelId, 1, event.occurredAt());
    consumerStateRepository.save(state);
    ```
  - Sequence `0` is **never persisted**.
- **Advancing Existing Watermark**:
  - `state.advanceSequence(newSequence, event.occurredAt())`.
  - `consumerStateRepository.save(state)`.

---

## 5. Potential Implementation Risks & Mitigations

| Risk | Mitigation |
| :--- | :--- |
| **Concurrent Initial Events** | Solved by `acquireParcelLock(parcelId)` at transaction start. Because PostgreSQL advisory locks lock on `hashtextextended(uuid::text, 0)`, threads block even before any row exists. |
| **Infinite Gap Buffering** | If a gap cannot be immediately filled via `eventsBetween`, the message is acknowledged so RabbitMQ queues do not stall, and the event remains safely persisted in `notification_pending_events`. Contiguous draining resolves it when missing events arrive. |
| **JSON Deserialization Drift** | When draining from `notification_pending_events`, the stored raw JSON payload is parsed via `ParcelStatusEventParser.parse(json)`, guaranteeing identical validation. |
| **Cross-Module Coupling** | The processor references strictly `delivery.api` (`ParcelEvents`, `ParcelStatusChanged`) and `identity.api` (`UserDirectory`, `UserSummary`), verified by `ModuleBoundaryTest`. |

---

## 6. Proposed Service Structure and Detailed Test Plan for Phase 5.2.3

### Class to Create:
`backend/src/main/java/com/swiftdrop/notification/application/NotificationEventProcessor.java`

**Core Public Method**:
```java
@Transactional
public void process(ParcelStatusChanged event, String rawPayload)
```

**Internal Collaborators**:
- `NotificationConsumerStateRepository` (locking & watermark persistence)
- `NotificationPendingEventRepository` (buffering & contiguous draining)
- `NotificationRecordRepository` (in-app notification persistence)
- `NotificationTemplateResolver` (title/message resolution)
- `ParcelStatusEventParser` (re-parsing buffered payloads)
- `ParcelEvents` (gap recovery facade from Delivery)
- `UserDirectory` (recipient validation facade from Identity)

### Comprehensive Test Plan for Phase 5.2.3:
The unit test suite (`NotificationEventProcessorTest.java`) and Testcontainers integration test suite (`NotificationEventProcessorIT.java`) must cover:
1. **Existing Pending Event Cleanup on Gap Recovery**: When recovered Delivery history events are applied, any matching buffered pending-event records for those same `(parcelId, sequence)` values are deleted, ensuring no pending records remain at or below the advanced watermark.
2. **In-Order Arrival with Existing Pending Record**: When an in-order event arrives that matches an existing pending record (e.g. earlier retry), the event is applied exactly once and the matching pending record is deleted without constraint violations.
3. **Idempotent Pending-Event Insertion**: Re-delivering an out-of-order event when a pending record already exists for `(parcelId, sequence)` does not attempt a duplicate insert and preserves the existing payload.
4. **Buffered Payload Consistency Validation**:
   - A pending payload with a mismatched `parcelId` is rejected, marked `UNRESOLVED`, and the watermark is not advanced.
   - A pending payload with a mismatched `sequence` is rejected, marked `UNRESOLVED`, and the watermark is not advanced.
5. **Incomplete History Handling**: When `eventsBetween()` returns an incomplete range, the watermark remains completely unchanged and the pending event is retained.
6. **Customer vs. Merchant Event Rules**:
   - CUSTOMER event with valid customer creates `NotificationRecord` and advances watermark.
   - CUSTOMER event with null `ownerCustomerId` throws `EventParsingException`.
   - CUSTOMER event with disabled/missing user advances watermark without creating `NotificationRecord`.
   - MERCHANT event advances watermark without querying `UserDirectory` or creating `NotificationRecord`.
7. **Advisory Lock & Concurrency**: Same parcel operations serialize under advisory locking.

---

## 7. Exact File Changes for Phase 5.2.3

### Files to Create:
1. `backend/src/main/java/com/swiftdrop/notification/application/NotificationEventProcessor.java`
2. `backend/src/test/java/com/swiftdrop/notification/application/NotificationEventProcessorTest.java`
3. `backend/src/test/java/com/swiftdrop/notification/application/NotificationEventProcessorIT.java`

### Files to Modify:
- None. (Zero modifications to existing entities, migrations, configuration, or other modules).

