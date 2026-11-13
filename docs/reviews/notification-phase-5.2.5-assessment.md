# Phase 5.2.5 Technical Assessment & Implementation Design: Scheduled Pending Event Reconciliation

**Target Module**: `notification`  
**Related Modules**: `delivery.api` (`ParcelEvents`)  
**Framework Versions**: Spring Boot 4.1.2, Java 21, PostgreSQL 18  
**Status**: Technical Assessment & Implementation Design (Read-Only)  
**Date**: November 2026

---

## 1. Context and Objective

In Phase 5.2.3 and Phase 5.2.4, we implemented the core `NotificationEventProcessor` and the RabbitMQ listener `NotificationEventListener`.
When an event arrives out-of-order ($sequence > expected$), `NotificationEventProcessor`:
1. Buffers the incoming event in `notification_pending_events` (status `PENDING`, $retry\_count = 0$).
2. Queries Delivery's public facade `ParcelEvents.eventsBetween(parcelId, expected, sequence - 1)`.
3. If Delivery's outbox history for the missing sequence range is incomplete (e.g., intermediate events are still in-flight or delayed in the outbox relay), the buffered event remains in `notification_pending_events` awaiting either:
   - Arrival of the missing events over RabbitMQ, or
   - Scheduled gap reconciliation.

**Phase 5.2.5 Objective**: Design a scheduled reconciliation worker that periodically scans for buffered pending events, queries `ParcelEvents.eventsBetween(...)` to resolve sequence gaps, applies recovered events in strict order, advances the sequence watermark, drains contiguous buffered events, and quarantines permanently missing events after 5 attempts as `UNRESOLVED`.

---

## 2. Core Worker Requirements & Policy Mapping

| Requirement | Value / Rule | Implementation Design |
|---|---|---|
| **Schedule Period** | Every **30 seconds** | `tasks.addFixedDelayTask(worker::reconcile, Duration.ofSeconds(30))` via `SchedulingConfigurer`, with a configurable property `swiftdrop.notification.reconciliation.interval=PT30S`. Fixed delay prevents overlapping runs if a cycle takes longer. |
| **Grace Period** | At least **15 seconds** after arrival | Cutoff timestamp: $T_{cutoff} = now() - 15\text{s}$. The query uses `findByStatusAndReceivedAtBeforeOrderByReceivedAtAsc(PENDING, T_cutoff, Limit)`. Events buffered $< 15\text{s}$ ago are skipped, allowing normal in-flight AMQP delivery to settle without wasted DB roundtrips. |
| **Max Attempts** | Maximum of **5 attempts** | Tracked in entity field `retryCount`. Incremented upon each reconciliation attempt where the gap remains unresolved. |
| **Quarantine Rule** | 5th failed attempt $\rightarrow$ `UNRESOLVED` | If attempt #5 cannot resolve the gap: `event.recordReconciliationAttempt(now)` sets `retryCount = 5`; then `event.markUnresolved()` transitions status to `UNRESOLVED`. The event is kept indefinitely with its original JSONB payload. Logged at `ERROR` level. |
| **Recovery Boundary** | Delivery public API only | Queries `com.swiftdrop.delivery.api.ParcelEvents#eventsBetween(parcelId, fromSeq, toSeq)`. Never accesses Delivery tables or entities. |
| **Locking Strategy** | Per-parcel PostgreSQL advisory lock | Uses non-blocking `consumerStateRepository.tryAcquireParcelLock(parcelId)`. If another transaction (e.g. RabbitMQ listener or another worker instance) holds the lock, skip the parcel for this poll. |
| **Concurrency Safety** | Multi-instance and listener safety | Transaction-level advisory lock (`pg_try_advisory_xact_lock`) prevents races with the listener or concurrent worker instances. |
| **Batch Size & Limits** | Configurable, bounded poll | Limit discovery to e.g. 50 parcels per poll (`Limit.of(batchSize)`) to avoid long-running transactions. |

---

## 3. Worker Architecture & Processing Flow

### 3.1 Reconciliation Flow Diagram

