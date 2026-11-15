package com.swiftdrop.pricing.web;

import java.math.BigDecimal;
import java.time.Instant;

import com.swiftdrop.pricing.domain.PricingConfig;

/**
 * Representation of the global pricing configuration.
 */
public record PricingConfigResponse(
        String id,
        BigDecimal standardBaseFee,
        BigDecimal freeKmAllowance,
        BigDecimal ratePerKm,
        BigDecimal maxCap,
        BigDecimal perKgFee,
        BigDecimal smallSizeSurcharge,
        BigDecimal mediumSizeSurcharge,
        BigDecimal largeSizeSurcharge,
        String currency,
        Instant updatedAt
) {
    public static PricingConfigResponse from(PricingConfig config) {
        return new PricingConfigResponse(
                config.getId(),
                config.getStandardBaseFee(),
                config.getFreeKmAllowance(),
                config.getRatePerKm(),
                config.getMaxCap(),
                config.getPerKgFee(),
                config.getSmallSizeSurcharge(),
                config.getMediumSizeSurcharge(),
                config.getLargeSizeSurcharge(),
                config.getCurrency(),
                config.getUpdatedAt()
        );
    }
}

