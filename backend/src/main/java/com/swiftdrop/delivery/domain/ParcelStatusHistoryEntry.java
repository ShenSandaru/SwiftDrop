package com.swiftdrop.delivery.domain;

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

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * One status change of a parcel, never updated after it is written. Its event ID and sequence are
 * the ones in the outbox event, so events can be rebuilt from history (ADR 0004 §4.3).
 */
@Entity
@Table(name = "parcel_status_history")
public class ParcelStatusHistoryEntry {

    public static final int MAX_NOTE_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    @Column(nullable = false, updatable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30, updatable = false)
    private ParcelStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30, updatable = false)
    private ParcelStatus toStatus;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, length = 50, updatable = false)
    private ActorRole actorRole;

    /** Personal data; never copied into events (ADR 0006 §4). */
    @Column(length = MAX_NOTE_LENGTH, updatable = false)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason_code", length = 40, updatable = false)
    private FailureReason failureReasonCode;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected ParcelStatusHistoryEntry() {
        // for JPA
    }

    /**
     * @param fromStatus {@code null} only for the creation entry (sequence 1, to {@code CREATED})
     * @param actorUserId {@code null} when the actor is not a user, such as the merchant API
     * @param failureReasonCode required for {@code FAILED}, otherwise {@code null}
     */
    public ParcelStatusHistoryEntry(UUID parcelId, int sequence, ParcelStatus fromStatus, ParcelStatus toStatus,
            UUID actorUserId, ActorRole actorRole, String note, FailureReason failureReasonCode, UUID eventId,
            Instant occurredAt) {
        this.parcelId = Objects.requireNonNull(parcelId, "parcelId");
        this.toStatus = Objects.requireNonNull(toStatus, "toStatus");
        this.actorRole = Objects.requireNonNull(actorRole, "actorRole");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        boolean creation = sequence == 1;
        if (sequence < 1 || creation != (fromStatus == null && toStatus == ParcelStatus.CREATED)) {
            throw new IllegalArgumentException("Only sequence 1 is the creation entry");
        }
        if ((toStatus == ParcelStatus.FAILED) != (failureReasonCode != null)) {
            throw new IllegalArgumentException("A failure reason is required for FAILED and only for FAILED");
        }
        this.sequence = sequence;
        this.fromStatus = fromStatus;
        this.actorUserId = actorUserId;
        this.note = note == null || note.isBlank() ? null : note.trim();
        if (this.note != null && this.note.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("note must be at most 500 characters");
        }
        this.failureReasonCode = failureReasonCode;
    }

    public UUID getId() {
        return id;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public int getSequence() {
        return sequence;
    }

    public ParcelStatus getFromStatus() {
        return fromStatus;
    }

    public ParcelStatus getToStatus() {
        return toStatus;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public ActorRole getActorRole() {
        return actorRole;
    }

    public String getNote() {
        return note;
    }

    public FailureReason getFailureReasonCode() {
        return failureReasonCode;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    /** Never includes the note. */
    @Override
    public String toString() {
        return "ParcelStatusHistoryEntry[parcelId=" + parcelId + ", sequence=" + sequence + ", fromStatus="
                + fromStatus + ", toStatus=" + toStatus + ", eventId=" + eventId + "]";
    }
}
