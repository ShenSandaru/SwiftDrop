# Phase 5.2.4 Technical Assessment & Implementation Design: RabbitMQ Event Listener

**Target Module**: `notification`  
**Related Modules**: `delivery.api`, `identity.api`  
**Framework Versions**: Spring Boot 4.1.2, Spring AMQP / Spring Rabbit 4.1.2, Java 21  
**Status**: Technical Assessment & Implementation Design (Read-Only)  
**Date**: November 2026

---

## 1. Existing Architecture & Codebase Inspection

### 1.1 AMQP Topology & Message Flow (Phase 5.1)
- **Declared in**: `com.swiftdrop.notification.infrastructure.NotificationAmqpConfiguration`.
- **Main Queue**: `notification.parcel-status-changed` (durable).
  - Bound to shared topic exchange `swiftdrop.events` (declared by Delivery module, `ParcelStatusChanged.EXCHANGE`).
  - Routing key: `parcel.status-changed` (`ParcelStatusChanged.EVENT_TYPE`).
  - Configured dead-letter arguments:
    - `x-dead-letter-exchange`: `notification.dlx`
    - `x-dead-letter-routing-key`: `notification.parcel-status-changed.dlq`
- **Dead-Letter Exchange & Queue**:
  - Direct exchange: `notification.dlx` (durable).
  - Queue: `notification.parcel-status-changed.dlq` (durable).
  - Binding: bound to `notification.dlx` with routing key `notification.parcel-status-changed.dlq`.

### 1.2 Upstream Producer & Message Format
- **Declared in**: `com.swiftdrop.delivery.infrastructure.outbox.OutboxPublisher#toMessage`.
- **Message Content**:
  - Body: JSON string encoded as UTF-8 bytes.
  - Content-Type: `application/json` (`MessageProperties.CONTENT_TYPE_JSON`).
  - Content-Encoding: `UTF-8`.
  - Message-ID: UUID string of the event (`eventId.toString()`).
  - Type: `parcel.status-changed` (`row.getEventType()`).
  - Delivery Mode: `PERSISTENT` (`MessageDeliveryMode.PERSISTENT`).
  - Timestamp: `Date` from `row.getCreatedAt()`.

### 1.3 Event Parsing & Core Processor (Phases 5.1 & 5.2.3)
- **Parser (`ParcelStatusEventParser`)**:
  - Method: `ParcelStatusChanged parse(String json)`.
  - Deserializes JSON into the `ParcelStatusChanged` envelope using Jackson 3 (`tools.jackson`).
  - Validates envelope and payload completeness.
  - Throws `EventParsingException` (runtime exception) on malformed JSON, unparseable UUIDs, missing mandatory envelope/data fields, unknown `ParcelStatus` enum values, and mismatched `eventType` or `schemaVersion`.
  - The exception message is sanitized to prevent leaking parser internals.
- **Core Processor (`NotificationEventProcessor`)**:
  - Method: `@Transactional(isolation = Isolation.READ_COMMITTED) public void process(ParcelStatusChanged event, String rawPayload)`.
  - Transaction-level advisory lock: `consumerStateRepository.acquireParcelLock(parcelId)` serializes execution per parcel.
  - Watermark tracking: updates or creates `NotificationConsumerState`.
  - Gap recovery & contiguous buffer draining: checks Delivery history via `ParcelEvents#eventsBetween` and buffers out-of-order events in `NotificationPendingEvent`.
  - In-order canonical resolution: if a buffered pending record exists for the sequence, it uses the canonical stored payload and verifies integrity before applying.
  - Quarantining: if a stored payload is corrupted or mismatched, it marks the record `UNRESOLVED` and halts without advancing the watermark.
  - In-app notification creation: persists `NotificationRecord` for eligible customer parcels via `NotificationTemplateResolver`.

---

## 2. Framework Execution Order: Transaction, Advice Chain, and Retries

### 2.1 Spring AMQP 4.1.2 Internal Execution Architecture
In Spring AMQP 4.1.2 (`spring-rabbit-4.1.2.jar`), `SimpleMessageListenerContainer` processes messages through consumer threads managed by `BlockingQueueConsumer`:

