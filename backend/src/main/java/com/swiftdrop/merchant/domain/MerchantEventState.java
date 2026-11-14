package com.swiftdrop.merchant.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Processing state of one merchant parcel's events (ADR 0004 §4.3). The row is locked while an event of
 * the parcel is handled, which serializes concurrent deliveries. Its merchant never changes.
 */
@Entity
@Table(name = "merchant_event_states")
public class MerchantEventState {

    @Id
    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "last_applied_sequence", nullable = false)
    private int lastAppliedSequence;

    @Column(nullable = false)
    private boolean stuck;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MerchantEventState() {
        // for JPA
    }

    public MerchantEventState(UUID parcelId, UUID merchantId, Instant now) {
        this.parcelId = parcelId;
        this.merchantId = merchantId;
        this.updatedAt = now;
    }

    /** Records that the event with this sequence was applied; only the next sequence is accepted. */
    public void advanceTo(int sequence, Instant now) {
        if (sequence != lastAppliedSequence + 1) {
            throw new IllegalStateException("sequence " + sequence + " does not follow " + lastAppliedSequence);
        }
        this.lastAppliedSequence = sequence;
        this.updatedAt = now;
    }

    public void markStuck(Instant now) {
        this.stuck = true;
        this.updatedAt = now;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public int getLastAppliedSequence() {
        return lastAppliedSequence;
    }

    public boolean isStuck() {
        return stuck;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
