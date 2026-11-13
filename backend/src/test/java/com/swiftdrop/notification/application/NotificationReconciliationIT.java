package com.swiftdrop.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swiftdrop.delivery.api.ParcelEvents;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;
import com.swiftdrop.notification.domain.NotificationConsumerState;
import com.swiftdrop.notification.domain.NotificationConsumerStateRepository;
import com.swiftdrop.notification.domain.NotificationPendingEvent;
import com.swiftdrop.notification.domain.NotificationPendingEventRepository;
import com.swiftdrop.notification.domain.NotificationPendingEventStatus;
import com.swiftdrop.notification.domain.NotificationRecord;
import com.swiftdrop.notification.domain.NotificationRecordRepository;
import com.swiftdrop.notification.infrastructure.NotificationReconciliationProperties;
import com.swiftdrop.pricing.api.FeeCalculator;

@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password=",
        "swiftdrop.notification.reconciliation.enabled=false" // Manual trigger during test
})
@Testcontainers
class NotificationReconciliationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private NotificationEventProcessor processor;

    @Autowired
    private NotificationPendingEventRepository pendingRepo;

    @Autowired
    private NotificationConsumerStateRepository consumerStateRepo;

    @Autowired
    private NotificationRecordRepository recordRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ParcelEvents parcelEvents;

    @MockitoBean
    private FeeCalculator feeCalculator;

    private UUID customerId;
    private final Instant baseTime = Instant.parse("2026-11-13T12:00:00Z");

    @BeforeEach
    void setUp() {
        recordRepo.deleteAll();
        pendingRepo.deleteAll();
        consumerStateRepo.deleteAll();

        // Seed real customer in PostgreSQL
        customerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                customerId, "cust-" + customerId + "@example.com", "hash", "Test Customer", true);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'CUSTOMER')", customerId);
    }

    @Test
    @DisplayName("Scenario 1: Pending event recovered from ParcelEvents after grace period in PostgreSQL")
    void pendingEventRecoveredFromParcelEvents() {
        UUID parcelId = UUID.randomUUID();
        NotificationReconciliationProperties properties = NotificationReconciliationProperties.defaults();
        TestClock clock = new TestClock(baseTime);
        NotificationReconciliationService service = new NotificationReconciliationService(pendingRepo, processor, properties, clock);

        // Buffer sequence 2 (received 20 seconds ago, past 15s grace period)
        createAndBufferPending(parcelId, 2, baseTime.minusSeconds(20));

        // Missing sequence 1 is returned by ParcelEvents
        ParcelStatusChanged seq1 = createEvent(parcelId, 1, ParcelStatus.CREATED);
        when(parcelEvents.eventsBetween(parcelId, 1, 1)).thenReturn(List.of(seq1));

        int processed = service.reconcile();
        assertThat(processed).isEqualTo(1);

        // Watermark advanced to 2
        NotificationConsumerState state = consumerStateRepo.findById(parcelId).orElseThrow();
        assertThat(state.getLastAppliedSequence()).isEqualTo(2);

        // Pending event removed
        assertThat(pendingRepo.findByParcelIdOrderBySequenceAsc(parcelId)).isEmpty();

        // NotificationRecord created for customer
        List<NotificationRecord> records = recordRepo.findAll();
        assertThat(records).hasSize(2); // Sequence 1 (Parcel Booked) + Sequence 2 (Out for Delivery)
    }

    @Test
    @DisplayName("Scenario 2: Lock contention skips parcel without incrementing retry count")
    void concurrentLockContentionSkipsReconciliation() throws Exception {
        UUID parcelId = UUID.randomUUID();
        NotificationReconciliationProperties properties = NotificationReconciliationProperties.defaults();
        TestClock clock = new TestClock(baseTime);
        NotificationReconciliationService service = new NotificationReconciliationService(pendingRepo, processor, properties, clock);

        // Buffer sequence 2 (past grace period)
        NotificationPendingEvent event = createAndBufferPending(parcelId, 2, baseTime.minusSeconds(20));

        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch releaseLockLatch = new CountDownLatch(1);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> lockHolderFuture = executor.submit(() -> {
            jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
                con.setAutoCommit(false);
                try (var stmt = con.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS text), 0))")) {
                    stmt.setString(1, parcelId.toString());
                    stmt.execute();
                }
                lockAcquiredLatch.countDown();
                try {
                    releaseLockLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                con.commit();
                return null;
            });
        });

        // Wait until background thread acquires the lock
        assertThat(lockAcquiredLatch.await(3, TimeUnit.SECONDS)).isTrue();

        try {
            // Reconcile attempt while lock is busy
            int processed = service.reconcile();
            assertThat(processed).isEqualTo(0);

            // Verified retry count remains 0
            NotificationPendingEvent persisted = pendingRepo.findById(event.getEventId()).orElseThrow();
            assertThat(persisted.getRetryCount()).isEqualTo(0);
            assertThat(persisted.getStatus()).isEqualTo(NotificationPendingEventStatus.PENDING);
        } finally {
            releaseLockLatch.countDown();
            lockHolderFuture.get(3, TimeUnit.SECONDS);
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("Scenario 3: Five incomplete attempts transition event to UNRESOLVED in PostgreSQL")
    void fiveIncompleteAttemptsQuarantineToUnresolved() {
        UUID parcelId = UUID.randomUUID();
        NotificationReconciliationProperties properties = NotificationReconciliationProperties.defaults();
        TestClock clock = new TestClock(baseTime);
        NotificationReconciliationService service = new NotificationReconciliationService(pendingRepo, processor, properties, clock);

        NotificationPendingEvent event = createAndBufferPending(parcelId, 2, baseTime.minusSeconds(20));

        // ParcelEvents returns empty (history missing)
        when(parcelEvents.eventsBetween(any(), anyInt(), anyInt())).thenReturn(List.of());

        // Attempts 1 to 4
        for (int i = 1; i <= 4; i++) {
            clock.set(baseTime.plusSeconds(i * 30));
            service.reconcile();
            NotificationPendingEvent current = pendingRepo.findById(event.getEventId()).orElseThrow();
            assertThat(current.getRetryCount()).isEqualTo(i);
            assertThat(current.getStatus()).isEqualTo(NotificationPendingEventStatus.PENDING);
        }

        // Attempt 5 -> Quarantined
        clock.set(baseTime.plusSeconds(150));
        service.reconcile();
        NotificationPendingEvent quarantined = pendingRepo.findById(event.getEventId()).orElseThrow();
        assertThat(quarantined.getRetryCount()).isEqualTo(5);
        assertThat(quarantined.getStatus()).isEqualTo(NotificationPendingEventStatus.UNRESOLVED);

        // Attempt 6 -> Ignored (partial index excludes UNRESOLVED)
        clock.set(baseTime.plusSeconds(180));
        int processedAfterQuarantine = service.reconcile();
        assertThat(processedAfterQuarantine).isEqualTo(0);
    }

    private NotificationPendingEvent createAndBufferPending(UUID parcelId, int sequence, Instant receivedAt) {
        ParcelStatusChanged event = createEvent(parcelId, sequence, ParcelStatus.OUT_FOR_DELIVERY);
        String payload = createPayload(event);
        NotificationPendingEvent pending = new NotificationPendingEvent(
                event.eventId(),
                parcelId,
                sequence,
                payload,
                receivedAt);
        return pendingRepo.save(pending);
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
                        1));
    }

    private String createPayload(ParcelStatusChanged event) {
        return "{"
                + "\"eventId\":\"" + event.eventId() + "\","
                + "\"eventType\":\"" + event.eventType() + "\","
                + "\"schemaVersion\":" + event.schemaVersion() + ","
                + "\"occurredAt\":\"" + event.occurredAt() + "\","
                + "\"producer\":\"" + event.producer() + "\","
                + "\"data\":{"
                + "\"parcelId\":\"" + event.data().parcelId() + "\","
                + "\"trackingNumber\":\"" + event.data().trackingNumber() + "\","
                + "\"newStatus\":\"" + event.data().newStatus() + "\","
                + "\"sequence\":" + event.data().sequence() + ","
                + "\"source\":\"" + event.data().source() + "\","
                + "\"ownerCustomerId\":\"" + event.data().ownerCustomerId() + "\""
                + "}"
                + "}";
    }

    static class TestClock extends Clock {
        private Instant now;

        TestClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

