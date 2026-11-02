package com.swiftdrop.delivery.api;

import java.util.List;
import java.util.Objects;

/**
 * What anyone holding a tracking number may see (CUS-6, D-16): status, cities and the status
 * timeline. Contains no names, phone numbers, street addresses, postal codes, notes or proof.
 *
 * @param history status timeline, oldest first
 */
public record PublicTrackingView(
        String trackingNumber,
        ParcelStatus status,
        String pickupCity,
        String dropoffCity,
        List<PublicStatusEvent> history
) {

    public PublicTrackingView {
        Objects.requireNonNull(trackingNumber, "trackingNumber must not be null");
        Objects.requireNonNull(status, "status must not be null");
        history = history != null ? List.copyOf(history) : List.of();
    }
}
