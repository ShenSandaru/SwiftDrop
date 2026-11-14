package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * Answer to a merchant order: the parcel as created, or the existing parcel when the reference was
 * already used (D-25). The fee amount is a string such as {@code "650.00"} (contract §3.1).
 */
record MerchantOrderCreatedResponse(UUID id, String merchantOrderReference, String trackingNumber,
        ParcelStatus status, Fee fee, Instant createdAt) {

    record Fee(String amount, String currency) {
    }

    static MerchantOrderCreatedResponse from(String merchantOrderReference, CreatedParcel parcel) {
        return new MerchantOrderCreatedResponse(parcel.parcelId(), merchantOrderReference, parcel.trackingNumber(),
                parcel.status(), new Fee(parcel.feeAmount().toPlainString(), parcel.feeCurrency()),
                parcel.createdAt());
    }
}
