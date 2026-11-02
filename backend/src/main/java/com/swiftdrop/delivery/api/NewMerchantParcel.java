package com.swiftdrop.delivery.api;

import java.util.UUID;

import com.swiftdrop.pricing.api.ParcelSize;

/**
 * A merchant order to turn into a parcel, sent by {@code merchant} after it has authenticated the
 * API key and checked that the merchant is approved.
 *
 * @param merchantOrderReference the merchant's own order ID; unique per merchant (D-25)
 * @param weightGrams parcel weight, 1 to 30 000 grams (D-24)
 * @param description optional short description of the contents
 */
public record NewMerchantParcel(
        UUID merchantId,
        String merchantOrderReference,
        DeliveryContact pickup,
        DeliveryContact dropoff,
        ParcelSize size,
        int weightGrams,
        String description
) {

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "NewMerchantParcel[merchantId=" + merchantId + ", merchantOrderReference=" + merchantOrderReference
                + ", size=" + size + ", weightGrams=" + weightGrams + ", ***]";
    }
}
