package com.swiftdrop.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelCreation;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.application.Actor;
import com.swiftdrop.delivery.application.ParcelStatusService;
import com.swiftdrop.delivery.application.StatusChange;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * Live tracking over a real HTTP connection against real PostgreSQL (ADR 0007): the stream opens with
 * a snapshot, receives committed status changes as they happen, and ends after the final status.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
class LiveTrackingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @LocalServerPort
    private int port;
    @Autowired
    private ParcelCreation creation;
    @Autowired
    private ParcelAssignment assignment;
    @Autowired
    private ParcelStatusService statuses;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private FeeCalculator feeCalculator;

    private final HttpClient client = HttpClient.newHttpClient();
    private UUID courierId;

    @BeforeEach
    void setUp() {
        when(feeCalculator.calculateFee(any(), any(), any(), anyInt())).thenReturn(new FeeQuote(
                new BigDecimal("650.00"), "LKR", new BigDecimal("400.00"), new BigDecimal("100.00"),
                new BigDecimal("150.00"), UUID.randomUUID()));
        courierId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, 'Courier', TRUE, now())",
                courierId, "courier-" + courierId + "@example.com");
    }

    @Test
    void streamDeliversSnapshotThenLiveChangesAndEndsAfterTheFinalStatus() throws Exception {
        CreatedParcel parcel = merchantParcel();
        assignment.assignCourier(parcel.parcelId(), courierId, UUID.randomUUID());
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        CompletableFuture<HttpResponse<Stream<String>>> response = open(parcel.trackingNumber().toLowerCase(), lines);

        assertThat(nextEvent(lines)).startsWith("event:snapshot").contains("\"status\":\"CREATED\"");

        change(parcel, ParcelStatus.PICKED_UP, null);
        assertThat(nextEvent(lines)).contains("event:status", "id:2", "\"status\":\"PICKED_UP\"", "\"sequence\":2");

        change(parcel, ParcelStatus.FAILED, FailureReason.ADDRESS_NOT_FOUND);
        String last = nextEvent(lines);
        assertThat(last).contains("\"status\":\"FAILED\"", "\"sequence\":3")
                .doesNotContain("ADDRESS_NOT_FOUND", "Nimal", "+9477", courierId.toString());

        // The server completes the stream after the final status
        HttpResponse<Stream<String>> finished = response.get(10, TimeUnit.SECONDS);
        assertThat(finished.statusCode()).isEqualTo(200);
        assertThat(finished.headers().firstValue("Content-Type")).get().asString().startsWith("text/event-stream");
    }

    @Test
    void rolledBackChangesAreNeverPushed() throws Exception {
        CreatedParcel parcel = merchantParcel();
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        open(parcel.trackingNumber(), lines);
        nextEvent(lines);

        // Not assigned to this courier: the change fails and is rolled back
        try {
            change(parcel, ParcelStatus.PICKED_UP, null);
        } catch (RuntimeException expected) {
            // ParcelNotFoundException
        }

        assertThat(lines.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void unknownNumberIsRefusedWith404() throws Exception {
        HttpResponse<Void> response = client.send(HttpRequest.newBuilder(uri("SD0000000000"))
                .header("Accept", "text/event-stream").build(), HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(404);
    }

    private CompletableFuture<HttpResponse<Stream<String>>> open(String trackingNumber, BlockingQueue<String> lines) {
        HttpRequest request = HttpRequest.newBuilder(uri(trackingNumber))
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofSeconds(30))
                .build();
        CompletableFuture<HttpResponse<Stream<String>>> response =
                client.sendAsync(request, HttpResponse.BodyHandlers.ofLines());
        response.thenAccept(r -> r.body().forEach(lines::add));
        return response;
    }

    /** Collects the lines of one event (up to the blank line that ends it). */
    private static String nextEvent(BlockingQueue<String> lines) throws InterruptedException {
        StringBuilder event = new StringBuilder();
        while (true) {
            String line = lines.poll(10, TimeUnit.SECONDS);
            assertThat(line).as("event line within 10 s, got so far: %s", event).isNotNull();
            if (line.isEmpty()) {
                if (!event.isEmpty()) {
                    return event.toString();
                }
                continue;
            }
            event.append(line).append('\n');
        }
    }

    private void change(CreatedParcel parcel, ParcelStatus target, FailureReason reason) {
        statuses.changeStatus(parcel.parcelId(), new StatusChange(target, reason, "Call Nimal at +94771234567"),
                Actor.courier(courierId));
    }

    private CreatedParcel merchantParcel() {
        DeliveryContact contact = new DeliveryContact("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo",
                UUID.randomUUID(), null);
        return creation.createMerchantParcel(new NewMerchantParcel(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                contact, contact, ParcelSize.SMALL, 1000, null));
    }

    private URI uri(String trackingNumber) {
        return URI.create("http://localhost:" + port + "/api/v1/tracked-parcels/" + trackingNumber + "/events");
    }
}
