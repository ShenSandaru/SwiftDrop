package com.swiftdrop.delivery.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A staff member's home hub (ADR 0012 §1): the dispatcher or courier works for that hub. Without a
 * hub the row marks a head office dispatcher, who works country-wide.
 */
@Entity
@Table(name = "hub_staff")
public class HubStaff {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "hub_id", updatable = false)
    private UUID hubId;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HubStaff() {
        // for JPA
    }

    private HubStaff(UUID userId, UUID hubId, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.hubId = hubId;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    /** The user works for this hub. */
    public static HubStaff atHub(UUID userId, UUID hubId, UUID createdBy, Instant now) {
        return new HubStaff(userId, Objects.requireNonNull(hubId, "hubId"), createdBy, now);
    }

    /** The user works country-wide. */
    public static HubStaff headOffice(UUID userId, UUID createdBy, Instant now) {
        return new HubStaff(userId, null, createdBy, now);
    }

    public boolean isHeadOffice() {
        return hubId == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    /** The hub, or {@code null} for head office. */
    public UUID getHubId() {
        return hubId;
    }

    /** The admin who created the row, or {@code null} for rows created by the migration. */
    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "HubStaff[user=" + userId + ", hub=" + (hubId == null ? "head office" : hubId) + "]";
    }
}
