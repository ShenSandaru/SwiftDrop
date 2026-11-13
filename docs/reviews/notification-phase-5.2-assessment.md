# Phase 5.2 Technical Assessment: Notification Event Consumption and Processing

**Target Module**: `notification`  
**Related Modules**: `delivery.api`, `identity.api`  
**Status**: Technical Assessment & Implementation Design (Read-Only)  
**Date**: November 2026

---

## 1. Existing Implementation Findings

### 1.1 Verified Components & Architecture

1. **AMQP Infrastructure (Phase 5.1)**:
   - Configured in `com.swiftdrop.notification.infrastructure.NotificationAmqpConfiguration`.
   - Main durable queue: `notification.parcel-status-changed`, bound to durable topic exchange `swiftdrop.events` via routing key `parcel.status-changed`.
   - Configured with dead-letter arguments: `x-dead-letter-exchange = notification.dlx` and `x-dead-letter-routing-key = notification.parcel-status-changed.dlq`.
   - Dead-letter exchange (`notification.dlx`) and durable queue (`notification.parcel-status-changed.dlq`) declared and bound.
2. **Event Parsing (Phase 5.1)**:
   - `ParcelStatusEventParser` deserializes raw JSON into `com.swiftdrop.delivery.api.ParcelStatusChanged` using tools.jackson / Jackson 3 with ISO-8601 timestamps.
   - Throws `EventParsingException` (with sanitized error details) upon encountering malformed JSON syntax, invalid UUIDs, unsupported schema versions, unsupported event types, or null required fields.
3. **Domain Entities & Persistence (Phase 4)**:
   - `NotificationRecord`: Maps to table `notification_records`. Supports orthogonal `is_read`/`read_at` and `delivery_status`/`sent_at`. Idempotency enforced by unique constraint `uq_notification_records_event_channel_user` (`event_id`, `channel`, `recipient_user_id`).
   - `NotificationConsumerState`: Maps to `notification_consumer_state`. Tracks per-parcel watermark `last_applied_sequence` ($lastApplied \ge 1$) and `updated_at`.
   - `NotificationPendingEvent`: Maps to `notification_pending_events`. Stores out-of-order JSONB payloads, `retry_count`, and `status` (`PENDING`, `UNRESOLVED`). Unique per parcel sequence via `uq_notification_pending_events_parcel_sequence`.
4. **Integration Points**:
   - `identity.api.UserDirectory`: Provides `Optional<UserSummary> findUser(UUID id)` returning `UserSummary(id, fullName, email, roles, enabled)`.
   - `delivery.api.ParcelEvents`: Provides `List<ParcelStatusChanged> eventsBetween(UUID parcelId, int fromSequence, int toSequence)`. Implemented by `ParcelEventHistory`, which queries `parcel_status_history` in `OrderBySequenceAsc` and reconstructs exact outbox event records.

---

## 2. Spring AMQP 4 Behaviour & Reliable Listener Architecture

### 2.1 The `@Transactional` vs AMQP Acknowledgement Myth

In Spring AMQP, placing `@Transactional` on a `@RabbitListener` method manages only the **Spring `PlatformTransactionManager` (PostgreSQL)**. It does **not** coordinate with RabbitMQ channel acknowledgements unless the message listener container is explicitly configured to tie the channel lifecycle to the external transaction.

If standard non-transacted listener configurations are used, acknowledgements operate independently of database commits. In that case, an unhandled exception or an application crash immediately following a database commit could result in either message loss (if acknowledged prematurely) or phantom duplicate dispatches.

### 2.2 Recommended Listener Configuration & Transaction Coordination

To coordinate database commits and message acknowledgements reliably in Spring AMQP 4:

1. **Listener Container Configuration**:
   - Use `SimpleRabbitListenerContainerFactory`.
   - Set `acknowledgeMode = AcknowledgeMode.AUTO`.
   - Configure Spring's `JpaTransactionManager` as the external transaction manager on the container factory:
     ```java
     factory.setTransactionManager(transactionManager);
     ```
   - Investigate setting `channelTransacted = true` alongside the external `PlatformTransactionManager`.
   
