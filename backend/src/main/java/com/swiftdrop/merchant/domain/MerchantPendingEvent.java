package com.swiftdrop.merchant.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An event that arrived before an earlier sequence of its parcel (ADR 0004 §4.3). It is applied once the
 * gap is closed, by the missing event or by reconciliation, and then deleted. The payload is the event
 * envelope exactly as received.
 */
@Entity
@Table(name = "merchant_pending_events")
public class MerchantPendingEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    @Column(nullable = false, updatable = false)
    private int sequence;

    @Column(nullable = false, updatable = false)
    private String payload;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "reconcile_attempts", nullable = false)
    private int reconcileAttempts;

    @Column(name = "next_reconcile_at", nullable = false)
    private Instant nextReconcileAt;

    protected MerchantPendingEvent() {
        // for JPA
    }

    public MerchantPendingEvent(UUID eventId, UUID parcelId, int sequence, String payload, Instant receivedAt,
            Instant nextReconcileAt) {
        this.eventId = eventId;
        this.parcelId = parcelId;
        this.sequence = sequence;
        this.payload = payload;
        this.receivedAt = receivedAt;
        this.nextReconcileAt = nextReconcileAt;
    }

    /** A reconciliation did not close the gap; try again after the backoff. */
    public void reconcileFailed(Instant now, Duration backoff) {
        this.reconcileAttempts++;
        this.nextReconcileAt = now.plus(backoff);
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

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public int getReconcileAttempts() {
        return reconcileAttempts;
    }

    public Instant getNextReconcileAt() {
        return nextReconcileAt;
    }
}
