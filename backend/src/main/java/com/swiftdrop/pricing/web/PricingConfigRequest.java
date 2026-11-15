package com.swiftdrop.pricing.web;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.swiftdrop.pricing.domain.PricingConfig;

/**
 * Payload for updating global pricing parameters.
 */
public record PricingConfigRequest(
        @NotNull @DecimalMin("0.00") BigDecimal standardBaseFee,
        @NotNull @DecimalMin("0.00") BigDecimal freeKmAllowance,
        @NotNull @DecimalMin("0.00") BigDecimal ratePerKm,
        @NotNull @DecimalMin("0.00") BigDecimal maxCap,
        @NotNull @DecimalMin("0.00") BigDecimal perKgFee,
        @NotNull @DecimalMin("0.00") BigDecimal smallSizeSurcharge,
        @NotNull @DecimalMin("0.00") BigDecimal mediumSizeSurcharge,
        @NotNull @DecimalMin("0.00") BigDecimal largeSizeSurcharge,
        @Pattern(regexp = "^[A-Z]{3}$") String currency
) {
}

