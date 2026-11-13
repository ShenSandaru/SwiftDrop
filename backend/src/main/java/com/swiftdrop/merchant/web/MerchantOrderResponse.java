package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;

/** A merchant's order as returned by {@code GET /api/v1/merchant-orders/{merchantOrderReference}}. */
record MerchantOrderResponse(UUID id, String merchantOrderReference, String trackingNumber, ParcelStatus status,
        String size, int weightGrams, Instant createdAt) {

    static MerchantOrderResponse from(ParcelSummary parcel) {
        return new MerchantOrderResponse(parcel.id(), parcel.merchantOrderReference(), parcel.trackingNumber(),
                parcel.status(), parcel.size().name(), parcel.weightGrams(), parcel.createdAt());
    }
}
