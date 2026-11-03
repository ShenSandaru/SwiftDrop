package com.swiftdrop.pricing.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A delivery zone representing a geographic region or service area.
 * Owned by the pricing module (ADR 0003, contract §5.2).
 */
@Entity
@Table(name = "zones")
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Zone() {
        // for JPA
    }

    public Zone(String code, String name) {
        this(code, name, true);
    }

    public Zone(String code, String name, boolean active) {
        this.code = normalizeCode(code);
        this.name = normalizeName(name);
        this.active = active;
        this.createdAt = Instant.now();
    }

    public static String normalizeCode(String code) {
        String trimmed = Objects.requireNonNull(code, "code must not be null").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    public static String normalizeName(String name) {
        String trimmed = Objects.requireNonNull(name, "name must not be null").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = normalizeCode(code);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = normalizeName(name);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

