package com.swiftdrop.merchant.domain;

import java.time.Instant;
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
 * An event that was not applied because it contradicts what is stored. Kept for review instead of being
 * dropped; the payload is the event envelope (no personal data).
 */
@Entity
@Table(name = "merchant_event_anomalies")
public class MerchantEventAnomaly {

    /** Why the event was quarantined. Fixed values, so they are safe as a metric tag. */
    public enum Kind {
        /** Its event ID is already stored for another parcel or sequence. */
        IDENTITY_CONFLICT,
        /** Another event ID is already stored for the same parcel and sequence. */
        SEQUENCE_CONFLICT,
        /** Its merchant does not own the parcel, or differs from the parcel's recorded merchant. */
        OWNERSHIP_MISMATCH,
        /** An unknown event ID for a sequence that was already applied and whose ID is no longer kept. */
        UNKNOWN_DUPLICATE
    }

    static final int MAX_DETAIL_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "parcel_id", updatable = false)
    private UUID parcelId;

    @Column(updatable = false)
    private Integer sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private Kind kind;

    @Column(nullable = false, updatable = false, length = MAX_DETAIL_LENGTH)
    private String detail;

    @Column(nullable = false, updatable = false)
    private String payload;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected MerchantEventAnomaly() {
        // for JPA
    }

    public MerchantEventAnomaly(UUID eventId, UUID parcelId, Integer sequence, Kind kind, String detail,
            String payload, Instant receivedAt) {
        this.eventId = eventId;
        this.parcelId = parcelId;
        this.sequence = sequence;
        this.kind = kind;
        this.detail = detail.length() > MAX_DETAIL_LENGTH ? detail.substring(0, MAX_DETAIL_LENGTH) : detail;
        this.payload = payload;
        this.receivedAt = receivedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public Integer getSequence() {
        return sequence;
    }

    public Kind getKind() {
        return kind;
    }

    public String getDetail() {
        return detail;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
