package com.swiftdrop.delivery.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.application.ParcelView;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryEntry;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * Delivery receipt (CUS-5, ADR 0006 §6): a booking confirmation until the parcel is delivered, then
 * with the delivery time and recipient; for failed parcels the failure time and reason. The fee is
 * the total fixed at booking.
 *
 * @param deliveredAt set only for {@code DELIVERED}
 * @param failedAt set only for {@code FAILED}
 */
record ReceiptResponse(UUID parcelId, String trackingNumber, ParcelStatus status, Instant createdAt,
        AddressResponse pickup, AddressResponse dropoff, ParcelSize size, int weightGrams, String description,
        MoneyResponse fee, Instant deliveredAt, String recipientName, Instant failedAt, FailureReason failureReason) {

    static ReceiptResponse from(ParcelView view) {
        Parcel parcel = view.parcel();
        Instant deliveredAt = null;
        String recipientName = null;
        Instant failedAt = null;
        FailureReason failureReason = null;
        // The final status is always the last history entry
        if (!view.history().isEmpty()) {
            ParcelStatusHistoryEntry last = view.history().getLast();
            if (parcel.getStatus() == ParcelStatus.DELIVERED) {
                deliveredAt = last.getOccurredAt();
                recipientName = view.recipientName();
            } else if (parcel.getStatus() == ParcelStatus.FAILED) {
                failedAt = last.getOccurredAt();
                failureReason = last.getFailureReasonCode();
            }
        }
        return new ReceiptResponse(parcel.getId(), parcel.getTrackingNumber(), parcel.getStatus(),
                parcel.getCreatedAt(), AddressResponse.from(parcel.getPickup()),
                AddressResponse.from(parcel.getDropoff()), parcel.getSize(), parcel.getWeightGrams(),
                parcel.getDescription(), MoneyResponse.feeOf(parcel), deliveredAt, recipientName, failedAt,
                failureReason);
    }

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "ReceiptResponse[parcelId=" + parcelId + ", trackingNumber=" + trackingNumber + ", status=" + status + "]";
    }
}
