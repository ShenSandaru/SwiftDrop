package com.swiftdrop.delivery.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.application.Actor;
import com.swiftdrop.delivery.application.ParcelEventRecorder;
import com.swiftdrop.delivery.application.ParcelEventSerializer;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.TrackingNumberGenerator;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * The relay against real PostgreSQL and RabbitMQ (ADR 0004 §3, §4.2, §11): a committed event reaches
 * a bound queue with the ADR message properties and is marked published; an unroutable event waits
 * in the outbox and is published once a queue is bound; the advisory lock keeps a second relay out.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password=",
        "swiftdrop.outbox.relay.enabled=true",
        "swiftdrop.outbox.relay.poll-interval=500ms",
        "swiftdrop.outbox.relay.backoff-initial=200ms",
        "swiftdrop.outbox.relay.backoff-max=1s"
})
@Testcontainers
class OutboxRelayIT {

    private static final String RABBIT_USER = "swiftdrop-test";
    private static final String RABBIT_PASSWORD = "swiftdrop-test-only";
    private static final String TEST_QUEUE = "test.outbox-relay";
    private static final Duration WAIT = Duration.ofSeconds(20);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    // The same image as compose.yaml; the default guest user may only connect from inside the container
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
    private ParcelRepository parcels;
    @Autowired
    private ParcelEventRecorder recorder;
    @Autowired
    private ParcelEventSerializer serializer;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private AmqpAdmin admin;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private FeeCalculator feeCalculator;

    @AfterEach
    void removeTestQueue() {
        admin.deleteQueue(TEST_QUEUE);
    }

    @Test
    void committedEventReachesABoundQueueWithTheAdrProperties() {
        bindTestQueue();

        ParcelStatusChanged event = createParcel();

        Message message = rabbitTemplate.receive(TEST_QUEUE, WAIT.toMillis());
        assertThat(message).as("message on the bound queue").isNotNull();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.eventId().toString());
        assertThat(message.getMessageProperties().getType()).isEqualTo(ParcelStatusChanged.EVENT_TYPE);
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo(ParcelStatusChanged.EVENT_TYPE);
        assertThat(serializer.fromJson(new String(message.getBody(), StandardCharsets.UTF_8))).isEqualTo(event);

        await().atMost(WAIT).untilAsserted(() -> assertThat(publishedAt(event)).isNotNull());
        assertThat(attempts(event)).isZero();
    }

    @Test
    void unroutableEventWaitsInTheOutboxUntilAQueueIsBound() {
        ParcelStatusChanged event = createParcel();

        await().atMost(WAIT).untilAsserted(() -> assertThat(attempts(event)).isGreaterThanOrEqualTo(1));
        assertThat(publishedAt(event)).isNull();
        assertThat(jdbc.queryForObject("SELECT last_failure_category FROM parcel_outbox_events WHERE event_id = ?",
                String.class, event.eventId())).isEqualTo("returned");

        bindTestQueue();

        Message message = rabbitTemplate.receive(TEST_QUEUE, WAIT.toMillis());
        assertThat(message).as("message after the queue was bound").isNotNull();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.eventId().toString());
        await().atMost(WAIT).untilAsserted(() -> assertThat(publishedAt(event)).isNotNull());
    }

    @Test
    void relayWaitsWhileAnotherInstanceHoldsTheLock() throws Exception {
        bindTestQueue();
        try (Connection otherRelay = dataSource.getConnection(); Statement statement = otherRelay.createStatement()) {
            otherRelay.setAutoCommit(false);
            statement.execute("SELECT pg_advisory_xact_lock(" + OutboxRelay.LOCK_KEY + ")");

            ParcelStatusChanged event = createParcel();
            // Several poll intervals pass without a publish while the other "relay" holds the lock
            assertThat(rabbitTemplate.receive(TEST_QUEUE, 2_000)).isNull();
            assertThat(publishedAt(event)).isNull();

            otherRelay.rollback();

            Message message = rabbitTemplate.receive(TEST_QUEUE, WAIT.toMillis());
            assertThat(message).isNotNull();
            assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.eventId().toString());
        }
    }

    private void bindTestQueue() {
        TopicExchange exchange = new TopicExchange(ParcelStatusChanged.EXCHANGE, true, false);
        admin.declareExchange(exchange);
        // RabbitMQ 4 refuses transient shared queues; the queue is deleted after each test
        Queue queue = new Queue(TEST_QUEUE, true, false, false);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(ParcelStatusChanged.EVENT_TYPE));
    }

    /** A committed parcel creation, which writes its first outbox row. */
    private ParcelStatusChanged createParcel() {
        UUID customerId = insertUser();
        return transactions.execute(status -> {
            Parcel parcel = parcels.save(Parcel.forCustomer(new TrackingNumberGenerator().next(), customerId,
                    address("Colombo"), address("Kandy"), ParcelSize.SMALL, 1000, null,
                    new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), Instant.now()));
            parcels.flush();
            return recorder.record(parcel, null, Actor.customer(customerId), null, null, Instant.now());
        });
    }

    private Timestamp publishedAt(ParcelStatusChanged event) {
        return jdbc.queryForObject("SELECT published_at FROM parcel_outbox_events WHERE event_id = ?", Timestamp.class,
                event.eventId());
    }

    private int attempts(ParcelStatusChanged event) {
        return jdbc.queryForObject("SELECT attempts FROM parcel_outbox_events WHERE event_id = ?", Integer.class,
                event.eventId());
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, ?, TRUE, now())",
                id, "customer-" + id + "@example.com", "Test customer");
        return id;
    }

    private static ParcelAddress address(String city) {
        return new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, city, UUID.randomUUID(), null);
    }
}
