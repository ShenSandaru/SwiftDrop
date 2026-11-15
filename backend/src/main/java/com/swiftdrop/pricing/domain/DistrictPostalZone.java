package com.swiftdrop.pricing.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A standard postal delivery zone within an administrative district.
 * Owned by the pricing module (ADR 0003).
 */
@Entity
@Table(name = "district_postal_zones")
public class DistrictPostalZone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "district_code", nullable = false)
    private District district;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected DistrictPostalZone() {
        // for JPA
    }

    public DistrictPostalZone(District district, String code, String name, String postalCode) {
        this.district = Objects.requireNonNull(district, "district must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null").trim().toUpperCase();
        this.name = Objects.requireNonNull(name, "name must not be null").trim();
        this.postalCode = postalCode != null ? postalCode.trim() : null;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public District getDistrict() {
        return district;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

