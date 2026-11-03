package com.swiftdrop.delivery.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.pricing.api.ParcelSize;

/** One row of the delivery history (CUS-5): cities only, no contacts or street addresses. */
record ParcelListItemResponse(UUID id, String trackingNumber, ParcelStatus status, ParcelSource source,
        ParcelSize size, int weightGrams, String pickupCity, String dropoffCity, MoneyResponse fee,
        Instant createdAt, Instant updatedAt) {

    static ParcelListItemResponse from(Parcel parcel) {
        return new ParcelListItemResponse(parcel.getId(), parcel.getTrackingNumber(), parcel.getStatus(),
                parcel.getSource(), parcel.getSize(), parcel.getWeightGrams(), parcel.getPickup().getCity(),
                parcel.getDropoff().getCity(), MoneyResponse.feeOf(parcel), parcel.getCreatedAt(),
                parcel.getUpdatedAt());
    }
}
