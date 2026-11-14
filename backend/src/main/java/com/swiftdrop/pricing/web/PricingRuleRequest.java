package com.swiftdrop.pricing.web;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating or updating a pricing rule between an origin and destination zone.
 */
public record PricingRuleRequest(
        @NotNull(message = "originZoneId is required")
        UUID originZoneId,

        @NotNull(message = "destinationZoneId is required")
        UUID destinationZoneId,

        @NotNull(message = "baseFee is required")
        @DecimalMin(value = "0.00", message = "baseFee must be non-negative")
        BigDecimal baseFee,

        @NotNull(message = "perKgFee is required")
        @DecimalMin(value = "0.00", message = "perKgFee must be non-negative")
        BigDecimal perKgFee,

        @DecimalMin(value = "0.00", message = "smallSizeSurcharge must be non-negative")
        BigDecimal smallSizeSurcharge,

        @DecimalMin(value = "0.00", message = "mediumSizeSurcharge must be non-negative")
        BigDecimal mediumSizeSurcharge,

        @DecimalMin(value = "0.00", message = "largeSizeSurcharge must be non-negative")
        BigDecimal largeSizeSurcharge,

        @Size(min = 3, max = 3, message = "currency must be a 3-character ISO code")
        String currency,

        Boolean active,
        Long version
) {

    public BigDecimal smallSizeSurchargeOrDefault() {
        return smallSizeSurcharge != null ? smallSizeSurcharge : BigDecimal.ZERO;
    }

    public BigDecimal mediumSizeSurchargeOrDefault() {
        return mediumSizeSurcharge != null ? mediumSizeSurcharge : BigDecimal.ZERO;
    }

    public BigDecimal largeSizeSurchargeOrDefault() {
        return largeSizeSurcharge != null ? largeSizeSurcharge : BigDecimal.ZERO;
    }

    public String currencyOrDefault() {
        return (currency != null && !currency.isBlank()) ? currency.trim().toUpperCase() : "LKR";
    }

    public boolean activeOrDefault() {
        return active != null ? active : true;
    }
}

