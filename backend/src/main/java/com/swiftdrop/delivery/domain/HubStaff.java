package com.swiftdrop.delivery.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A staff member's home hub (ADR 0012 §1): the dispatcher or courier works for that hub. A person
 * may have several rows.
 */
@Entity
@Table(name = "hub_staff")
public class HubStaff {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "hub_id", nullable = false, updatable = false)
    private UUID hubId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HubStaff() {
        // for JPA
    }

    private HubStaff(UUID userId, UUID hubId, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.hubId = Objects.requireNonNull(hubId, "hubId");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    /** The user works for this hub. */
    public static HubStaff atHub(UUID userId, UUID hubId, UUID createdBy, Instant now) {
        return new HubStaff(userId, hubId, createdBy, now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    /** The hub the user works for. */
    public UUID getHubId() {
        return hubId;
    }

    /** The admin who created the row. */
    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "HubStaff[user=" + userId + ", hub=" + hubId + "]";
    }
}
