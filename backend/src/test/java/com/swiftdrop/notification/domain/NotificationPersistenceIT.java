package com.swiftdrop.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;

import com.swiftdrop.pricing.api.FeeCalculator;

@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
@Transactional
class NotificationPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private NotificationRecordRepository recordRepository;

    @Autowired
    private NotificationConsumerStateRepository consumerStateRepository;

    @Autowired
    private NotificationPendingEventRepository pendingEventRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private FeeCalculator feeCalculator;

    private UUID userId;

    @BeforeEach
    void setupUser() {
        userId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, ?, true, NOW())",
                userId, "notify-test-" + userId + "@example.com", "Notify Test User");
    }

    @Test
    @DisplayName("NotificationRecord persists, round-trips correctly, and enforces constraints")
    void persistAndQueryNotificationRecord() {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();

        NotificationRecord record = new NotificationRecord(
                eventId,
                userId,
                parcelId,
                "SD23456789AB",
                "Parcel Out for Delivery",
                "Your driver is arriving soon.",
                NotificationChannel.IN_APP,
                NotificationDeliveryStatus.PENDING,
                NOW);

        NotificationRecord saved = recordRepository.saveAndFlush(record);
        entityManager.clear();

        Optional<NotificationRecord> found = recordRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTrackingNumber()).isEqualTo("SD23456789AB");
        assertThat(found.get().getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(found.get().getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(found.get().isRead()).isFalse();

        // Feed query
        Page<NotificationRecord> feed = recordRepository.findByRecipientUserId(
                userId,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));
        assertThat(feed.getContent()).hasSize(1);

        // Unread queries
        Page<NotificationRecord> unread = recordRepository.findByRecipientUserIdAndChannelAndIsReadFalse(
                userId,
                NotificationChannel.IN_APP,
                PageRequest.of(0, 10));
        assertThat(unread.getContent()).hasSize(1);
        assertThat(recordRepository.countByRecipientUserIdAndChannelAndIsReadFalse(userId, NotificationChannel.IN_APP))
                .isEqualTo(1);

        // Mark read
        saved = found.get();
        saved.markAsRead(NOW.plusSeconds(10));
        recordRepository.saveAndFlush(saved);
        entityManager.clear();

        assertThat(recordRepository.countByRecipientUserIdAndChannelAndIsReadFalse(userId, NotificationChannel.IN_APP))
                .isZero();
    }

    @Test
    @DisplayName("NotificationRecord enforces unique constraint on (eventId, channel, recipientUserId)")
    void duplicateNotificationRecordRejected() {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();

        NotificationRecord r1 = new NotificationRecord(
                eventId,
                userId,
                parcelId,
                "SD23456789AB",
                "Parcel Out for Delivery",
                "Message 1",
                NotificationChannel.IN_APP,
                NotificationDeliveryStatus.PENDING,
                NOW);
        recordRepository.saveAndFlush(r1);

        NotificationRecord r2 = new NotificationRecord(
                eventId,
                userId,
                parcelId,
                "SD23456789AB",
                "Duplicate Event",
                "Message 2",
                NotificationChannel.IN_APP,
                NotificationDeliveryStatus.PENDING,
                NOW);

        assertThatThrownBy(() -> recordRepository.saveAndFlush(r2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("NotificationConsumerState tracks watermark per parcel")
    void persistAndAdvanceConsumerState() {
        UUID parcelId = UUID.randomUUID();

        NotificationConsumerState state = new NotificationConsumerState(parcelId, 1, NOW);
        consumerStateRepository.saveAndFlush(state);
        entityManager.clear();

        Optional<NotificationConsumerState> loaded = consumerStateRepository.findById(parcelId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getLastAppliedSequence()).isEqualTo(1);

        loaded.get().advanceSequence(2, NOW.plusSeconds(5));
        consumerStateRepository.saveAndFlush(loaded.get());
        entityManager.clear();

        NotificationConsumerState updated = consumerStateRepository.findById(parcelId).orElseThrow();
        assertThat(updated.getLastAppliedSequence()).isEqualTo(2);
    }

    @Test
    @DisplayName("NotificationPendingEvent stores and retrieves JSONB payload, enforces sequence uniqueness")
    void persistAndQueryPendingEvents() {
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        String jsonPayload1 = "{\"parcelId\":\"" + parcelId + "\",\"sequence\":2,\"status\":\"IN_TRANSIT\"}";
        String jsonPayload2 = "{\"parcelId\":\"" + parcelId + "\",\"sequence\":3,\"status\":\"OUT_FOR_DELIVERY\"}";

        NotificationPendingEvent pe1 = new NotificationPendingEvent(eventId1, parcelId, 2, jsonPayload1, NOW.minusSeconds(120));
        NotificationPendingEvent pe2 = new NotificationPendingEvent(eventId2, parcelId, 3, jsonPayload2, NOW.minusSeconds(60));

        pendingEventRepository.saveAndFlush(pe1);
        pendingEventRepository.saveAndFlush(pe2);
        entityManager.clear();

        List<NotificationPendingEvent> parcelEvents = pendingEventRepository.findByParcelIdOrderBySequenceAsc(parcelId);
        assertThat(parcelEvents).hasSize(2);
        assertThat(parcelEvents.get(0).getSequence()).isEqualTo(2);
        assertThat(parcelEvents.get(0).getPayload()).contains(parcelId.toString(), "IN_TRANSIT");
        assertThat(parcelEvents.get(1).getSequence()).isEqualTo(3);
        assertThat(parcelEvents.get(1).getPayload()).contains(parcelId.toString(), "OUT_FOR_DELIVERY");

        // Discovery for reconciliation scheduler
        List<NotificationPendingEvent> dueEvents = pendingEventRepository
                .findByStatusAndReceivedAtBeforeOrderByReceivedAtAsc(
                        NotificationPendingEventStatus.PENDING,
                        NOW.minusSeconds(30),
                        Limit.of(10));
        assertThat(dueEvents).hasSize(2);

        // Unique constraint check: duplicate sequence for same parcel rejected
        UUID eventIdDuplicate = UUID.randomUUID();
        NotificationPendingEvent peDuplicate = new NotificationPendingEvent(
                eventIdDuplicate,
                parcelId,
                2,
                jsonPayload1,
                NOW);

        assertThatThrownBy(() -> pendingEventRepository.saveAndFlush(peDuplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

