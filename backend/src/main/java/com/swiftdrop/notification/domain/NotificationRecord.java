package com.swiftdrop.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A notification record created for a specific recipient, event, and channel.
 * Corresponds to table {@code notification_records} (ADR 0003, docs/architecture/notification-database-design.md).
 */
@Entity
@Table(name = "notification_records")
public class NotificationRecord {

    public static final int MAX_TRACKING_NUMBER_LENGTH = 16;
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_FAILURE_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private UUID recipientUserId;

    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    @Column(name = "tracking_number", nullable = false, length = MAX_TRACKING_NUMBER_LENGTH, updatable = false)
    private String trackingNumber;

    @Column(nullable = false, length = MAX_TITLE_LENGTH, updatable = false)
    private String title;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private NotificationChannel channel;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "read_at")
    private Instant readAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private NotificationDeliveryStatus deliveryStatus;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failure_reason", length = MAX_FAILURE_REASON_LENGTH)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NotificationRecord() {
        // for JPA
    }

    public NotificationRecord(
            UUID eventId,
            UUID recipientUserId,
            UUID parcelId,
            String trackingNumber,
            String title,
            String message,
            NotificationChannel channel,
            NotificationDeliveryStatus deliveryStatus,
            Instant createdAt) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.recipientUserId = Objects.requireNonNull(recipientUserId, "recipientUserId");
        this.parcelId = Objects.requireNonNull(parcelId, "parcelId");
        this.trackingNumber = Objects.requireNonNull(trackingNumber, "trackingNumber");
        this.title = Objects.requireNonNull(title, "title");
        this.message = Objects.requireNonNull(message, "message");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.deliveryStatus = Objects.requireNonNull(deliveryStatus, "deliveryStatus");
        this.isRead = false;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public void markAsRead(Instant readAt) {
        this.isRead = true;
        this.readAt = Objects.requireNonNull(readAt, "readAt");
    }

    public void markAsSent(Instant sentAt) {
        this.deliveryStatus = NotificationDeliveryStatus.SENT;
        this.sentAt = Objects.requireNonNull(sentAt, "sentAt");
        this.failureReason = null;
    }

    public void markAsFailed(String failureReason) {
        this.deliveryStatus = NotificationDeliveryStatus.FAILED;
        if (failureReason != null && failureReason.length() > MAX_FAILURE_REASON_LENGTH) {
            this.failureReason = failureReason.substring(0, MAX_FAILURE_REASON_LENGTH);
        } else {
            this.failureReason = failureReason;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getRecipientUserId() {
        return recipientUserId;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public boolean isRead() {
        return isRead;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public NotificationDeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

