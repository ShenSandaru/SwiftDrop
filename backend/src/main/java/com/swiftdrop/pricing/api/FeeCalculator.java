package com.swiftdrop.pricing.api;

import java.util.UUID;

/**
 * Public facade of the pricing module for fee calculation (D-07, DES-6 Strategy).
 *
 * <p>Calculates quoted delivery fees based on route zone pairs, chargeable weight,
 * and parcel size category. Does not expose pricing entities or database persistence models.
 */
public interface FeeCalculator {

    /**
     * Calculates the delivery fee for a specific route, size category, and weight.
     *
     * @param originZoneId pickup zone identifier
     * @param destinationZoneId dropoff zone identifier
     * @param size parcel size category tier
     * @param weightGrams parcel weight in grams (1 - 30,000)
     * @return calculated immutable fee breakdown
     */
    FeeQuote calculateFee(UUID originZoneId, UUID destinationZoneId, ParcelSize size, int weightGrams);
}
