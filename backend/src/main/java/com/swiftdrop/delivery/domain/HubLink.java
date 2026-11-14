package com.swiftdrop.delivery.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A direct link between two branch hubs (ADR 0011 §1): parcels between them skip the central hub.
 * A link has no direction, so the pair is stored once with the smaller hub ID first.
 */
@Entity
@Table(name = "hub_links")
public class HubLink {

    @Id
    private UUID id;

    @Column(name = "first_hub_id", nullable = false, updatable = false)
    private UUID firstHubId;

    @Column(name = "second_hub_id", nullable = false, updatable = false)
    private UUID secondHubId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HubLink() {
        // for JPA
    }

    /**
     * @param hubId      one hub of the pair, in either order
     * @param otherHubId the other hub
     * @param createdBy  the admin creating the link
     * @throws IllegalArgumentException if both IDs name the same hub
     */
    public HubLink(UUID hubId, UUID otherHubId, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.firstHubId = first(hubId, otherHubId);
        this.secondHubId = second(hubId, otherHubId);
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    /** The hub ID stored first for this pair. */
    public static UUID first(UUID hubId, UUID otherHubId) {
        return isBefore(hubId, otherHubId) ? hubId : otherHubId;
    }

    /** The hub ID stored second for this pair. */
    public static UUID second(UUID hubId, UUID otherHubId) {
        return isBefore(hubId, otherHubId) ? otherHubId : hubId;
    }

    // Compared as text, which is how PostgreSQL orders UUIDs (byte by byte). UUID.compareTo compares
    // signed longs and would disagree with the check constraint for half of all pairs.
    private static boolean isBefore(UUID hubId, UUID otherHubId) {
        Objects.requireNonNull(hubId, "hubId");
        Objects.requireNonNull(otherHubId, "otherHubId");
        int order = hubId.toString().compareTo(otherHubId.toString());
        if (order == 0) {
            throw new IllegalArgumentException("A hub cannot be linked to itself");
        }
        return order < 0;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFirstHubId() {
        return firstHubId;
    }

    public UUID getSecondHubId() {
        return secondHubId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "HubLink[" + firstHubId + " - " + secondHubId + "]";
    }
}