2. **Understanding "Best Efforts One Phase Commit" (1PC)**:
   - When an external transaction manager (`JpaTransactionManager`) is configured with a transacted RabbitMQ channel (`channelTransacted = true`), Spring AMQP implements a **Best Efforts 1PC** pattern (often called the *Chained / Synchronized Transactions* pattern).
   - **Crucial Architectural Note**: This is **not a true distributed atomic 2-phase commit (XA)**. A distributed transaction coordinator does not exist.
   - **How Best Efforts 1PC Operates**:
     1. The PostgreSQL JPA transaction is started first.
     2. The message listener executes the business logic (advisory locks, notification records, watermark updates).
     3. The external transaction manager commits the PostgreSQL transaction **first**.
     4. Upon successful database commit, the RabbitMQ transaction is committed (delivering the channel `basicAck`).
   - **Failure & Crash Semantics**:
     - If the database transaction fails or throws an exception, the JPA transaction rolls back, and the RabbitMQ transaction rolls back (the message is not acknowledged and is subject to redelivery/retry).
     - **Vulnerability Window**: If the JVM crashes, the network disconnects, or the broker dies in the tiny time window *after* PostgreSQL commits but *before* the RabbitMQ transaction commit reaches the broker, RabbitMQ will redeliver the message upon reconnection.
     - Because distributed 2PC does not exist, **the consumer must be completely idempotent**: the watermark check and database unique constraints absorb the redelivery safely without duplicate side effects.
   - **Mandatory Integration Test Requirement**:
     - An explicit integration test must verify this exact rollback behavior: simulate a transient database exception during processing, verify that all database changes roll back, verify that RabbitMQ redelivers the message, and confirm that upon successful retry the message is acknowledged and database state is committed cleanly.

### 2.3 Transient Retry vs Fatal Poison-Pill Routing to DLQ

Spring AMQP's listener container should be wired with a `StatefulRetryOperationsInterceptor` or stateless retry interceptor:

1. **Fatal / Poison Messages**:
   - When `ParcelStatusEventParser` throws `EventParsingException` (e.g. malformed JSON, unparseable UUID, unsupported `schemaVersion`), retrying against the database will never succeed.
   - The consumer wraps this in `AmqpRejectAndDontRequeueException` (or configures `FatalExceptionStrategy` with `EventParsingException`).
   - Spring AMQP immediately issues `basicReject(requeue = false)`.
   - RabbitMQ dead-letters the message into `notification.dlx` $\rightarrow$ `notification.parcel-status-changed.dlq`.
2. **Transient Failures**:
   - Transient database connection drops, temporary deadlocks, or lock acquisition timeouts throw retryable exceptions.
   - Configured retry policy:
     - Initial interval: `1000ms`, multiplier: `2.0`, max interval: `5000ms`, max attempts: `3`.
   - If all 3 attempts fail, a `RejectAndDontRequeueRecoverer` triggers `basicReject(requeue = false)`, routing the message to the DLQ and preventing an infinite spin loop on the broker.
3. **Crash Recovery & Idempotency**:
   - If the node crashes after the database transaction has committed but before the AMQP ACK reaches the broker, RabbitMQ redelivers the message upon connection recovery with `redelivered = true`.
   - The notification consumer's watermark check (`sequence <= last_applied_sequence`) immediately detects that the event was already applied, skipping duplicate insertion and committing cleanly, allowing the ACK to finalize without duplicate notification records.

---

## 3. Concurrency Protection: PostgreSQL Transactional Advisory Locks

### 3.1 The Concurrency Challenge
Multiple consumer threads or concurrent delivery status changes for the same parcel could arrive simultaneously:
- For example, `sequence = 1` (`CREATED`) and `sequence = 2` (`PICKED_UP`) could be read concurrently.
- If thread 2 checks `notification_consumer_state` before thread 1 commits, thread 2 finds no row, incorrectly assumes `sequence = 1` is expected, and flags a gap or attempts a race condition `INSERT`.
- Row-level locking (`SELECT ... FOR UPDATE`) fails because the row does not exist yet.

