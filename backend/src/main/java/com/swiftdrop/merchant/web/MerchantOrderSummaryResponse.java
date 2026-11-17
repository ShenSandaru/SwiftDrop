package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;

/** One row of the merchant portal's order list and dashboard activity. Never exposes the parcel entity. */
public record MerchantOrderSummaryResponse(UUID id, String trackingNumber, String merchantOrderReference,
        ParcelStatus status, String size, int weightGrams, boolean courierAssigned, Instant createdAt) {

    static MerchantOrderSummaryResponse from(ParcelSummary parcel) {
        return new MerchantOrderSummaryResponse(parcel.id(), parcel.trackingNumber(), parcel.merchantOrderReference(),
                parcel.status(), parcel.size().name(), parcel.weightGrams(), parcel.assignedCourierId() != null,
                parcel.createdAt());
    }
}
