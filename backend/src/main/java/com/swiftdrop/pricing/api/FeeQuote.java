package com.swiftdrop.pricing.api;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable price quote breakdown for a delivery route (D-07).
 *
 * <p>Financial amounts use {@link BigDecimal} with 2 decimal places and currency code {@code "LKR"}.
 */
public record FeeQuote(
        BigDecimal totalFee,
        String currency,
        BigDecimal baseFee,
        BigDecimal weightFee,
        BigDecimal sizeFee,
        UUID pricingRuleId
) {
    public FeeQuote {
        Objects.requireNonNull(totalFee, "totalFee must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(baseFee, "baseFee must not be null");
        Objects.requireNonNull(weightFee, "weightFee must not be null");
        Objects.requireNonNull(sizeFee, "sizeFee must not be null");
        Objects.requireNonNull(pricingRuleId, "pricingRuleId must not be null");
    }
}
