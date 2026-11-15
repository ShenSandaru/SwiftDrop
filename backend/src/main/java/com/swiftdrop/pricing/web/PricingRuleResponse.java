package com.swiftdrop.pricing.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.pricing.domain.PricingRule;
import com.swiftdrop.pricing.domain.Zone;

/**
 * Representation of a pricing rule with enriched origin and destination zone names and codes.
 */
public record PricingRuleResponse(
        UUID id,
        UUID originZoneId,
        String originZoneCode,
        String originZoneName,
        UUID destinationZoneId,
        String destinationZoneCode,
        String destinationZoneName,
        BigDecimal baseFee,
        BigDecimal perKgFee,
        BigDecimal smallSizeSurcharge,
        BigDecimal mediumSizeSurcharge,
        BigDecimal largeSizeSurcharge,
        String currency,
        boolean active,
        long version,
        Instant createdAt
) {

    public static PricingRuleResponse from(PricingRule rule, Zone origin, Zone destination) {
        return new PricingRuleResponse(
                rule.getId(),
                rule.getOriginZoneId(),
                origin != null ? origin.getCode() : null,
                origin != null ? origin.getName() : null,
                rule.getDestinationZoneId(),
                destination != null ? destination.getCode() : null,
                destination != null ? destination.getName() : null,
                rule.getBaseFee(),
                rule.getPerKgFee(),
                rule.getSmallSizeSurcharge(),
                rule.getMediumSizeSurcharge(),
                rule.getLargeSizeSurcharge(),
                rule.getCurrency(),
                rule.isActive(),
                rule.getVersion(),
                rule.getCreatedAt()
        );
    }
}

