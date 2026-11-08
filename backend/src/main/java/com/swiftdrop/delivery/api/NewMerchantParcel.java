package com.swiftdrop.delivery.api;

import java.util.Arrays;
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

    /**
     * For callers that may not depend on {@code pricing} (ADR 0003, for example {@code merchant}): the
     * size is given by its name, such as {@code "SMALL"}. A missing size is reported when the parcel is
     * created, like any other missing field.
     *
     * @throws IllegalArgumentException if {@code sizeName} is not a known parcel size
     */
    public static NewMerchantParcel withSizeName(UUID merchantId, String merchantOrderReference,
            DeliveryContact pickup, DeliveryContact dropoff, String sizeName, int weightGrams, String description) {
        return new NewMerchantParcel(merchantId, merchantOrderReference, pickup, dropoff, sizeOf(sizeName),
                weightGrams, description);
    }

    private static ParcelSize sizeOf(String name) {
        if (name == null) {
            return null;
        }
        for (ParcelSize size : ParcelSize.values()) {
            if (size.name().equals(name)) {
                return size;
            }
        }
        throw new IllegalArgumentException("size must be one of " + Arrays.toString(ParcelSize.values()));
    }

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "NewMerchantParcel[merchantId=" + merchantId + ", merchantOrderReference=" + merchantOrderReference
                + ", size=" + size + ", weightGrams=" + weightGrams + ", ***]";
    }
}
