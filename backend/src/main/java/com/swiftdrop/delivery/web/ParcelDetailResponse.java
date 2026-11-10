package com.swiftdrop.delivery.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.application.ParcelView;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryEntry;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * Parcel detail with its status timeline (CUS-4). Notes are {@code null} for viewers who may not see
 * them.
 *
 * @param deliveryAttempts delivery attempts started so far (ADR 0009 §3)
 */
record ParcelDetailResponse(UUID id, String trackingNumber, ParcelStatus status, ParcelSource source,
        String merchantOrderReference, AddressResponse pickup, AddressResponse dropoff, ParcelSize size,
        int weightGrams, String description, MoneyResponse fee, UUID assignedCourierId, Instant createdAt,
        Instant updatedAt, int deliveryAttempts, List<StatusEntry> history) {

    /**
     * @param hubName the hub reached or left in this step, or {@code null}
     * @param attempt the delivery attempt of this step, or {@code null}
     */
    record StatusEntry(int sequence, ParcelStatus status, Instant occurredAt, FailureReason failureReason,
            String note, String hubName, Integer attempt) {

        static StatusEntry from(ParcelStatusHistoryEntry entry, boolean withNote, ParcelView view) {
            return new StatusEntry(entry.getSequence(), entry.getToStatus(), entry.getOccurredAt(),
                    entry.getFailureReasonCode(), withNote ? entry.getNote() : null, view.hubName(entry.getHubCode()),
                    entry.getAttempt());
        }
    }

    static ParcelDetailResponse from(ParcelView view) {
        Parcel parcel = view.parcel();
        return new ParcelDetailResponse(parcel.getId(), parcel.getTrackingNumber(), parcel.getStatus(),
                parcel.getSource(), parcel.getMerchantOrderReference(), AddressResponse.from(parcel.getPickup()),
                AddressResponse.from(parcel.getDropoff()), parcel.getSize(), parcel.getWeightGrams(),
                parcel.getDescription(), MoneyResponse.feeOf(parcel), parcel.getAssignedCourierId(),
                parcel.getCreatedAt(), parcel.getUpdatedAt(), parcel.getDeliveryAttempts(),
                view.history().stream().map(entry -> StatusEntry.from(entry, view.notesVisible(), view)).toList());
    }

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "ParcelDetailResponse[id=" + id + ", trackingNumber=" + trackingNumber + ", status=" + status + "]";
    }
}
