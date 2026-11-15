package com.swiftdrop.delivery.api;

import java.util.Arrays;
import java.util.UUID;

import com.swiftdrop.pricing.api.ParcelSize;

/**
 * A walk-in customer parcel booked at a hub counter by a dispatcher or administrator.
 *
 * @param hubId the origin hub receiving the parcel immediately
 * @param weightGrams parcel weight, 1 to 30 000 grams (D-24)
 * @param description optional short description of the contents
 * @param staff the staff member receiving the parcel
 */
public record NewWalkInParcel(
        UUID hubId,
        DeliveryContact pickup,
        DeliveryContact dropoff,
        ParcelSize size,
        int weightGrams,
        String description,
        Staff staff
) {

    /**
     * For callers that do not depend on {@code pricing} (ADR 0003, for example {@code dispatch}): the
     * size is given by its name, such as {@code "SMALL"}.
     *
     * @throws IllegalArgumentException if {@code sizeName} is not a known parcel size
     */
    public static NewWalkInParcel withSizeName(UUID hubId, DeliveryContact pickup, DeliveryContact dropoff,
            String sizeName, int weightGrams, String description, Staff staff) {
        return new NewWalkInParcel(hubId, pickup, dropoff, sizeOf(sizeName), weightGrams, description, staff);
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
        return "NewWalkInParcel[hubId=" + hubId + ", size=" + size + ", weightGrams=" + weightGrams + ", ***]";
    }
}