```
               BlockingQueueConsumer (Worker Thread)
                               │
                               ▼
        Is factory.setTransactionManager(jpaTxManager) set?
        ├── YES: SimpleMessageListenerContainer wraps the call in:
        │        transactionTemplate.execute(txStatus -> { ... })
        │        (JPA Transaction begins here, surrounding the entire receive-and-execute cycle)
        │
        ▼
   Advice Chain (ProxyFactory wrapping ContainerDelegate / MessageListener)
        │
        ├── RetryOperationsInterceptor (Stateless vs Stateful)
        │        │
        │        ▼
        │   NotificationEventListener.onParcelStatusChanged(message)
        │        │
        │        ▼
        │   NotificationEventProcessor.process()
        │        (Propagates into surrounding container JPA transaction)
```

### 2.2 Critical Finding: Stateless vs. Stateful Retries with External Transaction Manager

1. **The Stateless Retry Trap with External Transaction Manager**:
   - If a **Stateless** `RetryOperationsInterceptor` is placed on a container where `transactionManager` is configured on `SimpleRabbitListenerContainerFactory`:
     - The container starts the JPA transaction **before** calling the advice chain.
     - When `NotificationEventProcessor.process()` fails with a database exception, the JPA `EntityManager` / Hibernate Session is marked **rollback-only**.
     - The stateless retry interceptor loops **inside the same method invocation**, attempting retry 2 and retry 3 within the **exact same corrupted, rollback-only JPA transaction**.
     - Subsequent queries will fail with `TransactionSystemException: Transaction marked as rollback-only` or Hibernate `IllegalStateException`.
     - **Conclusion**: A stateless retry interceptor **cannot** provide a fresh JPA transaction per retry attempt when the transaction is managed at the listener container level!

