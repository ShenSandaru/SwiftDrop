package com.swiftdrop.delivery.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One unpublished or published row of the transactional outbox (ADR 0004 §3). Written in the same
 * transaction as its status change; the relay publishes it and records the outcome.
 */
@Entity
@Table(name = "parcel_outbox_events")
public class OutboxEvent {

    /** Length of {@code last_failure_category}. */
    static final int MAX_FAILURE_CATEGORY_LENGTH = 40;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    @Column(nullable = false, updatable = false)
    private int sequence;

    @Column(name = "event_type", nullable = false, length = 100, updatable = false)
    private String eventType;

    /** The serialized event envelope; contains no personal data. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_failure_category", length = 40)
    private String lastFailureCategory;

    protected OutboxEvent() {
        // for JPA
    }

    /** A new unpublished row, due for publishing immediately. */
    public OutboxEvent(UUID eventId, UUID parcelId, int sequence, String eventType, String payload, Instant createdAt) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.parcelId = Objects.requireNonNull(parcelId, "parcelId");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be >= 1");
        }
        this.sequence = sequence;
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.nextAttemptAt = createdAt;
    }

    public Long getId() {
        return id;
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

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastFailureCategory() {
        return lastFailureCategory;
    }

    /** Whether the relay may try to publish this row now (its backoff has passed). */
    public boolean isDue(Instant now) {
        return !nextAttemptAt.isAfter(now);
    }

    /** Records that the broker confirmed the row (ADR 0004 §3); the last failure stays for reference. */
    public void markPublished(Instant at) {
        if (isPublished()) {
            throw new IllegalStateException("Outbox row " + id + " is already published");
        }
        this.publishedAt = Objects.requireNonNull(at, "at");
    }

    /**
     * Records a failed publish attempt and when to try again (ADR 0004 §4.2). The row is never
     * dropped; it stays unpublished until a later attempt succeeds.
     *
     * @param category short failure category, for example {@code returned}; never message content
     */
    public void recordFailure(String category, Instant nextAttemptAt) {
        if (isPublished()) {
            throw new IllegalStateException("Outbox row " + id + " is already published");
        }
        if (category == null || category.isBlank() || category.length() > MAX_FAILURE_CATEGORY_LENGTH) {
            throw new IllegalArgumentException("category must be 1 to " + MAX_FAILURE_CATEGORY_LENGTH + " characters");
        }
        this.attempts++;
        this.lastFailureCategory = category;
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
    }

    @Override
    public String toString() {
        return "OutboxEvent[id=" + id + ", eventId=" + eventId + ", parcelId=" + parcelId + ", sequence=" + sequence
                + ", published=" + isPublished() + ", attempts=" + attempts + "]";
    }
}
