package com.swiftdrop.delivery.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * What anyone holding a tracking number may see (CUS-6, D-16): status, cities, the status timeline
 * and an estimated delivery date. Contains no names, phone numbers, street addresses, postal codes,
 * notes or proof.
 *
 * @param history           status timeline, oldest first
 * @param estimatedDelivery the day the parcel is expected, in the business time zone; {@code null}
 *                          when the parcel is finished or returning, or its route is not known
 */
public record PublicTrackingView(
        String trackingNumber,
        ParcelStatus status,
        String pickupCity,
        String dropoffCity,
        List<PublicStatusEvent> history,
        LocalDate estimatedDelivery
) {

    public PublicTrackingView {
        Objects.requireNonNull(trackingNumber, "trackingNumber must not be null");
        Objects.requireNonNull(status, "status must not be null");
        history = history != null ? List.copyOf(history) : List.of();
    }

    public PublicTrackingView(String trackingNumber, ParcelStatus status, String pickupCity, String dropoffCity,
            List<PublicStatusEvent> history) {
        this(trackingNumber, status, pickupCity, dropoffCity, history, null);
    }
}
