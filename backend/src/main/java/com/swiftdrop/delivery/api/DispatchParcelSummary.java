package com.swiftdrop.delivery.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.swiftdrop.pricing.api.ParcelSize;

/** Operational parcel view for dispatch; address contact details remain behind the parcel detail API. */
public record DispatchParcelSummary(
        UUID id,
        String trackingNumber,
        ParcelStatus status,
        ParcelSource source,
        String merchantOrderReference,
        UUID assignedCourierId,
        ParcelSize size,
        int weightGrams,
        String pickupCity,
        String dropoffCity,
        Instant createdAt,
        Instant updatedAt
) {
    public DispatchParcelSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(pickupCity, "pickupCity");
        Objects.requireNonNull(dropoffCity, "dropoffCity");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
