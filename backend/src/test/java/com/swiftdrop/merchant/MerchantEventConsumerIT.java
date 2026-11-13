package com.swiftdrop.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.QueryTimeoutException;
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

import com.swiftdrop.delivery.TestHubs;
import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.api.ParcelCreation;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;
import com.swiftdrop.merchant.application.MerchantEventJson;
import com.swiftdrop.merchant.application.MerchantEventProcessor;
import com.swiftdrop.merchant.application.MerchantEventReconciler;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpoint;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * The merchant event consumer against real PostgreSQL and RabbitMQ 4.3 (SD-410, ADR 0004 §4.3, §6, §7.1).
 * The outbox relay stays off; tests publish the outbox payload themselves, so they control order, duplicates
 * and gaps. Delivery's real status history backs reconciliation.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password=",
        "swiftdrop.merchant.events.consumer.enabled=true",
        "swiftdrop.merchant.events.retry-initial-delay=100ms",
        "swiftdrop.merchant.events.retry-max-delay=200ms",
        "swiftdrop.merchant.events.grace-period=10s",
        "swiftdrop.merchant.events.reconcile-interval=1s"
})
@Testcontainers
class MerchantEventConsumerIT {

    private static final String QUEUE = "merchant.parcel-status-changed";
    private static final String DEAD_LETTER_QUEUE = QUEUE + ".dlq";
    private static final Duration WAIT = Duration.ofSeconds(30);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Container
    static GenericContainer<?> rabbit = new GenericContainer<>("rabbitmq:4.3-management-alpine")
            .withEnv("RABBITMQ_DEFAULT_USER", "swiftdrop-test")
            .withEnv("RABBITMQ_DEFAULT_PASS", "swiftdrop-test-only")
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbit.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "swiftdrop-test");
        registry.add("spring.rabbitmq.password", () -> "swiftdrop-test-only");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private AmqpAdmin admin;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ParcelCreation parcelCreation;
    @Autowired
    private MerchantRepository merchants;
    @Autowired
    private MerchantWebhookEndpointRepository endpoints;
    @Autowired
    private MerchantEventJson json;
    @Autowired
    private MerchantEventReconciler reconciler;

    @MockitoSpyBean
    private MerchantEventProcessor processor;
    @MockitoBean
    private FeeCalculator feeCalculator;

    private UUID merchantId;
    private UUID allStatuses;
    private UUID createdOnly;
    private UUID deliveredOnly;

    @BeforeEach
    void setUp() {
        TestHubs.install(jdbc);
        when(feeCalculator.calculateFee(any(), any(), any(), anyInt())).thenReturn(new FeeQuote(
                new BigDecimal("650.00"), "LKR", new BigDecimal("400.00"), new BigDecimal("150.00"),
                new BigDecimal("100.00"), UUID.randomUUID()));
        Merchant merchant = new Merchant(insertUser(), "Shop " + UUID.randomUUID(), "Retail",
                "REG-" + UUID.randomUUID(), "+94771234567", "Main St", null, null);
        merchant.approve(insertUser());
        merchantId = merchants.saveAndFlush(merchant).getId();
        allStatuses = endpoint(Set.of(ParcelStatus.values()));
        createdOnly = endpoint(Set.of(ParcelStatus.CREATED));
        deliveredOnly = endpoint(Set.of(ParcelStatus.DELIVERED));
    }

    @AfterEach
    void purge() {
        admin.purgeQueue(DEAD_LETTER_QUEUE, false);
    }

    @Test
    void merchantEventCreatesOnePendingRecordPerSubscribedEndpointWithTheReceivedBody() {
        CreatedParcel parcel = createParcel();
        String body = outboxPayload(parcel.parcelId(), 1);

        publish(body);

        await().atMost(WAIT).untilAsserted(() -> assertThat(records(parcel.parcelId())).hasSize(2));
        assertThat(jdbc.queryForList("SELECT endpoint_id FROM merchant_webhook_deliveries WHERE parcel_id = ?",
                UUID.class, parcel.parcelId())).containsExactlyInAnyOrder(allStatuses, createdOnly);
        assertThat(jdbc.queryForList("SELECT status FROM merchant_webhook_deliveries WHERE parcel_id = ?",
                String.class, parcel.parcelId())).containsOnly("PENDING");
        assertThat(jdbc.queryForList("SELECT payload FROM merchant_webhook_deliveries WHERE parcel_id = ?",
                String.class, parcel.parcelId())).containsOnly(body);
        assertThat(lastApplied(parcel.parcelId())).isEqualTo(1);
        assertThat(deliveredOnly).isNotNull();
    }

    @Test
    void redeliveredMessageAfterCommitCreatesNoSecondRecord() {
        CreatedParcel parcel = createParcel();
        String body = outboxPayload(parcel.parcelId(), 1);
        publish(body);
        await().atMost(WAIT).untilAsserted(() -> assertThat(records(parcel.parcelId())).hasSize(2));

        // As if the app crashed after the commit and RabbitMQ delivered the message again
        publish(body);

        await().atMost(WAIT).untilAsserted(() -> verify(processor, times(2))
                .process(argThat(e -> e.data().parcelId().equals(parcel.parcelId())), anyString()));
        assertThat(records(parcel.parcelId())).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_processed_events WHERE parcel_id = ?",
                Integer.class, parcel.parcelId())).isEqualTo(1);
    }

    @Test
    void customerParcelEventsLeaveNoState() {
        UUID parcelId = UUID.randomUUID();
        ParcelStatusChanged customer = ParcelStatusChanged.of(UUID.randomUUID(), Instant.now(),
                new ParcelStatusChangedData(parcelId, "SD7K3M9Q2XWB", null, ParcelStatus.CREATED, 1,
                        ParcelSource.CUSTOMER, UUID.randomUUID(), null, null, null));

        publish(json.toJson(customer));

        await().atMost(WAIT).untilAsserted(() -> verify(processor)
                .process(argThat(e -> e.data().parcelId().equals(parcelId)), anyString()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_event_states WHERE parcel_id = ?",
                Integer.class, parcelId)).isZero();
    }

    @Test
    void malformedMessageGoesToTheDeadLetterQueueWithoutRetry() {
        publish("this is not json");

        Message dead = rabbitTemplate.receive(DEAD_LETTER_QUEUE, WAIT.toMillis());
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo("this is not json");
        verify(processor, times(0)).process(any(), anyString());
    }

    @Test
    void transientFailureIsRetriedAndThenSucceeds() {
        CreatedParcel parcel = createParcel();
        doThrow(new QueryTimeoutException("database busy")).doCallRealMethod()
                .when(processor).process(argThat(e -> e.data().parcelId().equals(parcel.parcelId())), anyString());

        publish(outboxPayload(parcel.parcelId(), 1));

        await().atMost(WAIT).untilAsserted(() -> assertThat(records(parcel.parcelId())).hasSize(2));
        verify(processor, times(2)).process(argThat(e -> e.data().parcelId().equals(parcel.parcelId())), anyString());
    }

    @Test
    void retryExhaustionDeadLettersTheMessageAfterThreeAttempts() {
        CreatedParcel parcel = createParcel();
        doThrow(new QueryTimeoutException("database down"))
                .when(processor).process(argThat(e -> e.data().parcelId().equals(parcel.parcelId())), anyString());
        String body = outboxPayload(parcel.parcelId(), 1);

        publish(body);

        Message dead = rabbitTemplate.receive(DEAD_LETTER_QUEUE, WAIT.toMillis());
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo(body);
        verify(processor, times(3)).process(argThat(e -> e.data().parcelId().equals(parcel.parcelId())), anyString());
        assertThat(records(parcel.parcelId())).isEmpty();
        doCallRealMethod().when(processor).process(any(), anyString());
    }

    @Test
    void aGapIsReconciledFromDeliveryHistoryWithTheOriginalEventId() {
        CreatedParcel parcel = createParcel();
        ParcelStatusChanged created = json.parse(outboxPayload(parcel.parcelId(), 1));
        // Sequence 2 arrives, sequence 1 never does
        publish(json.toJson(next(created, 2, ParcelStatus.PICKED_UP)));
        await().atMost(WAIT).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM merchant_pending_events WHERE parcel_id = ?", Integer.class, parcel.parcelId()))
                .isEqualTo(1));
        assertThat(lastApplied(parcel.parcelId())).isZero();

        // After the 10 s grace period the reconciler rebuilds sequence 1 from history and drains sequence 2
        await().atMost(WAIT).untilAsserted(() -> assertThat(lastApplied(parcel.parcelId())).isEqualTo(2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_pending_events WHERE parcel_id = ?",
                Integer.class, parcel.parcelId())).isZero();
        assertThat(jdbc.queryForList("SELECT event_id FROM merchant_processed_events WHERE sequence = 1 AND parcel_id = ?",
                UUID.class, parcel.parcelId())).containsExactly(created.eventId());
        assertThat(jdbc.queryForList("SELECT event_type FROM merchant_webhook_deliveries WHERE endpoint_id = ? "
                + "AND parcel_id = ? ORDER BY sequence", String.class, allStatuses, parcel.parcelId()))
                .containsExactly("CREATED", "PICKED_UP");
    }

    @Test
    void twoReconcilersAtOnceApplyEachEventOnce() throws Exception {
        CreatedParcel parcel = createParcel();
        ParcelStatusChanged created = json.parse(outboxPayload(parcel.parcelId(), 1));
        processor.process(next(created, 2, ParcelStatus.PICKED_UP), json.toJson(next(created, 2, ParcelStatus.PICKED_UP)));
        jdbc.update("UPDATE merchant_pending_events SET next_reconcile_at = now() - interval '1 minute' "
                + "WHERE parcel_id = ?", parcel.parcelId());

        runConcurrently(List.of(() -> {
            reconciler.reconcileDue();
            return null;
        }, () -> {
            reconciler.reconcileDue();
            return null;
        }));

        await().atMost(WAIT).untilAsserted(() -> assertThat(lastApplied(parcel.parcelId())).isEqualTo(2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_processed_events WHERE parcel_id = ?",
                Integer.class, parcel.parcelId())).isEqualTo(2);
        assertThat(records(parcel.parcelId())).hasSize(3);
    }

    @Test
    void concurrentEventsOfOneParcelAreSerializedAndOfDifferentParcelsIndependent() throws Exception {
        CreatedParcel first = createParcel();
        CreatedParcel second = createParcel();
        ParcelStatusChanged firstCreated = json.parse(outboxPayload(first.parcelId(), 1));
        ParcelStatusChanged firstPickedUp = next(firstCreated, 2, ParcelStatus.PICKED_UP);
        String secondCreated = outboxPayload(second.parcelId(), 1);

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int copy = 0; copy < 3; copy++) {
            tasks.add(() -> processor.process(firstCreated, json.toJson(firstCreated)));
            tasks.add(() -> processor.process(firstPickedUp, json.toJson(firstPickedUp)));
            tasks.add(() -> processor.process(json.parse(secondCreated), secondCreated));
        }
        runConcurrently(tasks);

        assertThat(lastApplied(first.parcelId())).isEqualTo(2);
        assertThat(lastApplied(second.parcelId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_processed_events WHERE parcel_id = ?",
                Integer.class, first.parcelId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_pending_events WHERE parcel_id = ?",
                Integer.class, first.parcelId())).isZero();
        assertThat(records(first.parcelId())).hasSize(3);
        assertThat(records(second.parcelId())).hasSize(2);
        verify(processor, atLeast(9)).process(any(), anyString());
    }

    private void runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            for (Future<Object> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private CreatedParcel createParcel() {
        DeliveryContact pickup = new DeliveryContact("Store", "+94771234567", "1 Main St", null, "Colombo",
                TestHubs.COLOMBO_ZONE, null);
        DeliveryContact dropoff = new DeliveryContact("Nimal", "+94779876543", "2 Hill Rd", null, "Kandy",
                TestHubs.KANDY_ZONE, null);
        return parcelCreation.createMerchantParcel(new NewMerchantParcel(merchantId, "ORD-" + UUID.randomUUID(),
                pickup, dropoff, ParcelSize.MEDIUM, 1200, null));
    }

    private static ParcelStatusChanged next(ParcelStatusChanged previous, int sequence, ParcelStatus status) {
        ParcelStatusChangedData data = previous.data();
        return ParcelStatusChanged.of(UUID.randomUUID(), Instant.now(), new ParcelStatusChangedData(data.parcelId(),
                data.trackingNumber(), previous.data().newStatus(), status, sequence, data.source(),
                data.ownerCustomerId(), data.ownerMerchantId(), data.merchantOrderReference(), null));
    }

    private String outboxPayload(UUID parcelId, int sequence) {
        return jdbc.queryForObject("SELECT payload::text FROM parcel_outbox_events WHERE parcel_id = ? AND sequence = ?",
                String.class, parcelId, sequence);
    }

    private void publish(String body) {
        Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setMessageId(UUID.randomUUID().toString())
                .build();
        rabbitTemplate.send(ParcelStatusChanged.EXCHANGE, ParcelStatusChanged.EVENT_TYPE, message);
    }

    private List<UUID> records(UUID parcelId) {
        return jdbc.queryForList("SELECT id FROM merchant_webhook_deliveries WHERE parcel_id = ?", UUID.class, parcelId);
    }

    private Integer lastApplied(UUID parcelId) {
        List<Integer> values = jdbc.queryForList(
                "SELECT last_applied_sequence FROM merchant_event_states WHERE parcel_id = ?", Integer.class, parcelId);
        return values.isEmpty() ? 0 : values.get(0);
    }

    private UUID endpoint(Set<ParcelStatus> events) {
        return endpoints.saveAndFlush(new MerchantWebhookEndpoint(merchantId, "https://hooks.example.com/" + UUID.randomUUID(),
                "IT", "cipher", "iv", 1, "abcd", events)).getId();
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) "
                + "VALUES (?, ?, ?, ?, ?, NOW())", userId, "merchant" + userId + "@example.com", "hash", "Merchant", true);
        return userId;
    }
}