```
Scheduled Trigger (Every 30s fixed delay)
                 │
                 ▼
Find pending events awaiting reconciliation:
status = 'PENDING' AND received_at <= (now - 15s)
(Ordered by received_at ASC, bounded by batch limit)
                 │
                 ▼
Group eligible pending events by parcel_id
                 │
                 ▼
For each unique parcelId:
┌────────────────────────────────────────────────────────────────────────┐
│ Open independent Transaction (REQUIRES_NEW or TransactionTemplate)     │
│                                                                        │
│ 1. Try acquire parcel advisory lock:                                   │
│    consumerStateRepository.tryAcquireParcelLock(parcelId)              │
│    ├── FALSE: Another thread holds lock (e.g. listener processing).    │
│    │          Skip parcel; proceed to next parcel.                     │
│    │                                                                   │
│    └── TRUE: Lock acquired for duration of transaction.                │
│                                                                        │
│ 2. Load current consumer state:                                        │
│    NotificationConsumerState state = repository.findById(parcelId)     │
│    currentWatermark = (state != null) ? state.getLastApplied() : 0     │
│    expectedSequence = currentWatermark + 1                             │
│                                                                        │
│ 3. Fetch all pending events for this parcel:                           │
│    List<NotificationPendingEvent> pending =                            │
│         pendingEventRepo.findByParcelIdOrderBySequenceAsc(parcelId)    │
│                                                                        │
│ 4. Check if lowest pending sequence <= currentWatermark:               │
│    ├── If so, delete obsolete pending rows (already applied).          │
│                                                                        │
│ 5. Identify lowest missing gap:                                        │
│    targetSequence = lowest pending.sequence > expectedSequence        │
│                                                                        │
│ 6. Query Delivery public API:                                          │
│    events = parcelEvents.eventsBetween(parcelId,                       │
│                                        expectedSequence,               │
│                                        targetSequence - 1)             │
│                                                                        │
│ 7. Evaluate Recovery:                                                  │
│    ├── Gaps recovered completely:                                      │
│    │     - Apply each recovered event in order                         │
│    │     - Update sequence watermark                                   │
│    │     - Apply target pending event & delete pending entity          │
│    │     - Drain contiguous pending events that follow                 │
│    │                                                                   │
│    └── Gaps still missing / incomplete:                                │
│          - For each pending event in this parcel:                      │
│              event.recordReconciliationAttempt(now)                    │
│              if (event.getRetryCount() >= 5) {                         │
│                  event.markUnresolved()                                │
│                  log.error("Exhausted 5 attempts. Quarantined...")      │
│              }                                                         │
│              pendingEventRepo.save(event)                              │
│                                                                        │
│ 8. Commit Transaction & Release Advisory Lock                          │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Deep Dive: Key Technical Decisions

### 4.1 Non-Blocking Advisory Locking: `tryAcquireParcelLock`
- `NotificationConsumerStateRepository` provides both `acquireParcelLock` (blocking) and `tryAcquireParcelLock` (non-blocking using `pg_try_advisory_xact_lock`).
- **Why `tryAcquireParcelLock` is critical for the scheduled worker**:
  - If a RabbitMQ listener thread is currently processing an event for `parcelId`, it holds `pg_advisory_xact_lock(parcelId)`.
  - If the scheduled worker used a blocking lock, the entire reconciliation batch would block waiting for the listener to finish.
  - With `tryAcquireParcelLock`, if the parcel is currently locked by a listener, the worker immediately skips it and moves to the next parcel. The listener itself will either advance the watermark and drain pending events, or the worker will reconcile it on the next 30-second cycle.

### 4.2 Grace Period vs. Schedule Timing
- **15-Second Grace Period**:
  - Handled directly by the database partial index query:
    `ix_notification_pending_events_reconciliation` on `(received_at, parcel_id) WHERE status = 'PENDING'`.
  - Condition: `status = 'PENDING' AND received_at <= :cutoff` where `cutoff = clock.instant().minusSeconds(15)`.
  - Even if an event arrives at second 29, the worker running at second 30 will calculate `cutoff = 15` and will ignore this event until the 60-second run.
- **30-Second Fixed-Delay Schedule**:
  - Configured using Spring's `SchedulingConfigurer` with `tasks.addFixedDelayTask(worker::reconcile, interval)`.
  - Fixed delay guarantees that if a reconciliation cycle takes 4 seconds to process 50 parcels, the next run occurs 30 seconds after completion, avoiding thread pile-up.

### 4.3 Exact Retry Count and Quarantine Semantics
The entity `NotificationPendingEvent` defines:
```java
public void recordReconciliationAttempt(Instant reconciledAt) {
    this.retryCount++;
    this.lastReconciledAt = Objects.requireNonNull(reconciledAt, "reconciledAt");
}

