package com.swiftdrop.pricing.domain;

import java.math.BigDecimal;
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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A pricing rule defining delivery fees between an origin zone and a destination zone.
 * Owned by the pricing module (ADR 0003, contract §5.2).
 */
@Entity
@Table(name = "pricing_rules")
public class PricingRule {

    public static final String DEFAULT_CURRENCY = "LKR";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "origin_zone_id", nullable = false)
    private UUID originZoneId;

    @Column(name = "destination_zone_id", nullable = false)
    private UUID destinationZoneId;

    @Column(name = "base_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "per_kg_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal perKgFee;

    @Column(name = "small_size_surcharge", nullable = false, precision = 12, scale = 2)
    private BigDecimal smallSizeSurcharge;

    @Column(name = "medium_size_surcharge", nullable = false, precision = 12, scale = 2)
    private BigDecimal mediumSizeSurcharge;

    @Column(name = "large_size_surcharge", nullable = false, precision = 12, scale = 2)
    private BigDecimal largeSizeSurcharge;

    // The migration declares CHAR(3); without this Hibernate's validate expects VARCHAR(3)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean active;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PricingRule() {
        // for JPA
    }

    public PricingRule(
            UUID originZoneId,
            UUID destinationZoneId,
            BigDecimal baseFee,
            BigDecimal perKgFee
    ) {
        this(originZoneId, destinationZoneId, baseFee, perKgFee, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, DEFAULT_CURRENCY, true);
    }

    public PricingRule(
            UUID originZoneId,
            UUID destinationZoneId,
            BigDecimal baseFee,
            BigDecimal perKgFee,
            BigDecimal smallSizeSurcharge,
            BigDecimal mediumSizeSurcharge,
            BigDecimal largeSizeSurcharge,
            String currency
    ) {
        this(originZoneId, destinationZoneId, baseFee, perKgFee, smallSizeSurcharge, mediumSizeSurcharge, largeSizeSurcharge, currency, true);
    }

    public PricingRule(
            UUID originZoneId,
            UUID destinationZoneId,
            BigDecimal baseFee,
            BigDecimal perKgFee,
            BigDecimal smallSizeSurcharge,
            BigDecimal mediumSizeSurcharge,
            BigDecimal largeSizeSurcharge,
            String currency,
            boolean active
    ) {
        this.originZoneId = Objects.requireNonNull(originZoneId, "originZoneId must not be null");
        this.destinationZoneId = Objects.requireNonNull(destinationZoneId, "destinationZoneId must not be null");
        this.baseFee = requireNonNegative(baseFee, "baseFee");
        this.perKgFee = requireNonNegative(perKgFee, "perKgFee");
        this.smallSizeSurcharge = requireNonNegative(smallSizeSurcharge != null ? smallSizeSurcharge : BigDecimal.ZERO, "smallSizeSurcharge");
        this.mediumSizeSurcharge = requireNonNegative(mediumSizeSurcharge != null ? mediumSizeSurcharge : BigDecimal.ZERO, "mediumSizeSurcharge");
        this.largeSizeSurcharge = requireNonNegative(largeSizeSurcharge != null ? largeSizeSurcharge : BigDecimal.ZERO, "largeSizeSurcharge");
        this.currency = normalizeCurrency(currency);
        this.active = active;
        this.createdAt = Instant.now();
    }

    public static BigDecimal requireNonNegative(BigDecimal amount, String name) {
        Objects.requireNonNull(amount, name + " must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return amount;
    }

    public static String normalizeCurrency(String currency) {
        String trimmed = Objects.requireNonNull(currency, "currency must not be null").trim();
        if (trimmed.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-character ISO code");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOriginZoneId() {
        return originZoneId;
    }

    public void setOriginZoneId(UUID originZoneId) {
        this.originZoneId = Objects.requireNonNull(originZoneId, "originZoneId must not be null");
    }

    public UUID getDestinationZoneId() {
        return destinationZoneId;
    }

    public void setDestinationZoneId(UUID destinationZoneId) {
        this.destinationZoneId = Objects.requireNonNull(destinationZoneId, "destinationZoneId must not be null");
    }

    public BigDecimal getBaseFee() {
        return baseFee;
    }

    public void setBaseFee(BigDecimal baseFee) {
        this.baseFee = requireNonNegative(baseFee, "baseFee");
    }

    public BigDecimal getPerKgFee() {
        return perKgFee;
    }

    public void setPerKgFee(BigDecimal perKgFee) {
        this.perKgFee = requireNonNegative(perKgFee, "perKgFee");
    }

    public BigDecimal getSmallSizeSurcharge() {
        return smallSizeSurcharge;
    }

    public void setSmallSizeSurcharge(BigDecimal smallSizeSurcharge) {
        this.smallSizeSurcharge = requireNonNegative(smallSizeSurcharge, "smallSizeSurcharge");
    }

    public BigDecimal getMediumSizeSurcharge() {
        return mediumSizeSurcharge;
    }

    public void setMediumSizeSurcharge(BigDecimal mediumSizeSurcharge) {
        this.mediumSizeSurcharge = requireNonNegative(mediumSizeSurcharge, "mediumSizeSurcharge");
    }

    public BigDecimal getLargeSizeSurcharge() {
        return largeSizeSurcharge;
    }

    public void setLargeSizeSurcharge(BigDecimal largeSizeSurcharge) {
        this.largeSizeSurcharge = requireNonNegative(largeSizeSurcharge, "largeSizeSurcharge");
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = normalizeCurrency(currency);
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

