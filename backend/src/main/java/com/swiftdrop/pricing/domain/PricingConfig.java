package com.swiftdrop.pricing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Global pricing tariff configuration for automated distance, weight, and size calculations.
 * Owned by the pricing module (ADR 0003).
 */
@Entity
@Table(name = "pricing_config")
public class PricingConfig {

    public static final String DEFAULT_ID = "DEFAULT";
    public static final String DEFAULT_CURRENCY = "LKR";

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "standard_base_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal standardBaseFee;

    @Column(name = "free_km_allowance", nullable = false, precision = 6, scale = 2)
    private BigDecimal freeKmAllowance;

    @Column(name = "rate_per_km", nullable = false, precision = 12, scale = 2)
    private BigDecimal ratePerKm;

    @Column(name = "max_cap", nullable = false, precision = 12, scale = 2)
    private BigDecimal maxCap;

    @Column(name = "per_kg_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal perKgFee;

    @Column(name = "small_size_surcharge", nullable = false, precision = 12, scale = 2)
    private BigDecimal smallSizeSurcharge;

    @Column(name = "medium_size_surcharge", nullable = false, precision = 12, scale = 2)
    private BigDecimal mediumSizeSurcharge;

    @Column(name = "large_size_surcharge", nullable = false, precision = 12, scale = 2)
    private BigDecimal largeSizeSurcharge;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PricingConfig() {
        // for JPA
    }

    public PricingConfig(
            String id,
            BigDecimal standardBaseFee,
            BigDecimal freeKmAllowance,
            BigDecimal ratePerKm,
            BigDecimal maxCap,
            BigDecimal perKgFee,
            BigDecimal smallSizeSurcharge,
            BigDecimal mediumSizeSurcharge,
            BigDecimal largeSizeSurcharge,
            String currency
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.standardBaseFee = validateNonNegative(standardBaseFee, "standardBaseFee");
        this.freeKmAllowance = validateNonNegative(freeKmAllowance, "freeKmAllowance");
        this.ratePerKm = validateNonNegative(ratePerKm, "ratePerKm");
        this.maxCap = validateNonNegative(maxCap, "maxCap");
        this.perKgFee = validateNonNegative(perKgFee, "perKgFee");
        this.smallSizeSurcharge = validateNonNegative(smallSizeSurcharge, "smallSizeSurcharge");
        this.mediumSizeSurcharge = validateNonNegative(mediumSizeSurcharge, "mediumSizeSurcharge");
        this.largeSizeSurcharge = validateNonNegative(largeSizeSurcharge, "largeSizeSurcharge");
        this.currency = (currency != null && !currency.isBlank()) ? currency.trim().toUpperCase() : DEFAULT_CURRENCY;
        this.updatedAt = Instant.now();
    }

    public static PricingConfig defaultConfig() {
        return new PricingConfig(
                DEFAULT_ID,
                new BigDecimal("350.00"),
                new BigDecimal("30.00"),
                new BigDecimal("1.33"),
                new BigDecimal("1000.00"),
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                new BigDecimal("50.00"),
                new BigDecimal("150.00"),
                DEFAULT_CURRENCY
        );
    }

    public void update(
            BigDecimal standardBaseFee,
            BigDecimal freeKmAllowance,
            BigDecimal ratePerKm,
            BigDecimal maxCap,
            BigDecimal perKgFee,
            BigDecimal smallSizeSurcharge,
            BigDecimal mediumSizeSurcharge,
            BigDecimal largeSizeSurcharge,
            String currency
    ) {
        this.standardBaseFee = validateNonNegative(standardBaseFee, "standardBaseFee");
        this.freeKmAllowance = validateNonNegative(freeKmAllowance, "freeKmAllowance");
        this.ratePerKm = validateNonNegative(ratePerKm, "ratePerKm");
        this.maxCap = validateNonNegative(maxCap, "maxCap");
        this.perKgFee = validateNonNegative(perKgFee, "perKgFee");
        this.smallSizeSurcharge = validateNonNegative(smallSizeSurcharge, "smallSizeSurcharge");
        this.mediumSizeSurcharge = validateNonNegative(mediumSizeSurcharge, "mediumSizeSurcharge");
        this.largeSizeSurcharge = validateNonNegative(largeSizeSurcharge, "largeSizeSurcharge");
        if (currency != null && !currency.isBlank()) {
            this.currency = currency.trim().toUpperCase();
        }
        this.updatedAt = Instant.now();
    }

    private static BigDecimal validateNonNegative(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }

    public String getId() {
        return id;
    }

    public BigDecimal getStandardBaseFee() {
        return standardBaseFee;
    }

    public BigDecimal getFreeKmAllowance() {
        return freeKmAllowance;
    }

    public BigDecimal getRatePerKm() {
        return ratePerKm;
    }

    public BigDecimal getMaxCap() {
        return maxCap;
    }

    public BigDecimal getPerKgFee() {
        return perKgFee;
    }

    public BigDecimal getSmallSizeSurcharge() {
        return smallSizeSurcharge;
    }

    public BigDecimal getMediumSizeSurcharge() {
        return mediumSizeSurcharge;
    }

    public BigDecimal getLargeSizeSurcharge() {
        return largeSizeSurcharge;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