public void markUnresolved() {
    this.status = NotificationPendingEventStatus.UNRESOLVED;
}
```
**Progression across runs**:
- Arrival: `retryCount = 0`, `status = PENDING`.
- Poll 1 (after $\ge 15\text{s}$): Incomplete $\rightarrow$ `retryCount = 1`, `lastReconciledAt = T1`, `status = PENDING`.
- Poll 2 (+30s): Incomplete $\rightarrow$ `retryCount = 2`, `lastReconciledAt = T2`, `status = PENDING`.
- Poll 3 (+30s): Incomplete $\rightarrow$ `retryCount = 3`, `lastReconciledAt = T3`, `status = PENDING`.
- Poll 4 (+30s): Incomplete $\rightarrow$ `retryCount = 4`, `lastReconciledAt = T4`, `status = PENDING`.
- Poll 5 (+30s): Incomplete $\rightarrow$ `retryCount = 5`, `lastReconciledAt = T5`, **`status = UNRESOLVED`**.
- Once marked `UNRESOLVED`:
  - It drops out of the partial index `WHERE status = 'PENDING'`.
  - The worker query will never select it again.
  - The original JSONB payload is preserved indefinitely for manual audit/replay.
  - Emits:
    ```java
    log.error("Pending event {} for parcel {} sequence {} failed 5 reconciliation attempts. Quarantined as UNRESOLVED.",
            event.getEventId(), parcelId, event.getSequence());
    ```

### 4.4 Transaction Boundaries & Atomicity
- **Per-Parcel Transaction**:
  - Do **not** wrap the entire 30-second poll in one giant transaction.
  - Each parcel is reconciled in its own isolated transaction (`TransactionTemplate` or service method with `@Transactional(propagation = Propagation.REQUIRES_NEW)`).
  - Benefits:
    - Advisory lock is acquired and released per parcel.
    - If one parcel fails (e.g. database timeout), other parcels commit independently.
    - Locks are held for milliseconds rather than the duration of the entire batch.

### 4.5 Draining & Watermark Invariants
- When missing gap events are recovered from `parcelEvents.eventsBetween(...)`, they are applied in sequence order.
- Each applied event:
  - Generates an in-app `NotificationRecord` (if eligible customer parcel).
  - Advances `NotificationConsumerState.lastAppliedSequence`.
  - Deletes any corresponding pending event entity for that sequence.
- After the recovered range is applied, the buffered trigger event is applied and deleted.
- Finally, the worker invokes `NotificationEventProcessor.drainContiguousPendingEvents(...)` to advance past any subsequent contiguous sequences (e.g. $S+1, S+2$) buffered in `notification_pending_events`.

---

## 5. Architectural Alignment & Constraints Checklist

| Constraint / Guideline | Verification |
|---|---|
| **No Delivery table access** | Only calls `ParcelEvents.eventsBetween(parcelId, fromSeq, toSeq)`. Verified compliant with ADR 0003 & ADR 0004 §4.3. |
| **No Delivery lifecycle mutation** | Read-only interaction with Delivery. Verified. |
| **Module Boundaries** | `NotificationReconciliationService` resides in `com.swiftdrop.notification.application`, respecting `ModuleBoundaryTest`. |
| **Advisory Lock Key Consistency** | Reuses `consumerStateRepository.tryAcquireParcelLock(parcelId)` which hashes `CAST(parcelId AS text)` with seed 0, identical to `NotificationEventProcessor.acquireParcelLock(parcelId)`. |
| **No extra infrastructure** | Uses Spring `@Scheduled` / `SchedulingConfigurer` with PostgreSQL. No Redis or external scheduler required. |
| **Configuration Toggle** | Enabled by default; toggled via `swiftdrop.notification.reconciliation.enabled=true`. |

---

## 6. Implementation Plan: Files to Create or Modify

### 6.1 Files to Create
1. **`NotificationReconciliationProperties.java`**
   - Package: `com.swiftdrop.notification.infrastructure`.
   - Properties:
     - `swiftdrop.notification.reconciliation.enabled` (default: `true`).
     - `swiftdrop.notification.reconciliation.interval` (default: `PT30S`).
     - `swiftdrop.notification.reconciliation.grace-period` (default: `PT15S`).
     - `swiftdrop.notification.reconciliation.batch-size` (default: `50`).
     - `swiftdrop.notification.reconciliation.max-attempts` (default: `5`).
2. **`NotificationReconciliationService.java`**
   - Package: `com.swiftdrop.notification.application`.
   - Methods:
     - `int reconcileBatch()`: Finds pending events older than grace period, groups by parcel, and delegates each parcel to `reconcileParcel(parcelId)`.
     - `boolean reconcileParcel(UUID parcelId)`: Decorated with `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
