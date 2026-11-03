package com.swiftdrop.pricing.web;

import java.math.BigDecimal;
import java.util.UUID;

import com.swiftdrop.pricing.api.FeeQuote;

/**
 * Fee estimate (contract §4.3). Amounts are strings such as {@code "650.00"} so clients never
 * round them through floating point.
 */
record PriceQuoteResponse(String amount, String currency, UUID pricingRuleId, Breakdown breakdown) {

    record Breakdown(String baseFee, String weightFee, String sizeSurcharge) {
    }

    static PriceQuoteResponse from(FeeQuote quote) {
        return new PriceQuoteResponse(
                text(quote.totalFee()),
                quote.currency(),
                quote.pricingRuleId(),
                new Breakdown(text(quote.baseFee()), text(quote.weightFee()), text(quote.sizeFee())));
    }

    private static String text(BigDecimal amount) {
        return amount.toPlainString();
    }
}