2. **The Correct Solutions**:
   There are two clean architectural patterns in Spring AMQP 4 to ensure each retry attempt runs in a fresh transaction:

   - **Pattern A: Method-Level Transaction with Stateless Retry (RECOMMENDED)**:
     - Leave `transactionManager = null` on `SimpleRabbitListenerContainerFactory`.
     - Use `AcknowledgeMode.AUTO` and `channelTransacted = false` (or standard container transaction).
     - Place `RetryOperationsInterceptor.stateless()` on the container factory's advice chain.
     - Configure an explicit `RetryPolicy` on `RetryInterceptorBuilder` to classify exceptions:
       - **Excludes**: `AmqpRejectAndDontRequeueException`, `EventParsingException`.
       - **Includes**: Approved transient infrastructure and database exceptions (`TransientDataAccessException`, `PessimisticLockingFailureException`, `CannotAcquireLockException`, `QueryTimeoutException`, `TransientDataAccessResourceException`).
     - The transaction is demarcated directly on `NotificationEventProcessor.process(...)` with `@Transactional(isolation = Isolation.READ_COMMITTED)`.
     - **Execution Flow**:
       1. The container receives the message.
       2. The retry interceptor executes attempt 1.
       3. If `NotificationEventListener` throws `AmqpRejectAndDontRequeueException` (from `EventParsingException`), the retry policy evaluates to `false` and does **not** retry; `RejectAndDontRequeueRecoverer` triggers immediately.
       4. `NotificationEventProcessor.process()` starts JPA Transaction #1.
       5. If a transient database exception occurs, JPA Transaction #1 rolls back completely.
       6. The retry interceptor checks the retry policy (returns `true`), sleeps for exponential backoff (1s), and invokes attempt 2.
       7. `NotificationEventProcessor.process()` starts **fresh JPA Transaction #2**.
       8. If attempt 2 fails, the retry interceptor sleeps for exponential backoff (2s), and invokes attempt 3 (fresh JPA Transaction #3).
       9. If all 3 attempts fail (1 initial attempt + 2 retries), `RejectAndDontRequeueRecoverer` throws `ListenerExecutionFailedException` wrapping `AmqpRejectAndDontRequeueException`.
       10. The container catches this and issues `basicReject(requeue = false)` to route the message to `notification.dlx` $\rightarrow$ `notification.parcel-status-changed.dlq`.
       11. If any attempt succeeds, the JPA transaction commits, the method returns cleanly, and Spring AMQP issues `basicAck`.

   - **Pattern B: Container-Level Transaction with Stateful Retry (`StatefulRetryOperationsInterceptor`)**:
     - `StatefulRetryOperationsInterceptor` does not sleep in an in-memory loop. Instead, upon failure, it allows the exception to propagate out, which rolls back the container's JPA transaction and RabbitMQ channel.
     - It relies on RabbitMQ message requeue (`requeue = true`) and message tracking using `messageKeyGenerator` (Message ID / Correlation ID).
     - **Limitation**: In standard RabbitMQ without the delayed-exchange plugin, requeued messages are redelivered immediately at head-of-line without exponential backoff (spin-retry).
     - **Conclusion**: Pattern A is vastly superior because it provides clean exponential backoff (1s, 2s, 5s), guarantees a fresh JPA transaction per attempt, and avoids broker spin loops.

---

## 3. Transaction and Acknowledgement Analysis

### 3.1 Does Pattern A Guarantee Commit Before Acknowledgement?
**Yes.**
In Spring AMQP with `AcknowledgeMode.AUTO`:
1. The listener container thread invokes the listener / processor.
2. `@Transactional` on `NotificationEventProcessor.process(...)` opens the PostgreSQL connection, acquires the advisory lock, performs updates, and commits the database transaction before returning to the listener.
3. Only after `NotificationEventProcessor.process()` returns successfully does the container issue the channel `basicAck` to RabbitMQ.
4. If an exception is thrown, the JPA transaction rolls back. The container receives the exception and does **not** send an ACK.

### 3.2 Failure Scenarios & Actual Guarantees

1. **PostgreSQL commits successfully, but RabbitMQ ACK fails (or connection drops)**:
   - **Guarantees**: Exactly-once message delivery is impossible in distributed systems without distributed 2PC.
   - **Behavior**: RabbitMQ detects connection termination and redelivers the message with `redelivered = true`.
   - **Safety**: `NotificationEventProcessor` is strictly idempotent:
     - `sequence <= lastApplied` is detected as a duplicate no-op.
     - `NotificationRecordRepository` enforces `uq_notification_records_event_channel_user`.
     - The redelivery succeeds as a no-op, commits, and acknowledges cleanly.
2. **PostgreSQL transaction rolls back**:
   - The exception propagates out to the retry interceptor or container.
   - No ACK is sent.
   - All database modifications roll back cleanly.
3. **Fatal / Poison-Pill Messages**:
   - `EventParsingException` is caught by `NotificationEventListener` and re-thrown as `AmqpRejectAndDontRequeueException`.
   - The retry interceptor's `RetryPolicy` is explicitly configured to exclude `AmqpRejectAndDontRequeueException` and `EventParsingException`.
   - The retry interceptor immediately stops without retrying and invokes `RejectAndDontRequeueRecoverer`.
   - `RejectAndDontRequeueRecoverer` throws `ListenerExecutionFailedException` wrapping `AmqpRejectAndDontRequeueException`.
   - The container catches `AmqpRejectAndDontRequeueException` (checked via `ContainerUtils.shouldRequeue(...)`, returning `false`).
   - Spring AMQP sends `basicReject(requeue = false)`, routing the message directly to `notification.parcel-status-changed.dlq`.
4. **All Attempts Exhausted (1 Initial Attempt + 2 Retries = 3 Total Attempts)**:
   - `RejectAndDontRequeueRecoverer` wraps the failure in `AmqpRejectAndDontRequeueException`.
   - The container sends `basicReject(requeue = false)`, routing the message directly to `notification.parcel-status-changed.dlq`.

---

## 4. Recommended Container Factory Configuration

### 4.1 Retry Classification and Attempt Semantics Verification
In Spring AMQP 4.1.2 / Spring Core 7.0.10:
- `RetryInterceptorBuilder.stateless()` uses `DefaultRetryPolicy` built via `RetryPolicy.builder()`.
- **`maxRetries` Semantics**: In `RetryPolicy.Builder`, `maxRetries(count)` configures the maximum number of backoff/retry executions on the underlying `ExponentialBackOff` (`setMaxAttempts(count)`). The initial listener execution occurs separately before entering the backoff retry loop in `RetryTemplate`. Consequently:
  - `maxRetries(3)` configures 3 retry executions after the initial attempt $\rightarrow$ **4 total attempts**.
  - `maxRetries(2)` configures 2 retry executions after the initial attempt $\rightarrow$ **3 total attempts**.
  - Therefore, to satisfy the requirement of **exactly 3 total attempts** (1 initial attempt + 2 retries), the configuration must specify **`.maxRetries(2)`**.
- **Exception Classification Default Behavior**: If no custom inclusions/exclusions are defined, `DefaultRetryPolicy` uses `InstanceFilter` with `matchIfEmpty = true`, which **retries all `Throwable` instances by default**.
- Therefore, **`AmqpRejectAndDontRequeueException` is NOT automatically excluded from in-memory stateless retries by default**. Without an explicit retry policy, throwing `AmqpRejectAndDontRequeueException` would still trigger in-memory retries before reaching the recoverer!
- To guarantee zero retries for poison pills and enforce exactly 3 total attempts for approved transient issues, an explicit retry policy MUST be configured on `RetryInterceptorBuilder`:
  1. **Do not retry**:
     - `EventParsingException` (poison-pill payload/schema validation errors)
     - `AmqpRejectAndDontRequeueException` (explicit AMQP fatal rejection marker)
     - `IllegalArgumentException`, `NullPointerException` (unrecoverable programmer / runtime bugs)
  2. **Retry approved transient database and infrastructure exceptions**:
     - `TransientDataAccessException` (and subclasses, e.g. `QueryTimeoutException`, `TransientDataAccessResourceException`)
     - `CannotAcquireLockException` (PostgreSQL advisory lock contention / deadlock)
     - `PessimisticLockingFailureException` (row lock acquisition timeouts)
  3. **Unexpected exceptions**:
     - Do not retry arbitrary unknown runtime exceptions; immediately reject to DLQ for manual operator inspection.

### 4.2 Configuration Code

```java
@Bean(name = "notificationRabbitListenerContainerFactory")
SimpleRabbitListenerContainerFactory notificationRabbitListenerContainerFactory(
        ConnectionFactory connectionFactory) {

    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
    factory.setDefaultRequeueRejected(false);

    // Build stateless retry interceptor with explicit retry classification:
    // Exactly 3 total attempts (1 initial attempt + 2 retries), 1s initial, 2.0 multiplier, 5s max delay
    RetryOperationsInterceptor retryInterceptor = RetryInterceptorBuilder.stateless()
            .maxRetries(2)
            .backOffOptions(1000, 2.0, 5000)
            .configureRetryPolicy(policy -> policy
                    // Explicitly exclude non-retryable fatal exceptions (takes precedence)
                    .excludes(
                            EventParsingException.class,
                            AmqpRejectAndDontRequeueException.class,
                            IllegalArgumentException.class,
                            NullPointerException.class
                    )
                    // Explicitly include approved transient infrastructure & database exceptions
                    .includes(
                            TransientDataAccessException.class,
                            CannotAcquireLockException.class,
                            PessimisticLockingFailureException.class
                    )
            )
            .recoverer(new RejectAndDontRequeueRecoverer())
            .build();

    factory.setAdviceChain(retryInterceptor);
    return factory;
}
```

---

## 5. Listener Implementation Structure

```java
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final ParcelStatusEventParser parser;
    private final NotificationEventProcessor processor;

    public NotificationEventListener(ParcelStatusEventParser parser, NotificationEventProcessor processor) {
        this.parser = parser;
        this.processor = processor;
    }

    @RabbitListener(
            queues = NotificationAmqpConfiguration.NOTIFICATION_QUEUE,
            containerFactory = "notificationRabbitListenerContainerFactory"
    )
    public void onParcelStatusChanged(Message message) {
        String rawJson = new String(message.getBody(), StandardCharsets.UTF_8);
        ParcelStatusChanged event;
        try {
            event = parser.parse(rawJson);
        } catch (EventParsingException e) {
            log.error("Fatal parsing error for message id {}. Rejecting to DLQ without retry.",
                    message.getMessageProperties().getMessageId(), e);
            throw new AmqpRejectAndDontRequeueException("Fatal event parsing error", e);
        }

        // Delegate to transactional core processor (runs in isolated JPA transaction per attempt)
        processor.process(event, rawJson);
    }
}
```

---

## 6. Testing Strategy

### 6.1 Unit Tests (`NotificationEventListenerTest`)
- Mock `ParcelStatusEventParser` and `NotificationEventProcessor`.
- Verify:
  1. Valid message is parsed and passed to `processor.process(event, rawJson)`.
  2. `EventParsingException` throws `AmqpRejectAndDontRequeueException`.
  3. Runtime/Database exception from `processor.process()` propagates to trigger retry interceptor.

### 6.2 Integration Tests (`NotificationEventListenerIT`)
Using Testcontainers (`PostgreSQLContainer` and `GenericContainer<rabbitmq:4.3-management-alpine>`):
1. **End-to-End Processing**: Send message to `swiftdrop.events`. Listener consumes, commits to PostgreSQL, ACKs to RabbitMQ.
2. **Poison Pill to DLQ**: Send invalid JSON to `swiftdrop.events`. Verify zero retries, immediate `basicReject(requeue = false)`, and receipt in `notification.parcel-status-changed.dlq`.
3. **Transient Retry & Fresh Transaction**:
   - Inject transient failure on attempt 1 in test mock/spy.
   - Verify attempt 2 starts in a fresh transaction, succeeds, and ACKs.
4. **Exhausted Retries to DLQ**:
   - Make `NotificationEventProcessor` fail permanently with a transient error.
   - Verify exactly 3 total attempts occur (1 initial attempt + 2 retries with backoff), followed by dead-lettering into `notification.parcel-status-changed.dlq`.
5. **Idempotent Redelivery**:
   - Send duplicate message. Verify idempotency filter in `NotificationEventProcessor` prevents duplicate records.

---

## 7. Answers to Verification Questions

1. **When does the retry interceptor execute relative to the JPA transaction?**
   - With Pattern A (method-level `@Transactional`), the retry interceptor executes **outside** the JPA transaction. Each retry attempt invokes `processor.process()`, which starts a **brand-new, independent JPA transaction**.
2. **Does each retry attempt receive a fresh transaction?**
   - **Yes.** Because the transaction is demarcated on `NotificationEventProcessor.process()`, when attempt 1 throws an exception, JPA Transaction #1 rolls back. Attempt 2 invokes the method again, opening fresh JPA Transaction #2 with a clean `EntityManager`.
3. **Is the advice-chain ordering correct?**
   - **Yes.** Setting `factory.setAdviceChain(retryInterceptor)` on `SimpleRabbitListenerContainerFactory` ensures the retry advice intercepts the listener invocation directly.
4. **Does PostgreSQL commit before RabbitMQ acknowledges?**
   - **Yes.** With `AcknowledgeMode.AUTO`, Spring AMQP only sends `basicAck` after the listener method (and its underlying committed JPA transaction) returns without exception.
5. **What happens on failure scenarios?**
   - **PostgreSQL commits, RabbitMQ channel commit/ACK drops**: RabbitMQ redelivers; `NotificationEventProcessor`'s watermark check handles it idempotently as a safe no-op.
   - **PostgreSQL rolls back**: Exception propagates out; no ACK is sent; message is retried or rejected.
   - **All retries exhausted**: After 3 total attempts (1 initial attempt + 2 retries configured via `.maxRetries(2)`), `RejectAndDontRequeueRecoverer` throws `AmqpRejectAndDontRequeueException`, issuing `basicReject(requeue = false)` to route to `notification.dlx` $\rightarrow$ `notification.parcel-status-changed.dlq`.
6. **Does `AmqpRejectAndDontRequeueException` bypass retries and route to DLQ?**
   - **Yes, when explicitly excluded in `configureRetryPolicy`**. Bytecode inspection of Spring AMQP 4.1.2 and Spring Core 7.0.10 confirmed that `DefaultRetryPolicy` uses `InstanceFilter` with `matchIfEmpty = true`, meaning **all throwables are retried by default** if neither includes nor excludes are configured. By explicitly calling `.excludes(AmqpRejectAndDontRequeueException.class, EventParsingException.class, ...)` and `.includes(TransientDataAccessException.class, ...)`, `shouldRetry()` returns `false` immediately, halting retries without in-memory delays. `RejectAndDontRequeueRecoverer` triggers, and `ContainerUtils.shouldRequeue()` returns `false`, causing Spring AMQP to issue `basicReject(requeue = false)` directly to DLQ.
7. **Does `RejectAndDontRequeueRecoverer` route to the same DLQ?**
   - **Yes.** It throws `ListenerExecutionFailedException` wrapping `AmqpRejectAndDontRequeueException`, prompting `basicReject(requeue = false)`. RabbitMQ dead-letter configuration routes it to `notification.parcel-status-changed.dlq`.
8. **Are there configuration conflicts?**
   - Setting `setTransactionManager` on the container factory while using a stateless retry interceptor causes transaction corruption (retrying inside a rollback-only transaction). By delegating JPA transaction demarcation to `NotificationEventProcessor.process()` (Pattern A), this conflict is completely eliminated.

---

## 8. Final Verdict

**APPROVE WITH EXPLICIT RETRY POLICY DEFINITION**

The transaction, retry, and dead-letter architecture is verified against Spring Boot 4.1.2, Spring AMQP 4.1.2, and Spring Core 7.0.10. Pattern A with the explicit `configureRetryPolicy` satisfies all architectural invariants cleanly and robustly without silent retry bugs on poison pills.