3. **`NotificationReconciliationConfiguration.java`**
   - Package: `com.swiftdrop.notification.infrastructure`.
   - `@ConditionalOnProperty(name = "swiftdrop.notification.reconciliation.enabled", havingValue = "true", matchIfMissing = true)`
   - Implements `SchedulingConfigurer` adding fixed-delay task.
4. **`NotificationReconciliationServiceTest.java`** (Unit Test)
   - Tests grace period filtering, retry counter increment, 5th attempt quarantine transition, parcel lock skipping, and gap application logic with mocks.
5. **`NotificationReconciliationIT.java`** (Testcontainers Integration Test)
   - Real PostgreSQL container:
     - Scenario 1: Pending event with missing gap recovered from mock `ParcelEvents` after grace period.
     - Scenario 2: Event buffered $< 15\text{s}$ is ignored.
     - Scenario 3: Gap still missing; attempts increment up to 5, then status transitions to `UNRESOLVED`.
     - Scenario 4: Concurrent lock collision: when a transaction holds `acquireParcelLock`, `reconcileParcel` skips without blocking.

### 6.2 Files to Modify
1. **`NotificationEventProcessor.java`**
   - Refactor `applyEvent(...)` and `drainContiguousPendingEvents(...)` to package-private or provide a dedicated method `reconcileParcel(UUID parcelId)` inside `NotificationEventProcessor` to keep all event application and watermark advancement centralized.

---

## 7. Risks, Edge Cases & Decisions for Approval

### Decision 1: Placement of Reconciliation Logic
- **Option A (Recommended)**: Place `reconcileParcel(UUID parcelId)` inside `NotificationEventProcessor` (or a collaborating package-private component in `com.swiftdrop.notification.application`).
  - *Rationale*: Reuses `applyEvent`, `updateWatermark`, `drainContiguousPendingEvents`, advisory lock helpers, and template resolvers, avoiding code duplication and keeping all watermark advancement unified.
- **Option B**: Duplicate event application and draining logic in a new `NotificationReconciliationService`.

### Decision 2: Retry Count Increment Timing
- When gap reconciliation fails:
  - Attempt 1 ($T_0 + 15\text{s}$): `retryCount = 1`, `status = PENDING`.
  - Attempt 2 ($T_0 + 45\text{s}$): `retryCount = 2`, `status = PENDING`.
  - Attempt 3 ($T_0 + 75\text{s}$): `retryCount = 3`, `status = PENDING`.
  - Attempt 4 ($T_0 + 105\text{s}$): `retryCount = 4`, `status = PENDING`.
  - Attempt 5 ($T_0 + 135\text{s}$): `retryCount = 5`, `status = UNRESOLVED`.
- Quarantined events immediately drop out of the partial index query `WHERE status = 'PENDING'`, preventing further polling.

