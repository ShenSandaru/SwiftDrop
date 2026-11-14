package com.swiftdrop.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Out-of-order event buffered until preceding events arrive or reconciliation catches up.
 * Corresponds to table {@code notification_pending_events} (ADR 0004 §4.3).
 */
@Entity
@Table(name = "notification_pending_events")
public class NotificationPendingEvent implements Persistable<UUID> {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    @Column(nullable = false, updatable = false)
    private int sequence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationPendingEventStatus status;

    @Column(name = "last_reconciled_at")
    private Instant lastReconciledAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @jakarta.persistence.Transient
    private boolean isNew = true;

    protected NotificationPendingEvent() {
        this.isNew = false;
    }

    public NotificationPendingEvent(UUID eventId, UUID parcelId, int sequence, String payload, Instant receivedAt) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.parcelId = Objects.requireNonNull(parcelId, "parcelId");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be >= 1");
        }
        this.sequence = sequence;
        this.payload = Objects.requireNonNull(payload, "payload");
        this.retryCount = 0;
        this.status = NotificationPendingEventStatus.PENDING;
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        this.isNew = true;
    }

    public void recordReconciliationAttempt(Instant reconciledAt) {
        this.retryCount++;
        this.lastReconciledAt = Objects.requireNonNull(reconciledAt, "reconciledAt");
    }

    public void markUnresolved() {
        this.status = NotificationPendingEventStatus.UNRESOLVED;
    }

    @Override
    public UUID getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @jakarta.persistence.PostLoad
    @jakarta.persistence.PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public int getSequence() {
        return sequence;
    }

    public String getPayload() {
        return payload;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public NotificationPendingEventStatus getStatus() {
        return status;
    }

    public Instant getLastReconciledAt() {
        return lastReconciledAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}

