package com.swiftdrop.delivery.application;

import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * What a customer or merchant asks to send (CUS-2). Checked by the domain when the parcel is built,
 * so customer and merchant bookings follow the same rules.
 *
 * @param description optional, at most 200 characters
 */
public record Booking(DeliveryContact pickup, DeliveryContact dropoff, ParcelSize size, int weightGrams,
        String description) {

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "Booking[size=" + size + ", weightGrams=" + weightGrams + ", ***]";
    }
}
