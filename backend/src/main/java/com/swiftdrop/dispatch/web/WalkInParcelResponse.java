package com.swiftdrop.dispatch.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.delivery.api.ParcelStatus;

/** Response for a walk-in parcel booking created at a hub counter. */
record WalkInParcelResponse(
        UUID id,
        String trackingNumber,
        ParcelStatus status,
        MoneyFee fee,
        UUID assignedCourierId,
        Instant createdAt) {

    record MoneyFee(BigDecimal amount, String currency) {
    }

    static WalkInParcelResponse from(CreatedParcel created) {
        return new WalkInParcelResponse(
                created.parcelId(),
                created.trackingNumber(),
                created.status(),
                new MoneyFee(created.feeAmount(), created.feeCurrency()),
                null,
                created.createdAt());
    }
}

