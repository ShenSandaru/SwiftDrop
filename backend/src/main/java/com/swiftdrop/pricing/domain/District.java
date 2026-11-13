package com.swiftdrop.pricing.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * An administrative district of Sri Lanka (e.g., Colombo, Kandy, Galle).
 * Owned by the pricing module (ADR 0003).
 */
@Entity
@Table(name = "districts")
public class District {

    @Id
    @Column(length = 10, nullable = false)
    private String code;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String province;

    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;

    @Column(name = "default_branch_name", nullable = false, length = 100)
    private String defaultBranchName;

    @Column(precision = 9, scale = 6)
    private Double latitude;

    @Column(precision = 9, scale = 6)
    private Double longitude;

    @OneToMany(mappedBy = "district", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("name ASC")
    private List<DistrictPostalZone> postalZones = new ArrayList<>();

    protected District() {
        // for JPA
    }

    public District(String code, String name, String province, String postalCode, String defaultBranchName) {
        this(code, name, province, postalCode, defaultBranchName, null, null);
    }

    public District(String code, String name, String province, String postalCode, String defaultBranchName, Double latitude, Double longitude) {
        this.code = Objects.requireNonNull(code, "code must not be null").trim().toUpperCase();
        this.name = Objects.requireNonNull(name, "name must not be null").trim();
        this.province = Objects.requireNonNull(province, "province must not be null").trim();
        this.postalCode = Objects.requireNonNull(postalCode, "postalCode must not be null").trim();
        this.defaultBranchName = Objects.requireNonNull(defaultBranchName, "defaultBranchName must not be null").trim();
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getProvince() {
        return province;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getDefaultBranchName() {
        return defaultBranchName;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public List<DistrictPostalZone> getPostalZones() {
        return Collections.unmodifiableList(postalZones);
    }
}

