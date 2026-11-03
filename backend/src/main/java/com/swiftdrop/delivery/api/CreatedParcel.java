package com.swiftdrop.delivery.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Result of creating a parcel.
 *
 * @param feeAmount the fee fixed at creation, 2 decimals
 * @param newlyCreated {@code false} when a merchant repeated an order reference and the existing
 *        parcel was returned instead (D-25); lets {@code merchant} answer 200 instead of 201
 */
public record CreatedParcel(
        UUID parcelId,
        String trackingNumber,
        ParcelStatus status,
        BigDecimal feeAmount,
        String feeCurrency,
        Instant createdAt,
        boolean newlyCreated
) {

    public CreatedParcel {
        Objects.requireNonNull(parcelId, "parcelId must not be null");
        Objects.requireNonNull(trackingNumber, "trackingNumber must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(feeAmount, "feeAmount must not be null");
        Objects.requireNonNull(feeCurrency, "feeCurrency must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
