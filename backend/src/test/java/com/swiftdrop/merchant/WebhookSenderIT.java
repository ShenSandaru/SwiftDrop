package com.swiftdrop.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpoint;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.merchant.infrastructure.webhook.WebhookSender;
import com.swiftdrop.merchant.infrastructure.webhook.WebhookSignature;
import com.swiftdrop.merchant.security.SecretCipher;

/**
 * The webhook sender against real PostgreSQL and a real HTTP receiver on loopback (SD-411, ADR 0004 §7.1, §7.2):
 * signed requests, retries, terminal failures, concurrent senders and expired leases. Local mode is on only so the
 * receiver may be on 127.0.0.1.
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
        "swiftdrop.merchant.webhooks.jitter=0"
})
@Testcontainers
class WebhookSenderIT {

    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final String SECRET = "whsec_integration-test-signing-secret";

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
    private SecretCipher cipher;
    @Autowired
    private WebhookSender sender;

    private HttpServer receiver;
    private final Queue<Received> received = new ConcurrentLinkedQueue<>();
    /** Statuses to answer per path, in order; 200 once the list is used up. */
    private final Map<String, Queue<Integer>> scripted = new ConcurrentHashMap<>();
    private volatile Duration receiverDelay = Duration.ZERO;
    private UUID merchantId;

    record Received(String path, Map<String, String> headers, byte[] body) {
    }

    @BeforeEach
    void setUp() throws IOException {
        receiver = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        receiver.createContext("/", this::handle);
        receiver.setExecutor(Executors.newFixedThreadPool(8));
        receiver.start();

        Merchant merchant = new Merchant(insertUser(), "Shop " + UUID.randomUUID(), "Retail", "REG-" + UUID.randomUUID(),
                "+94771234567", "Main St", null, null);
        merchant.approve(insertUser());
        merchantId = merchants.saveAndFlush(merchant).getId();
    }

    @AfterEach
    void stopReceiver() {
        receiver.stop(0);
    }

    @Test
    void sendsASignedRequestWithTheStoredBodyAndRecordsSuccess() {
        UUID endpoint = endpoint("/ok");
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\", \"exactly\" : \"as stored\"}";
        UUID delivery = pendingRecord(endpoint, eventId, payload);

        await().atMost(WAIT).untilAsserted(() -> assertThat(status(delivery)).isEqualTo("SUCCEEDED"));

        assertThat(received).singleElement().satisfies(request -> {
            assertThat(request.body()).isEqualTo(payload.getBytes(StandardCharsets.UTF_8));
            assertThat(request.headers().get(WebhookSignature.EVENT_ID_HEADER)).isEqualTo(eventId.toString());
            assertThat(request.headers().get(WebhookSignature.EVENT_TYPE_HEADER)).isEqualTo("parcel.status-changed");
            assertThat(WebhookSignature.verify(request.headers().get(WebhookSignature.SIGNATURE_HEADER), SECRET,
                    request.headers().get(WebhookSignature.TIMESTAMP_HEADER), request.body(), Instant.now(),
                    WebhookSignature.DEFAULT_TOLERANCE)).as("signature verifies with the endpoint secret").isTrue();
        });
        assertThat(attempts(delivery)).containsExactly("1:SUCCEEDED:200");
        assertThat(jdbc.queryForMap("SELECT attempts, lease_token, completed_at, last_http_status "
                + "FROM merchant_webhook_deliveries WHERE id = ?", delivery))
                .containsEntry("attempts", 1).containsEntry("lease_token", null).containsEntry("last_http_status", 200);
    }

    @Test
    void retriesAServerErrorThenSucceeds() {
        scripted.put("/flaky", new ConcurrentLinkedQueue<>(List.of(500)));
        UUID delivery = pendingRecord(endpoint("/flaky"), UUID.randomUUID(), "{}");

        await().atMost(WAIT).untilAsserted(() -> assertThat(status(delivery)).isEqualTo("SUCCEEDED"));

        assertThat(attempts(delivery)).containsExactly("1:HTTP_ERROR:500", "2:SUCCEEDED:200");
        // Same body and event ID, a fresh signature per attempt
        assertThat(received).hasSize(2);
        assertThat(received).extracting(r -> r.headers().get(WebhookSignature.EVENT_ID_HEADER)).hasSize(2)
                .containsOnly(received.peek().headers().get(WebhookSignature.EVENT_ID_HEADER));
    }

    @Test
    void aClientErrorFailsWithoutRetry() {
        scripted.put("/gone", new ConcurrentLinkedQueue<>(List.of(404, 404, 404)));
        UUID delivery = pendingRecord(endpoint("/gone"), UUID.randomUUID(), "{}");

        await().atMost(WAIT).untilAsserted(() -> assertThat(status(delivery)).isEqualTo("FAILED"));

        assertThat(attempts(delivery)).containsExactly("1:HTTP_ERROR:404");
        assertThat(received).hasSize(1);
    }

    @Test
    void concurrentSendersSendEachRecordOnce() throws Exception {
        receiverDelay = Duration.ofMillis(200);
        UUID endpoint = endpoint("/many");
        List<UUID> records = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            records.add(pendingRecord(endpoint, UUID.randomUUID(), "{\"n\":" + i + "}"));
        }

        // The scheduled sender runs too; three more polls start at the same moment
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> polls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            polls.add(pool.submit(() -> {
                start.await();
                sender.sendDue();
                return null;
            }));
        }
        start.countDown();
        for (Future<?> poll : polls) {
            poll.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        await().atMost(WAIT).untilAsserted(() -> assertThat(records).allSatisfy(
                id -> assertThat(status(id)).isEqualTo("SUCCEEDED")));
        List<String> eventIds = received.stream().map(r -> r.headers().get(WebhookSignature.EVENT_ID_HEADER)).toList();
        assertThat(eventIds).hasSize(12).doesNotHaveDuplicates();
        assertThat(records).allSatisfy(id -> assertThat(attempts(id)).hasSize(1));
    }

    @Test
    void anExpiredLeaseIsReclaimedAndAValidOneIsNot() {
        UUID endpoint = endpoint("/lease");
        UUID abandoned = pendingRecord(endpoint, UUID.randomUUID(), "{\"crashed\":true}");
        UUID busy = pendingRecord(endpoint, UUID.randomUUID(), "{\"busy\":true}");
        // A sender crashed while holding the first record; another one is still working on the second
        jdbc.update("UPDATE merchant_webhook_deliveries SET status = 'IN_PROGRESS', lease_token = ?, "
                + "lease_expires_at = now() - interval '1 second' WHERE id = ?", UUID.randomUUID(), abandoned);
        jdbc.update("UPDATE merchant_webhook_deliveries SET status = 'IN_PROGRESS', lease_token = ?, "
                + "lease_expires_at = now() + interval '1 hour' WHERE id = ?", UUID.randomUUID(), busy);

        await().atMost(WAIT).untilAsserted(() -> assertThat(status(abandoned)).isEqualTo("SUCCEEDED"));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(status(busy)).isEqualTo("IN_PROGRESS"));
        assertThat(attempts(busy)).isEmpty();
    }

    @Test
    void aSuspendedMerchantsRecordFailsWithoutARequest() {
        UUID delivery;
        UUID endpoint = endpoint("/suspended");
        Merchant merchant = merchants.findById(merchantId).orElseThrow();
        merchant.suspend(insertUser(), "Review");
        merchants.saveAndFlush(merchant);
        delivery = pendingRecord(endpoint, UUID.randomUUID(), "{}");

        await().atMost(WAIT).untilAsserted(() -> assertThat(status(delivery)).isEqualTo("FAILED"));

        assertThat(attempts(delivery)).containsExactly("1:MERCHANT_NOT_APPROVED:null");
        assertThat(received).isEmpty();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            Thread.sleep(receiverDelay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Map<String, String> headers = new ConcurrentHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(headerName(name), values.get(0)));
        String path = exchange.getRequestURI().getPath();
        received.add(new Received(path, Collections.unmodifiableMap(headers), exchange.getRequestBody().readAllBytes()));
        Queue<Integer> statuses = scripted.get(path);
        Integer status = statuses == null ? null : statuses.poll();
        exchange.sendResponseHeaders(status == null ? 200 : status, -1);
        exchange.close();
    }

    /** The JDK server normalizes header names; map them back to the SwiftDrop spelling. */
    private static String headerName(String name) {
        for (String known : Set.of(WebhookSignature.EVENT_ID_HEADER, WebhookSignature.EVENT_TYPE_HEADER,
                WebhookSignature.TIMESTAMP_HEADER, WebhookSignature.SIGNATURE_HEADER)) {
            if (known.equalsIgnoreCase(name)) {
                return known;
            }
        }
        return name;
    }

    private UUID endpoint(String path) {
        SecretCipher.EncryptedSecret secret = cipher.encrypt(SECRET);
        String url = "http://127.0.0.1:" + receiver.getAddress().getPort() + path;
        return endpoints.saveAndFlush(new MerchantWebhookEndpoint(merchantId, url, "IT", secret.ciphertext(),
                secret.iv(), secret.keyVersion(), secret.hint(), Set.of(ParcelStatus.values()))).getId();
    }

    private UUID pendingRecord(UUID endpoint, UUID eventId, String payload) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO merchant_webhook_deliveries (id, endpoint_id, merchant_id, event_id, parcel_id, sequence, "
                + "event_type, payload, status, attempts, next_attempt_at, manual_redeliveries, created_at) "
                + "VALUES (?, ?, ?, ?, ?, 1, 'CREATED', ?, 'PENDING', 0, now(), 0, now())",
                id, endpoint, merchantId, eventId, UUID.randomUUID(), payload);
        return id;
    }

    private String status(UUID delivery) {
        return jdbc.queryForObject("SELECT status FROM merchant_webhook_deliveries WHERE id = ?", String.class, delivery);
    }

    private List<String> attempts(UUID delivery) {
        return jdbc.queryForList("SELECT attempt_number || ':' || outcome || ':' || COALESCE(http_status::text, 'null') "
                + "FROM merchant_webhook_attempts WHERE delivery_id = ? ORDER BY attempt_number", String.class, delivery);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) "
                + "VALUES (?, ?, ?, ?, ?, NOW())", userId, "merchant" + userId + "@example.com", "hash", "Merchant", true);
        return userId;
    }
}
