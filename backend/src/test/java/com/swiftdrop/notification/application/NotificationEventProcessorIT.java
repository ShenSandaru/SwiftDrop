package com.swiftdrop.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
import com.swiftdrop.notification.domain.NotificationRecord;
import com.swiftdrop.notification.domain.NotificationRecordRepository;
import com.swiftdrop.pricing.api.FeeCalculator;

@SpringBootTest(properties = {
        "swiftdrop.notification.consumer.enabled=true",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
class NotificationEventProcessorIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private NotificationEventProcessor processor;

    @Autowired
    private NotificationConsumerStateRepository consumerStateRepository;

    @Autowired
    private NotificationPendingEventRepository pendingEventRepository;

    @Autowired
    private NotificationRecordRepository recordRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ParcelEvents parcelEvents;

    @MockitoBean
    private FeeCalculator feeCalculator;

    private UUID parcelId;
    private UUID customerId;
    private final String trackingNumber = "SD1234567890";
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        parcelId = UUID.randomUUID();
        customerId = UUID.randomUUID();

        // Clean tables
        recordRepository.deleteAll();
        pendingEventRepository.deleteAll();
        consumerStateRepository.deleteAll();

        // Seed real customer in PostgreSQL
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                customerId, "customer-" + customerId + "@example.com", "hash", "Test Customer", true);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'CUSTOMER')", customerId);
    }

    private ParcelStatusChanged createEvent(int sequence, ParcelStatus newStatus) {
        return ParcelStatusChanged.of(
                UUID.randomUUID(),
                now,
                new ParcelStatusChangedData(
                        parcelId,
                        trackingNumber,
                        ParcelStatus.CREATED,
                        newStatus,
                        sequence,
                        ParcelSource.CUSTOMER,
                        customerId,
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    private String createRawPayload(ParcelStatusChanged event) {
        return "{"
                + "\"eventId\":\"" + event.eventId() + "\","
                + "\"eventType\":\"" + event.eventType() + "\","
                + "\"schemaVersion\":" + event.schemaVersion() + ","
                + "\"occurredAt\":\"" + event.occurredAt() + "\","
                + "\"producer\":\"" + event.producer() + "\","
                + "\"data\":{"
                + "\"parcelId\":\"" + event.data().parcelId() + "\","
                + "\"trackingNumber\":\"" + event.data().trackingNumber() + "\","
                + (event.data().previousStatus() != null ? "\"previousStatus\":\"" + event.data().previousStatus() + "\"," : "")
                + "\"newStatus\":\"" + event.data().newStatus() + "\","
                + "\"sequence\":" + event.data().sequence() + ","
                + "\"source\":\"" + event.data().source() + "\","
                + "\"ownerCustomerId\":\"" + event.data().ownerCustomerId() + "\""
                + "}"
                + "}";
    }

    @Test
    @DisplayName("In-order events sequentially advance consumer state watermark and persist notification records")
    void inOrderEventsAdvanceWatermarkAndPersistNotifications() {
        ParcelStatusChanged event1 = createEvent(1, ParcelStatus.CREATED);
        String raw1 = createRawPayload(event1);

        processor.process(event1, raw1);

        Optional<NotificationConsumerState> state1 = consumerStateRepository.findById(parcelId);
        assertThat(state1).isPresent();
        assertThat(state1.get().getLastAppliedSequence()).isEqualTo(1);

        List<NotificationRecord> records1 = recordRepository.findAll();
        assertThat(records1).hasSize(1);
        assertThat(records1.getFirst().getTitle()).isEqualTo("Parcel Booked");
        assertThat(records1.getFirst().getRecipientUserId()).isEqualTo(customerId);

        // Process sequence 2
        ParcelStatusChanged event2 = createEvent(2, ParcelStatus.PICKED_UP);
        String raw2 = createRawPayload(event2);

        processor.process(event2, raw2);

        Optional<NotificationConsumerState> state2 = consumerStateRepository.findById(parcelId);
        assertThat(state2).isPresent();
        assertThat(state2.get().getLastAppliedSequence()).isEqualTo(2);

        List<NotificationRecord> records2 = recordRepository.findAll();
        assertThat(records2).hasSize(2);
    }

    @Test
    @DisplayName("Duplicate event delivery does not duplicate records or advance watermark")
    void duplicateEventDeliveryIsIdempotent() {
        ParcelStatusChanged event1 = createEvent(1, ParcelStatus.CREATED);
        String raw1 = createRawPayload(event1);

        processor.process(event1, raw1);
        processor.process(event1, raw1); // re-deliver sequence 1

        Optional<NotificationConsumerState> state = consumerStateRepository.findById(parcelId);
        assertThat(state).isPresent();
        assertThat(state.get().getLastAppliedSequence()).isEqualTo(1);

        List<NotificationRecord> records = recordRepository.findAll();
        assertThat(records).hasSize(1);
    }

    @Test
    @DisplayName("Out-of-order event buffers in pending table, and gap recovery drains buffer up to the watermark")
    void outOfOrderEventBuffersAndDrainsOnGapRecovery() {
        // Step 1: Sequence 1 arrives
        ParcelStatusChanged event1 = createEvent(1, ParcelStatus.CREATED);
        processor.process(event1, createRawPayload(event1));
        assertThat(consumerStateRepository.findById(parcelId).get().getLastAppliedSequence()).isEqualTo(1);

        // Step 2: Sequence 3 arrives (gap at 2), gap recovery fails (e.g. empty delivery history)
        ParcelStatusChanged event3 = createEvent(3, ParcelStatus.OUT_FOR_DELIVERY);
        String raw3 = createRawPayload(event3);
        when(parcelEvents.eventsBetween(parcelId, 2, 2)).thenReturn(List.of()); // not yet in history

        processor.process(event3, raw3);

        // Watermark remains at 1, event 3 buffered in pending events table
        assertThat(consumerStateRepository.findById(parcelId).get().getLastAppliedSequence()).isEqualTo(1);
        Optional<NotificationPendingEvent> pending3 = pendingEventRepository.findByParcelIdAndSequence(parcelId, 3);
        assertThat(pending3).isPresent();

        // Step 3: Sequence 2 arrives in-order!
        // When sequence 2 is processed, it should advance watermark to 2, then drain buffered sequence 3 and advance to 3
        ParcelStatusChanged event2 = createEvent(2, ParcelStatus.PICKED_UP);
        processor.process(event2, createRawPayload(event2));

        Optional<NotificationConsumerState> finalState = consumerStateRepository.findById(parcelId);
        assertThat(finalState).isPresent();
        assertThat(finalState.get().getLastAppliedSequence()).isEqualTo(3);

        // Buffer for sequence 3 must be deleted
        assertThat(pendingEventRepository.findByParcelIdAndSequence(parcelId, 3)).isEmpty();

        // Notification records created for CREATED, PICKED_UP, and OUT_FOR_DELIVERY
        List<NotificationRecord> records = recordRepository.findAll();
        assertThat(records).hasSize(3);
    }

    @Test
    @DisplayName("Out-of-order event triggers gap recovery query and drains full sequence if history is returned")
    void gapRecoveryAppliesHistoryDirectly() {
        // Sequence 1 arrives
        ParcelStatusChanged event1 = createEvent(1, ParcelStatus.CREATED);
        processor.process(event1, createRawPayload(event1));

        // Sequence 3 arrives, but Delivery history CAN provide sequence 2
        ParcelStatusChanged event2 = createEvent(2, ParcelStatus.PICKED_UP);
        when(parcelEvents.eventsBetween(parcelId, 2, 2)).thenReturn(List.of(event2));

        ParcelStatusChanged event3 = createEvent(3, ParcelStatus.OUT_FOR_DELIVERY);
        processor.process(event3, createRawPayload(event3));

        // Watermark must immediately advance to 3
        Optional<NotificationConsumerState> state = consumerStateRepository.findById(parcelId);
        assertThat(state).isPresent();
        assertThat(state.get().getLastAppliedSequence()).isEqualTo(3);

        // Pending events table must be clean
        assertThat(pendingEventRepository.findAll()).isEmpty();

        // All 3 notifications created
        assertThat(recordRepository.findAll()).hasSize(3);
    }

    @Test
    @DisplayName("Concurrent processing for the same parcel is serialized by advisory lock without race conditions")
    void concurrentProcessingSerializedByAdvisoryLock() throws Exception {
        // Prepare events 1 and 2
        ParcelStatusChanged event1 = createEvent(1, ParcelStatus.CREATED);
        ParcelStatusChanged event2 = createEvent(2, ParcelStatus.PICKED_UP);
        String raw1 = createRawPayload(event1);
        String raw2 = createRawPayload(event2);

        // Gap recovery won't be needed if 1 applies before 2, but if 2 runs first, gap recovery can provide 1
        when(parcelEvents.eventsBetween(parcelId, 1, 1)).thenReturn(List.of(event1));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<?> future1 = executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await(5, TimeUnit.SECONDS);
                processor.process(event1, raw1);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> future2 = executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await(5, TimeUnit.SECONDS);
                processor.process(event2, raw2);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Ensure both threads are ready, then release them concurrently
        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();

        // Wait for both to complete
        future1.get(10, TimeUnit.SECONDS);
        future2.get(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        // Final watermark must be exactly 2
        Optional<NotificationConsumerState> state = consumerStateRepository.findById(parcelId);
        assertThat(state).isPresent();
        assertThat(state.get().getLastAppliedSequence()).isEqualTo(2);

        // Pending events buffer must be empty
        assertThat(pendingEventRepository.findAll()).isEmpty();

        // Exactly 2 notifications created (no duplicates)
        List<NotificationRecord> records = recordRepository.findAll();
        assertThat(records).hasSize(2);
    }
}
