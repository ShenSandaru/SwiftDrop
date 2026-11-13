package com.swiftdrop.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.json.JsonMapper;

import com.swiftdrop.delivery.api.ParcelEvents;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;
import com.swiftdrop.notification.domain.NotificationConsumerState;
import com.swiftdrop.notification.domain.NotificationConsumerStateRepository;
import com.swiftdrop.notification.domain.NotificationRecord;
import com.swiftdrop.notification.domain.NotificationRecordRepository;
import com.swiftdrop.notification.infrastructure.NotificationAmqpConfiguration;
import com.swiftdrop.pricing.api.FeeCalculator;

/**
 * End-to-end integration tests for {@link NotificationEventListener} against live PostgreSQL
 * and RabbitMQ containers (ADR 0004 §5, §11).
 * <p>
 * Verifies:
 * <ol>
 *   <li>Valid message consumed, processed in JPA transaction, and acknowledged.</li>
 *   <li>Malformed message routed to DLQ without in-memory retries.</li>
 *   <li>Transient database exception retried with backoff, succeeding on fresh transaction (2 total attempts).</li>
 *   <li>Persistent transient failure exhausted after 3 total attempts, routing to DLQ.</li>
 *   <li>Unexpected non-transient exception routed to DLQ after exactly 1 invocation.</li>
 *   <li>Message redelivery after committed transaction handled idempotently without duplicate records.</li>
 * </ol>
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
class NotificationEventListenerIT {

    private static final String RABBIT_USER = "swiftdrop-test";
    private static final String RABBIT_PASSWORD = "swiftdrop-test-only";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Container
    static GenericContainer<?> rabbit = new GenericContainer<>("rabbitmq:4.3-management-alpine")
            .withEnv("RABBITMQ_DEFAULT_USER", RABBIT_USER)
            .withEnv("RABBITMQ_DEFAULT_PASS", RABBIT_PASSWORD)
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbit.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> RABBIT_USER);
        registry.add("spring.rabbitmq.password", () -> RABBIT_PASSWORD);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin admin;

    @Autowired
    private NotificationConsumerStateRepository consumerStateRepository;

    @Autowired
    private NotificationRecordRepository recordRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private NotificationEventProcessor processor;

    @MockitoBean
    private ParcelEvents parcelEvents;

    @MockitoBean
    private FeeCalculator feeCalculator;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private UUID customerId;

    @BeforeEach
    void setUp() {
        reset(processor);

        // Purge queues
        admin.purgeQueue(NotificationAmqpConfiguration.NOTIFICATION_QUEUE);
        admin.purgeQueue(NotificationAmqpConfiguration.NOTIFICATION_DLQ);

        // Clean tables
        recordRepository.deleteAll();
        consumerStateRepository.deleteAll();

        // Seed customer user
        customerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                customerId, "cust-" + customerId + "@example.com", "hash", "Test Customer", true);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'CUSTOMER')", customerId);
    }

    @Test
    @DisplayName("Scenario 1: Valid message is consumed, processed in PostgreSQL, and acknowledged from RabbitMQ")
    void validMessageConsumedProcessedAndAcknowledged() throws Exception {
        UUID parcelId = UUID.randomUUID();
        ParcelStatusChanged event = createEvent(parcelId, 1, ParcelStatus.OUT_FOR_DELIVERY);
        String jsonPayload = jsonMapper.writeValueAsString(event);

        publishEvent(jsonPayload);

        // Verify PostgreSQL consumer state is committed
        await().atMost(TIMEOUT).untilAsserted(() -> {
            NotificationConsumerState state = consumerStateRepository.findById(parcelId).orElse(null);
            assertThat(state).isNotNull();
            assertThat(state.getLastAppliedSequence()).isEqualTo(1);
        });

        // Verify NotificationRecord was created
        assertThat(recordRepository.findByRecipientUserId(customerId, org.springframework.data.domain.Pageable.unpaged())).hasSize(1);

        // Verify processor was invoked exactly once
        verify(processor).process(any(), any());

        // Verify DLQ is empty
        Message dlqMessage = rabbitTemplate.receive(NotificationAmqpConfiguration.NOTIFICATION_DLQ, 500);
        assertThat(dlqMessage).isNull();
    }

    @Test
    @DisplayName("Scenario 2: Malformed message routes to DLQ immediately without in-memory retries")
    void malformedMessageRoutesToDlqWithoutRetries() {
        String invalidJson = "{\"eventId\":\"not-a-valid-json-envelope\"";

        publishEvent(invalidJson);

        // Await message arrival in DLQ
        await().atMost(TIMEOUT).untilAsserted(() -> {
            Message dlqMessage = rabbitTemplate.receive(NotificationAmqpConfiguration.NOTIFICATION_DLQ, 1000);
            assertThat(dlqMessage).isNotNull();
            assertThat(new String(dlqMessage.getBody(), StandardCharsets.UTF_8)).isEqualTo(invalidJson);
        });

        // Processor must never be invoked for an unparseable message (zero retries)
        verify(processor, never()).process(any(), any());
    }

    @Test
    @DisplayName("Scenario 3: Transient database exception causes retry and succeeds on fresh transaction (2 total invocations)")
    void transientFailureRetriedAndSucceedsOnFreshTransaction() throws Exception {
        UUID parcelId = UUID.randomUUID();
        ParcelStatusChanged event = createEvent(parcelId, 1, ParcelStatus.OUT_FOR_DELIVERY);
        String jsonPayload = jsonMapper.writeValueAsString(event);

        AtomicInteger attempts = new AtomicInteger(0);

        // Attempt 1 throws transient lock failure; Attempt 2 proceeds to real method
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                throw new CannotAcquireLockException("Simulated transient lock contention");
            }
            return invocation.callRealMethod();
        }).when(processor).process(any(), any());

        publishEvent(jsonPayload);

        // Await successful commit on 2nd attempt
        await().atMost(TIMEOUT).untilAsserted(() -> {
            NotificationConsumerState state = consumerStateRepository.findById(parcelId).orElse(null);
            assertThat(state).isNotNull();
            assertThat(state.getLastAppliedSequence()).isEqualTo(1);
        });

        // Verified exactly 2 invocations (1 initial attempt + 1 retry)
        assertThat(attempts.get()).isEqualTo(2);

        // Verified no message in DLQ
        Message dlqMessage = rabbitTemplate.receive(NotificationAmqpConfiguration.NOTIFICATION_DLQ, 500);
        assertThat(dlqMessage).isNull();
    }

    @Test
    @DisplayName("Scenario 4: Persistent transient failure exhausts retries after exactly 3 total attempts and routes to DLQ")
    void persistentTransientFailureExhaustsAndReachesDlq() throws Exception {
        UUID parcelId = UUID.randomUUID();
        ParcelStatusChanged event = createEvent(parcelId, 1, ParcelStatus.OUT_FOR_DELIVERY);
        String jsonPayload = jsonMapper.writeValueAsString(event);

        AtomicInteger attempts = new AtomicInteger(0);

        // Throw transient lock exception on all attempts
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new CannotAcquireLockException("Persistent lock failure");
        }).when(processor).process(any(), any());

        publishEvent(jsonPayload);

        // Await rejection to DLQ
        await().atMost(TIMEOUT).untilAsserted(() -> {
            Message dlqMessage = rabbitTemplate.receive(NotificationAmqpConfiguration.NOTIFICATION_DLQ, 1000);
            assertThat(dlqMessage).isNotNull();
            assertThat(new String(dlqMessage.getBody(), StandardCharsets.UTF_8)).isEqualTo(jsonPayload);
        });

        // Verified exactly 3 total attempts (1 initial attempt + 2 retries from maxRetries(2))
        assertThat(attempts.get()).isEqualTo(3);

        // Consumer state must not be committed
        assertThat(consumerStateRepository.findById(parcelId)).isEmpty();
    }

    @Test
    @DisplayName("Scenario 5: Unexpected exception is not retried and routes to DLQ after exactly 1 invocation")
    void unexpectedExceptionRoutesToDlqAfterSingleAttempt() throws Exception {
        UUID parcelId = UUID.randomUUID();
        ParcelStatusChanged event = createEvent(parcelId, 1, ParcelStatus.DELIVERED);
        String jsonPayload = jsonMapper.writeValueAsString(event);

        AtomicInteger attempts = new AtomicInteger(0);

        doAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("Unexpected application bug");
        }).when(processor).process(any(), any());

        publishEvent(jsonPayload);

        // Await rejection to DLQ
        await().atMost(TIMEOUT).untilAsserted(() -> {
            Message dlqMessage = rabbitTemplate.receive(NotificationAmqpConfiguration.NOTIFICATION_DLQ, 1000);
            assertThat(dlqMessage).isNotNull();
            assertThat(new String(dlqMessage.getBody(), StandardCharsets.UTF_8)).isEqualTo(jsonPayload);
        });

        // Verified exactly 1 invocation (zero retries for unexpected non-transient exceptions)
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Reliability: Redelivery of already-committed message is handled idempotently without duplicate records")
    void redeliveredMessageHandledIdempotently() throws Exception {
        UUID parcelId = UUID.randomUUID();
        ParcelStatusChanged event = createEvent(parcelId, 1, ParcelStatus.OUT_FOR_DELIVERY);
        String jsonPayload = jsonMapper.writeValueAsString(event);

        // 1. Initial delivery
        publishEvent(jsonPayload);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            NotificationConsumerState state = consumerStateRepository.findById(parcelId).orElse(null);
            assertThat(state).isNotNull();
            assertThat(state.getLastAppliedSequence()).isEqualTo(1);
        });
        assertThat(recordRepository.findByRecipientUserId(customerId, org.springframework.data.domain.Pageable.unpaged())).hasSize(1);

        // 2. Simulate broker redelivery of the exact same message
        Message redeliveredMessage = MessageBuilder.withBody(jsonPayload.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setRedelivered(true)
                .build();
        rabbitTemplate.send(ParcelStatusChanged.EXCHANGE, ParcelStatusChanged.EVENT_TYPE, redeliveredMessage);

        // Wait for processor to consume the redelivery
        await().atMost(TIMEOUT).untilAsserted(() -> {
            // Verify processor was invoked twice (initial + redelivery)
            verify(processor, org.mockito.Mockito.times(2)).process(any(), any());
        });

        // Assert strictly idempotent state: exactly 1 record, watermark remains 1
        assertThat(recordRepository.findByRecipientUserId(customerId, org.springframework.data.domain.Pageable.unpaged())).hasSize(1);
        NotificationConsumerState state = consumerStateRepository.findById(parcelId).orElseThrow();
        assertThat(state.getLastAppliedSequence()).isEqualTo(1);

        // Verify DLQ is empty
        Message dlqMessage = rabbitTemplate.receive(NotificationAmqpConfiguration.NOTIFICATION_DLQ, 500);
        assertThat(dlqMessage).isNull();
    }

    private void publishEvent(String jsonPayload) {
        Message message = MessageBuilder.withBody(jsonPayload.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .build();
        rabbitTemplate.send(ParcelStatusChanged.EXCHANGE, ParcelStatusChanged.EVENT_TYPE, message);
    }

    private ParcelStatusChanged createEvent(UUID parcelId, int sequence, ParcelStatus status) {
        return ParcelStatusChanged.of(
                UUID.randomUUID(),
                Instant.now(),
                new ParcelStatusChangedData(
                        parcelId,
                        "SD1234567890",
                        ParcelStatus.CREATED,
                        status,
                        sequence,
                        ParcelSource.CUSTOMER,
                        customerId,
                        null,
                        null,
                        null,
                        "HUB-01",
                        1
                )
        );
    }
}

