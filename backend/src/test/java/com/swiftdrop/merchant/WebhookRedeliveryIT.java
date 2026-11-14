package com.swiftdrop.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.merchant.api.WebhookDeliveryView;
import com.swiftdrop.merchant.application.RedeliveryRefusedException;
import com.swiftdrop.merchant.application.WebhookDeliveryDetail;
import com.swiftdrop.merchant.application.WebhookDeliveryHistoryService;
import com.swiftdrop.merchant.application.WebhookDeliveryNotFoundException;
import com.swiftdrop.merchant.application.WebhookRedeliveryService;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpoint;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookRedelivery.RequestedBy;
import com.swiftdrop.merchant.infrastructure.webhook.WebhookSignature;
import com.swiftdrop.merchant.security.SecretCipher;

/**
 * Manual redelivery and delivery history against real PostgreSQL, the real sender and a loopback receiver (SD-412,
 * ADR 0004 §7.5). The sender runs with a short backoff and 3 attempts per series so series end in seconds.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password=",
        "swiftdrop.webhook.allow-local=true",
        "swiftdrop.merchant.webhooks.sender.enabled=true",
        "swiftdrop.merchant.webhooks.poll-interval=200ms",
        "swiftdrop.merchant.webhooks.backoff-base=1s",
        "swiftdrop.merchant.webhooks.max-attempts=3",
        "swiftdrop.merchant.webhooks.jitter=0"
})
@Testcontainers
class WebhookRedeliveryIT {

    private static final Duration WAIT = Duration.ofSeconds(40);
    private static final String SECRET = "whsec_redelivery-test-signing-secret";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MerchantRepository merchants;
    @Autowired
    private MerchantWebhookEndpointRepository endpoints;
    @Autowired
    private MerchantWebhookDeliveryRepository deliveries;
    @Autowired
    private SecretCipher cipher;
    @Autowired
    private WebhookRedeliveryService redelivery;
    @Autowired
    private WebhookDeliveryHistoryService history;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private HttpServer receiver;
    private final Queue<Map<String, String>> received = new ConcurrentLinkedQueue<>();
    private final Queue<byte[]> bodies = new ConcurrentLinkedQueue<>();
    /** Requests still to answer with 500. */
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private UUID merchantId;
    private UUID merchantUser;

    @BeforeEach
    void setUp() throws IOException {
        receiver = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        receiver.createContext("/", this::handle);
        receiver.setExecutor(Executors.newFixedThreadPool(4));
        receiver.start();
        merchantUser = insertUser();
        merchantId = merchant(merchantUser);
    }

    @AfterEach
    void stop() {
        receiver.stop(0);
    }

    @Test
    void aFailedDeliveryIsRedeliveredWithTheSameEventAndBodyAndAudited() {
        UUID endpoint = endpoint(merchantId);
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\", \"kept\": \"byte for byte\"}";
        UUID delivery = record(endpoint, merchantId, eventId, payload, "FAILED", 3);

        int series = redelivery.redeliver(delivery, merchantId, merchantUser, RequestedBy.MERCHANT);

        assertThat(series).isEqualTo(1);
        await().atMost(WAIT).untilAsserted(() -> assertThat(status(delivery)).isEqualTo("SUCCEEDED"));
        assertThat(received).singleElement().satisfies(headers -> {
            assertThat(headers.get(WebhookSignature.EVENT_ID_HEADER)).isEqualTo(eventId.toString());
            assertThat(WebhookSignature.verify(headers.get(WebhookSignature.SIGNATURE_HEADER), SECRET,
                    headers.get(WebhookSignature.TIMESTAMP_HEADER), bodies.peek(), Instant.now(),
                    WebhookSignature.DEFAULT_TOLERANCE)).isTrue();
        });
        assertThat(bodies.peek()).isEqualTo(payload.getBytes(StandardCharsets.UTF_8));
        assertThat(jdbc.queryForMap("SELECT attempt_number, series, triggered_by_user_id FROM merchant_webhook_attempts "
                + "WHERE delivery_id = ? AND series = 1", delivery))
                .containsEntry("attempt_number", 4).containsEntry("series", 1)
                .containsEntry("triggered_by_user_id", merchantUser);
        assertThat(jdbc.queryForMap("SELECT series, requested_by_user_id, requested_by_role, previous_status "
                + "FROM merchant_webhook_redeliveries WHERE delivery_id = ?", delivery))
                .containsEntry("series", 1).containsEntry("requested_by_user_id", merchantUser)
                .containsEntry("requested_by_role", "MERCHANT").containsEntry("previous_status", "FAILED");

        WebhookDeliveryDetail detail = history.detail(delivery, merchantId);
        assertThat(detail.series()).extracting(WebhookDeliveryDetail.Series::number).containsExactly(0, 1);
        assertThat(detail.series().get(1).attempts()).extracting(WebhookDeliveryDetail.Attempt::number)
                .containsExactly(4);
    }

    @Test
    void threeSeriesAreAllowedAndTheFourthRedeliveryIsRefused() {
        failuresLeft.set(Integer.MAX_VALUE);
        UUID delivery = record(endpoint(merchantId), merchantId, UUID.randomUUID(), "{}", "FAILED", 3);

        for (int expectedSeries = 1; expectedSeries <= 3; expectedSeries++) {
            assertThat(redelivery.redeliver(delivery, merchantId, merchantUser, RequestedBy.MERCHANT))
                    .isEqualTo(expectedSeries);
            int attemptsAfterSeries = 3 + 3 * expectedSeries;
            await().atMost(WAIT).untilAsserted(() -> {
                assertThat(status(delivery)).isEqualTo("FAILED");
                assertThat(attemptCount(delivery)).isEqualTo(attemptsAfterSeries);
            });
        }

        assertThatThrownBy(() -> redelivery.redeliver(delivery, merchantId, merchantUser, RequestedBy.MERCHANT))
                .isInstanceOf(RedeliveryRefusedException.class);
        // Each series had its own 3 attempts; numbers kept increasing
        assertThat(jdbc.queryForList("SELECT series || ':' || COUNT(*) || ':' || MIN(attempt_number) || '-' || "
                + "MAX(attempt_number) FROM merchant_webhook_attempts WHERE delivery_id = ? GROUP BY series ORDER BY series",
                String.class, delivery)).containsExactly("0:3:1-3", "1:3:4-6", "2:3:7-9", "3:3:10-12");
    }

    @Test
    void concurrentRedeliveriesStartOneSeries() throws Exception {
        failuresLeft.set(Integer.MAX_VALUE);
        UUID delivery = record(endpoint(merchantId), merchantId, UUID.randomUUID(), "{}", "FAILED", 3);

        List<Object> outcomes = runConcurrently(5, () -> redelivery.redeliver(delivery, merchantId, merchantUser,
                RequestedBy.MERCHANT));

        assertThat(outcomes).filteredOn(Integer.class::isInstance).containsExactly(1);
        assertThat(outcomes).filteredOn(RedeliveryRefusedException.class::isInstance).hasSize(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_webhook_redeliveries WHERE delivery_id = ?",
                Integer.class, delivery)).isEqualTo(1);
    }

    @Test
    void anAdminMayRedeliverAnotherMerchantsDeliveryButAMerchantMayNot() {
        UUID otherUser = insertUser();
        UUID otherMerchant = merchant(otherUser);
        UUID delivery = record(endpoint(otherMerchant), otherMerchant, UUID.randomUUID(), "{}", "FAILED", 3);

        assertThatThrownBy(() -> redelivery.redeliver(delivery, merchantId, merchantUser, RequestedBy.MERCHANT))
                .isInstanceOf(WebhookDeliveryNotFoundException.class);
        assertThatThrownBy(() -> history.detail(delivery, merchantId)).isInstanceOf(WebhookDeliveryNotFoundException.class);

        UUID admin = insertUser();
        assertThat(redelivery.redeliver(delivery, null, admin, RequestedBy.ADMIN)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT requested_by_role FROM merchant_webhook_redeliveries WHERE delivery_id = ?",
                String.class, delivery)).isEqualTo("ADMIN");
    }

    @Test
    void historyIsScopedAndStableWhenTimestampsAreEqual() {
        UUID endpoint = endpoint(merchantId);
        UUID otherMerchant = merchant(insertUser());
        UUID foreign = record(endpoint(otherMerchant), otherMerchant, UUID.randomUUID(), "{}", "SUCCEEDED", 1);
        Instant sameTime = Instant.parse("2026-11-16T09:00:00Z");
        Set<UUID> mine = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            UUID id = record(endpoint, merchantId, UUID.randomUUID(), "{}", "SUCCEEDED", 1);
            jdbc.update("UPDATE merchant_webhook_deliveries SET created_at = ? WHERE id = ?",
                    java.sql.Timestamp.from(sameTime), id);
            mine.add(id);
        }

        List<UUID> first = ids(history.findDeliveriesByMerchant(merchantId, null, new PageRequest(0, 3)).items());
        List<UUID> second = ids(history.findDeliveriesByMerchant(merchantId, null, new PageRequest(1, 3)).items());
        List<UUID> again = ids(history.findDeliveriesByMerchant(merchantId, null, new PageRequest(0, 3)).items());

        List<UUID> all = new ArrayList<>(first);
        all.addAll(second);
        assertThat(all).hasSize(5).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(mine)
                .doesNotContain(foreign);
        assertThat(again).as("same page, same order").isEqualTo(first);
        assertThat(all).as("ID descending on equal timestamps")
                .isSortedAccordingTo((a, b) -> b.toString().compareTo(a.toString()));
    }

    @Test
    void anOldLeaseTokenCannotStoreAnOutcomeOnARedeliveredRecord() {
        failuresLeft.set(Integer.MAX_VALUE);
        UUID delivery = record(endpoint(merchantId), merchantId, UUID.randomUUID(), "{}", "FAILED", 3);
        UUID oldToken = UUID.randomUUID();

        redelivery.redeliver(delivery, merchantId, merchantUser, RequestedBy.MERCHANT);

        // A sender from before the redelivery tries to store its result. Whether or not the running sender has
        // already claimed the new series (with its own fresh token), the old token is not accepted.
        Integer stored = new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .execute(tx -> deliveries.finishAttempt(delivery, oldToken, Instant.now(), "SUCCEEDED", 1,
                        Instant.now(), Instant.now(), "SUCCEEDED", 200));
        assertThat(stored).isZero();
    }

    private static List<UUID> ids(List<WebhookDeliveryView> views) {
        return views.stream().map(WebhookDeliveryView::id).toList();
    }

    private List<Object> runConcurrently(int copies, Callable<Object> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(copies);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < copies; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> future : futures) {
            try {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                outcomes.add(e.getCause());
            }
        }
        pool.shutdownNow();
        return outcomes;
    }

    private void handle(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new ConcurrentHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> {
            for (String known : List.of(WebhookSignature.EVENT_ID_HEADER, WebhookSignature.TIMESTAMP_HEADER,
                    WebhookSignature.SIGNATURE_HEADER)) {
                if (known.equalsIgnoreCase(name)) {
                    headers.put(known, values.get(0));
                }
            }
        });
        byte[] body = exchange.getRequestBody().readAllBytes();
        boolean fail = failuresLeft.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0;
        if (!fail) {
            received.add(headers);
            bodies.add(body);
        }
        exchange.sendResponseHeaders(fail ? 500 : 200, -1);
        exchange.close();
    }

    private UUID endpoint(UUID merchant) {
        SecretCipher.EncryptedSecret secret = cipher.encrypt(SECRET);
        String url = "http://127.0.0.1:" + receiver.getAddress().getPort() + "/hook";
        return endpoints.saveAndFlush(new MerchantWebhookEndpoint(merchant, url, "IT", secret.ciphertext(), secret.iv(),
                secret.keyVersion(), secret.hint(), Set.of(ParcelStatus.values()))).getId();
    }

    /** A record that already went through a series ({@code attempts} attempts) and ended in {@code status}. */
    private UUID record(UUID endpoint, UUID merchant, UUID eventId, String payload, String status, int attempts) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO merchant_webhook_deliveries (id, endpoint_id, merchant_id, event_id, parcel_id, sequence, "
                + "event_type, payload, status, attempts, next_attempt_at, manual_redeliveries, created_at, completed_at) "
                + "VALUES (?, ?, ?, ?, ?, 1, 'CREATED', ?, ?, ?, now(), 0, now(), now())",
                id, endpoint, merchant, eventId, UUID.randomUUID(), payload, status, attempts);
        for (int n = 1; n <= attempts; n++) {
            jdbc.update("INSERT INTO merchant_webhook_attempts (id, delivery_id, attempt_number, attempted_at, outcome, "
                    + "http_status, duration_ms, series) VALUES (?, ?, ?, now(), 'HTTP_ERROR', 500, 5, 0)",
                    UUID.randomUUID(), id, n);
        }
        return id;
    }

    private UUID merchant(UUID user) {
        Merchant merchant = new Merchant(user, "Shop " + UUID.randomUUID(), "Retail", "REG-" + UUID.randomUUID(),
                "+94771234567", "Main St", null, null);
        merchant.approve(insertUser());
        return merchants.saveAndFlush(merchant).getId();
    }

    private String status(UUID delivery) {
        return jdbc.queryForObject("SELECT status FROM merchant_webhook_deliveries WHERE id = ?", String.class, delivery);
    }

    private int attemptCount(UUID delivery) {
        return jdbc.queryForObject("SELECT attempts FROM merchant_webhook_deliveries WHERE id = ?", Integer.class,
                delivery);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) "
                + "VALUES (?, ?, ?, ?, ?, NOW())", userId, "u" + userId + "@example.com", "hash", "User", true);
        return userId;
    }
}
