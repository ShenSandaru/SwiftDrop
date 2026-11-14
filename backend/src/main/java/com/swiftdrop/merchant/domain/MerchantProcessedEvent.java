package com.swiftdrop.merchant.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An event that was applied, kept to recognize redeliveries (ADR 0004 §4.3, retention 30 days). */
@Entity
@Table(name = "merchant_processed_events")
public class MerchantProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    @Column(nullable = false, updatable = false)
    private int sequence;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected MerchantProcessedEvent() {
        // for JPA
    }

    public MerchantProcessedEvent(UUID eventId, UUID parcelId, int sequence, Instant processedAt) {
        this.eventId = eventId;
        this.parcelId = parcelId;
        this.sequence = sequence;
        this.processedAt = processedAt;
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

    public Instant getProcessedAt() {
        return processedAt;
    }
}
