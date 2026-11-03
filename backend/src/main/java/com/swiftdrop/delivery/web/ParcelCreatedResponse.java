package com.swiftdrop.delivery.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.Parcel;

/** Answer to a booking (contract §4.3). */
record ParcelCreatedResponse(UUID id, String trackingNumber, ParcelStatus status, MoneyResponse fee,
        UUID assignedCourierId, Instant createdAt) {

    static ParcelCreatedResponse from(Parcel parcel) {
        return new ParcelCreatedResponse(parcel.getId(), parcel.getTrackingNumber(), parcel.getStatus(),
                MoneyResponse.feeOf(parcel), parcel.getAssignedCourierId(), parcel.getCreatedAt());
    }
}