### 3.2 Recommended Solution: 64-Bit Extended Hash Advisory Lock
PostgreSQL provides `hashtextextended(text, bigint)` which hashes a string into an `int8` (`bigint`):
```sql
SELECT pg_advisory_xact_lock(hashtextextended(:parcelIdString, 0))
```
- **Transaction-Scoped (`xact`)**: The lock is acquired exclusively at the start of the consumer's database transaction and is automatically released when the transaction commits or rolls back.
- **Independent per Parcel**: Parcels hash to distinct 64-bit space ($2^{64}$ possibilities), allowing parcels across the entire system to be processed concurrently without thread contention.
- **Serializes Same Parcel**: Any subsequent event or reconciliation worker operating on the same `parcelId` will wait cleanly on `pg_advisory_xact_lock` until the active transaction completes.
- **Advisory Lock Collisions & Correctness**:
  - Because `hashtextextended` maps an arbitrary string to a 64-bit integer, hash collisions are mathematically possible (albeit with negligible probability $\approx 1 / 2^{64}$).
  - **Critical Invariant**: If two distinct parcels happen to produce the exact same 64-bit hash, the only side effect is **temporary, unnecessary serialization** (one parcel's transaction briefly waits for the other to commit).
  - A hash collision **does not and cannot cause incorrect event processing or data corruption**: all database row updates and queries filter strictly by `parcel_id = :parcelId`, so distinct parcels never modify each other's records.

---

## 4. Event Processing & Customer Recipient Resolution

### 4.1 Initial Sequence & Watermark Handling
When handling the first event for a parcel:
- If no row exists in `notification_consumer_state`:
  - The previous sequence is treated **internally as zero (`0`)**.
  - The expected next sequence is strictly **one (`1`)** ($0 + 1$).
  - Once sequence 1 is successfully processed, the initial `notification_consumer_state` entity is created and persisted with `last_applied_sequence = 1`.
  - **Database Constraint Invariant**: Watermark zero (`0`) must **never be persisted** to the database, because database check constraint `ck_notification_consumer_state_sequence` strictly enforces `CHECK (last_applied_sequence >= 1)`.

### 4.2 Customer Ownership Validation & Event Classification

We explicitly classify incoming domain events to prevent confusion between malformed data and valid events that do not generate notifications:

1. **`source == ParcelSource.CUSTOMER`**:
   - **Case A: Valid `ownerCustomerId`**:
     - Lookup customer account via `UserDirectory.findUser(ownerCustomerId)`.
     - **Enabled Customer Account**: If present and `user.enabled() == true`, evaluate the parcel status against the notification matrix. If applicable, persist a `NotificationRecord`. Advance watermark.
     - **Disabled or Missing Customer Account**: If the user record is not found or is disabled (`enabled == false`), no `NotificationRecord` is generated. This is a **valid event scenario** (e.g. an account was deactivated after parcel creation): the event is **not rejected** as malformed; the sequence watermark is **still advanced**.
   - **Case B: Missing `ownerCustomerId` (`null`)**:
     - If `source == ParcelSource.CUSTOMER` but `ownerCustomerId` is `null`, this violates Delivery's domain contract (ADR 0004 §2).
     - This is classified as **malformed event data**: `ParcelStatusEventParser` or the event validator throws `EventParsingException`, rejecting the message to the DLQ.
2. **`source == ParcelSource.MERCHANT`**:
   - `ownerMerchantId` is an identifier for a merchant business profile, **not a user account ID**.
   - In Phase 5, customer notifications are not generated for merchant parcels.
   - This is a **valid event scenario**: no `NotificationRecord` is created, but the sequence watermark is **strictly advanced**.
   - Merchant IDs are never passed to `UserDirectory.findUser(...)`.

### 4.2 Approved Status Notification Matrix & Templates

| `ParcelStatus` | Generate Notification? | Title | Message Template |
| :--- | :---: | :--- | :--- |
| `CREATED` | **Yes** | Parcel Booked | `Your parcel {trackingNumber} has been booked.` |
| `PICKED_UP` | **Yes** | Parcel Picked Up | `Your parcel {trackingNumber} has been collected by our courier.` |
| `AT_HUB` | **No** | - | *Internal operational milestone; advances watermark only.* |
| `IN_TRANSIT` | **No** | - | *Internal transit milestone; advances watermark only.* |
| `OUT_FOR_DELIVERY` | **Yes** | Out for Delivery | `Your parcel {trackingNumber} is out for delivery (Attempt {attempt}).` |
| `DELIVERY_ATTEMPT_FAILED` | **Yes** | Delivery Attempt Failed | `Delivery attempt {attempt} for parcel {trackingNumber} was unsuccessful. Reason: {reason}.` |
| `DELIVERED` | **Yes** | Parcel Delivered | `Your parcel {trackingNumber} has been successfully delivered.` |
| `RETURNING` | **No** | - | *Internal return milestone; advances watermark only.* |
| `RETURNED` | **Yes** | Parcel Returned | `Your parcel {trackingNumber} has been returned to the sender.` |
| `CANCELLED` | **Yes** | Parcel Cancelled | `Your parcel {trackingNumber} has been cancelled.` |
| `FAILED` | **Yes** | Delivery Failed | `Your parcel {trackingNumber} could not be delivered. Reason: {reason}.` |

---

## 5. Sequence Recovery Algorithm & Contiguous Draining

When an event envelope with sequence $S$ arrives for $parcelId$:

```
                      Acquire Advisory Lock: hashtextextended(parcelId, 0)
                                            │
                                            ▼
                           Read notification_consumer_state
                                            │
               ┌────────────────────────────┼────────────────────────────┐
               ▼                            ▼                            ▼
       Case 1: S <= last            Case 2: S == last + 1         Case 3: S > last + 1
       (Duplicate / Replay)             (In-Order Event)             (Gap Detected)
               │                            │                            │
               ▼                            ▼                            ▼
          Log debug                     Process S                   Buffer S in
          & Commit                  Advance watermark        notification_pending_events
                                            │                            │
                                            ▼                            ▼
                                  Drain contiguous from        Call ParcelEvents.eventsBetween
                               notification_pending_events          (last + 1, S - 1)
                                   (S + 1, S + 2, ...)                   │
                                            │                            ▼
                                            │                  Complete? (All returned?)
                                            │                  ├── Yes: Process gap events,
                                            │                  │        advance watermark,
                                            │                  │        drain S and buffered
                                            │                  └── No:  Retain in buffer,
                                            │                           commit & ACK
                                            ▼
                                  Commit Transaction
                                            │
                                            ▼
                                      RabbitMQ ACK
```

### 5.1 Verifying Complete Gap Recovery
When calling `ParcelEvents.eventsBetween(parcelId, expectedSeq, S - 1)`:
- Target count: $N = (S - 1) - expectedSeq + 1$.
- Reconstructed list must satisfy:
  1. `rebuiltList.size() == N`
  2. For $i = 0 \dots N-1$: `rebuiltList.get(i).data().sequence() == expectedSeq + i`.
- If incomplete (e.g. Delivery transaction has not yet committed history rows):
  - Do not advance watermark.
  - Event $S$ remains stored in `notification_pending_events` with `status = 'PENDING'`.
  - Transaction commits and RabbitMQ message is acknowledged (preventing unacknowledged message queues from accumulating on the broker).

---

## 6. Recommended Implementation Breakdown for Phase 5.2

- **Step 1: Advisory Lock & State Repository Extension**
  - Add native query `SELECT pg_advisory_xact_lock(hashtextextended(:parcelId, 0))` to repository.
- **Step 2: Template & Content Formatter**
  - Create `NotificationTemplateResolver` to map status, attempt, and failure reason codes to customer-friendly titles and messages.
- **Step 3: Core Notification Event Processor**
  - Create `NotificationEventProcessor` implementing the transactional sequence logic, watermark advancement, customer lookup, gap recovery via `ParcelEvents`, and draining of buffered pending events.
- **Step 4: RabbitMQ Listener & Error Handler**
  - Create `ParcelStatusNotificationListener` with `@RabbitListener(queues = NotificationAmqpConfiguration.NOTIFICATION_QUEUE)`.
  - Configure exception interception to reject poison pills without requeue.
- **Step 5: Integration Testing with Testcontainers**
  - Create `NotificationEventConsumerIT` running against real RabbitMQ and PostgreSQL Testcontainers, verifying:
    - In-order event processing and notification record creation.
    - Ignored events (`AT_HUB`, `IN_TRANSIT`, `RETURNING`) advancing sequence watermark without creating notification records.
    - Duplicate event handling without constraint violations.
    - Sequence gap recovery via `ParcelEvents.eventsBetween(...)`.
    - DLQ routing for unparseable / fatal payloads.

---

## 7. Files Expected to be Created or Modified

### New Files to Create:
1. `backend/src/main/java/com/swiftdrop/notification/application/NotificationTemplateResolver.java`
2. `backend/src/main/java/com/swiftdrop/notification/application/NotificationEventProcessor.java`
3. `backend/src/main/java/com/swiftdrop/notification/infrastructure/ParcelStatusNotificationListener.java`
4. `backend/src/test/java/com/swiftdrop/notification/application/NotificationTemplateResolverTest.java`
5. `backend/src/test/java/com/swiftdrop/notification/infrastructure/NotificationEventConsumerIT.java`

### Files to Modify:
1. `backend/src/main/java/com/swiftdrop/notification/domain/NotificationConsumerStateRepository.java`:
   - Add advisory lock native query method.
2. `backend/src/main/java/com/swiftdrop/notification/infrastructure/NotificationAmqpConfiguration.java`:
   - Configure listener container factory with transaction manager and error handler / retry properties.

---

## 8. Confirmation of Non-Modification
This technical assessment is purely read-only. No application code, test classes, or database migrations were created or modified during this assessment.

