package com.swiftdrop.pricing.web;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.pricing.api.FeeCalculator;

/**
 * Fee estimate before booking (CUS-3). Any authenticated user may ask; anonymous access, as the
 * contract proposes, needs a public rule in {@code SecurityConfiguration}.
 */
@RestController
class PriceQuoteController {

    private final FeeCalculator feeCalculator;

    PriceQuoteController(FeeCalculator feeCalculator) {
        this.feeCalculator = feeCalculator;
    }

    @PostMapping("/api/v1/price-quotes")
    PriceQuoteResponse quote(@Valid @RequestBody PriceQuoteRequest request) {
        return PriceQuoteResponse.from(feeCalculator.calculateFee(
                request.pickupZoneId(), request.dropoffZoneId(), request.size(), request.weightGrams()));
    }
}
