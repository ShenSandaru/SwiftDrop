package com.swiftdrop.delivery.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A hub of the delivery network (ADR 0009 §1): the branch of one pricing zone, the central hub, or
 * both. The code is fixed at creation because parcel history and events record it.
 */
@Entity
@Table(name = "hubs")
public class Hub {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_CITY_LENGTH = 100;
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9-]{2,20}$");

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 20, updatable = false)
    private String code;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(name = "zone_id", unique = true)
    private UUID zoneId;

    @Column(nullable = false)
    private boolean central;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Hub() {
        // for JPA
    }

    /**
     * @param zoneId the zone this hub serves as its branch; {@code null} only for the central hub
     * @throws IllegalArgumentException if a value is invalid
     */
    public Hub(String code, String name, String city, UUID zoneId, boolean central, Instant now) {
        this.id = UUID.randomUUID();
        this.code = normalizeCode(code);
        this.createdAt = Objects.requireNonNull(now, "now");
        this.active = true;
        update(name, city, zoneId, central, now);
    }

    /** Changes everything but the code. */
    public void update(String name, String city, UUID zoneId, boolean central, Instant now) {
        this.name = required(name, "name", MAX_NAME_LENGTH);
        this.city = required(city, "city", MAX_CITY_LENGTH);
        if (!central && zoneId == null) {
            throw new IllegalArgumentException("A hub must serve a zone unless it is the central hub");
        }
        this.zoneId = zoneId;
        this.central = central;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    /** A deactivated hub accepts no new bookings for its zone; parcels already routed through it continue. */
    public void setActive(boolean active, Instant now) {
        this.active = active;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    /** Upper case, letters, digits and hyphens, 2 to 20 characters. */
    public static String normalizeCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("code is required");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("code must be 2 to 20 letters, digits or hyphens");
        }
        return normalized;
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public boolean isCentral() {
        return central;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Hub[code=" + code + ", central=" + central + ", active=" + active + "]";
    }
}
