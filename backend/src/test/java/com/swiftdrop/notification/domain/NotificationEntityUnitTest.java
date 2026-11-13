package com.swiftdrop.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationEntityUnitTest {

    @Test
    @DisplayName("NotificationRecord initializes properly and manages read/delivery status transitions")
    void notificationRecordLifecycle() {
        UUID eventId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        String trackingNumber = "SD23456789AB";
        Instant now = Instant.now();

        NotificationRecord record = new NotificationRecord(
                eventId,
                recipientUserId,
                parcelId,
                trackingNumber,
                "Parcel Created",
                "Your parcel has been booked.",
                NotificationChannel.IN_APP,
                NotificationDeliveryStatus.PENDING,
                now);

        assertThat(record.getEventId()).isEqualTo(eventId);
        assertThat(record.getRecipientUserId()).isEqualTo(recipientUserId);
        assertThat(record.getParcelId()).isEqualTo(parcelId);
        assertThat(record.getTrackingNumber()).isEqualTo(trackingNumber);
        assertThat(record.getTitle()).isEqualTo("Parcel Created");
        assertThat(record.getMessage()).isEqualTo("Your parcel has been booked.");
        assertThat(record.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(record.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(record.isRead()).isFalse();
        assertThat(record.getReadAt()).isNull();
        assertThat(record.getSentAt()).isNull();
        assertThat(record.getFailureReason()).isNull();

        Instant readAt = now.plusSeconds(30);
        record.markAsRead(readAt);
        assertThat(record.isRead()).isTrue();
        assertThat(record.getReadAt()).isEqualTo(readAt);

        Instant sentAt = now.plusSeconds(10);
        record.markAsSent(sentAt);
        assertThat(record.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(record.getSentAt()).isEqualTo(sentAt);

        record.markAsFailed("Network timeout");
        assertThat(record.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(record.getFailureReason()).isEqualTo("Network timeout");
    }

    @Test
    @DisplayName("NotificationConsumerState tracks sequence and rejects backward steps")
    void notificationConsumerStateSequenceValidation() {
        UUID parcelId = UUID.randomUUID();
        Instant now = Instant.now();

        NotificationConsumerState state = new NotificationConsumerState(parcelId, 1, now);
        assertThat(state.getParcelId()).isEqualTo(parcelId);
        assertThat(state.getLastAppliedSequence()).isEqualTo(1);
        assertThat(state.isNew()).isTrue();

        Instant later = now.plusSeconds(5);
        state.advanceSequence(2, later);
        assertThat(state.getLastAppliedSequence()).isEqualTo(2);
        assertThat(state.getUpdatedAt()).isEqualTo(later);

        assertThatThrownBy(() -> state.advanceSequence(2, later.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must advance strictly beyond 2");

        assertThatThrownBy(() -> state.advanceSequence(1, later.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("NotificationPendingEvent records reconciliation attempts and status transitions")
    void notificationPendingEventTransitions() {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        Instant receivedAt = Instant.now();
        String payload = "{\"parcelId\":\"" + parcelId + "\",\"sequence\":3}";

        NotificationPendingEvent pendingEvent = new NotificationPendingEvent(
                eventId,
                parcelId,
                3,
                payload,
                receivedAt);

        assertThat(pendingEvent.getEventId()).isEqualTo(eventId);
        assertThat(pendingEvent.getParcelId()).isEqualTo(parcelId);
        assertThat(pendingEvent.getSequence()).isEqualTo(3);
        assertThat(pendingEvent.getPayload()).isEqualTo(payload);
        assertThat(pendingEvent.getRetryCount()).isZero();
        assertThat(pendingEvent.getStatus()).isEqualTo(NotificationPendingEventStatus.PENDING);
        assertThat(pendingEvent.isNew()).isTrue();

        Instant reconcileAt = receivedAt.plusSeconds(60);
        pendingEvent.recordReconciliationAttempt(reconcileAt);
        assertThat(pendingEvent.getRetryCount()).isEqualTo(1);
        assertThat(pendingEvent.getLastReconciledAt()).isEqualTo(reconcileAt);

        pendingEvent.markUnresolved();
        assertThat(pendingEvent.getStatus()).isEqualTo(NotificationPendingEventStatus.UNRESOLVED);
    }

    @Test
    @DisplayName("Enums conform strictly to allowed database check constraint string representations")
    void enumConstraintValues() {
        assertThat(NotificationChannel.valueOf("IN_APP")).isEqualTo(NotificationChannel.IN_APP);
        assertThat(NotificationChannel.valueOf("EMAIL")).isEqualTo(NotificationChannel.EMAIL);
        assertThat(NotificationChannel.valueOf("SMS")).isEqualTo(NotificationChannel.SMS);
        assertThat(NotificationChannel.valueOf("PUSH")).isEqualTo(NotificationChannel.PUSH);

        assertThat(NotificationDeliveryStatus.valueOf("PENDING")).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(NotificationDeliveryStatus.valueOf("SENT")).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(NotificationDeliveryStatus.valueOf("FAILED")).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(NotificationDeliveryStatus.valueOf("NOT_APPLICABLE")).isEqualTo(NotificationDeliveryStatus.NOT_APPLICABLE);

        assertThat(NotificationPendingEventStatus.valueOf("PENDING")).isEqualTo(NotificationPendingEventStatus.PENDING);
        assertThat(NotificationPendingEventStatus.valueOf("UNRESOLVED")).isEqualTo(NotificationPendingEventStatus.UNRESOLVED);
    }
}

