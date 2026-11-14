package com.swiftdrop.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.springframework.data.domain.Persistable;

/**
 * Consumer state tracking the latest sequentially applied event for each parcel.
 * Corresponds to table {@code notification_consumer_state} (ADR 0004 §4.3).
 */
@Entity
@Table(name = "notification_consumer_state")
public class NotificationConsumerState implements Persistable<UUID> {

    @Id
    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "last_applied_sequence", nullable = false)
    private int lastAppliedSequence;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @jakarta.persistence.Transient
    private boolean isNew = true;

    protected NotificationConsumerState() {
        this.isNew = false;
    }

    public NotificationConsumerState(UUID parcelId, int lastAppliedSequence, Instant updatedAt) {
        this.parcelId = Objects.requireNonNull(parcelId, "parcelId");
        if (lastAppliedSequence < 1) {
            throw new IllegalArgumentException("lastAppliedSequence must be >= 1");
        }
        this.lastAppliedSequence = lastAppliedSequence;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.isNew = true;
    }

    public void advanceSequence(int sequence, Instant updatedAt) {
        if (sequence <= this.lastAppliedSequence) {
            throw new IllegalArgumentException("Sequence must advance strictly beyond " + this.lastAppliedSequence);
        }
        this.lastAppliedSequence = sequence;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @Override
    public UUID getId() {
        return parcelId;
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

    public UUID getParcelId() {
        return parcelId;
    }

    public int getLastAppliedSequence() {
        return lastAppliedSequence;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

