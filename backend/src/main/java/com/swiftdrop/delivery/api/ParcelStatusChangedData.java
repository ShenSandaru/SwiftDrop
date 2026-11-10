package com.swiftdrop.delivery.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Data of a {@code parcel.status-changed} event (ADR 0004 §2). Carries IDs and codes only, never
 * personal data or free-text notes; consumers look up what they are entitled to see.
 *
 * @param previousStatus {@code null} for the initial {@link ParcelStatus#CREATED} event
 * @param sequence per-parcel sequence number; {@code CREATED} is 1
 * @param ownerCustomerId set for {@link ParcelSource#CUSTOMER} parcels, otherwise {@code null}
 * @param ownerMerchantId set for {@link ParcelSource#MERCHANT} parcels, otherwise {@code null}
 * @param failureReasonCode set only when {@code newStatus} needs a reason ({@code FAILED},
 *                          {@code DELIVERY_ATTEMPT_FAILED})
 * @param hubCode optional: the hub reached ({@code AT_HUB}) or left ({@code IN_TRANSIT}); ADR 0009 §7
 * @param attempt optional: the delivery attempt ({@code OUT_FOR_DELIVERY}, {@code DELIVERY_ATTEMPT_FAILED})
 */
public record ParcelStatusChangedData(
        UUID parcelId,
        String trackingNumber,
        ParcelStatus previousStatus,
        ParcelStatus newStatus,
        int sequence,
        ParcelSource source,
        UUID ownerCustomerId,
        UUID ownerMerchantId,
        String merchantOrderReference,
        FailureReason failureReasonCode,
        String hubCode,
        Integer attempt
) {

    public ParcelStatusChangedData {
        Objects.requireNonNull(parcelId, "parcelId must not be null");
        Objects.requireNonNull(trackingNumber, "trackingNumber must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be >= 1");
        }
        if (newStatus.needsFailureReason() != (failureReasonCode != null)) {
            throw new IllegalArgumentException(
                    "failureReasonCode is required for FAILED and DELIVERY_ATTEMPT_FAILED, and only for them");
        }
        if (attempt != null && attempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1");
        }
    }

    /** Without hub and attempt, as before ADR 0009. */
    public ParcelStatusChangedData(UUID parcelId, String trackingNumber, ParcelStatus previousStatus,
            ParcelStatus newStatus, int sequence, ParcelSource source, UUID ownerCustomerId, UUID ownerMerchantId,
            String merchantOrderReference, FailureReason failureReasonCode) {
        this(parcelId, trackingNumber, previousStatus, newStatus, sequence, source, ownerCustomerId, ownerMerchantId,
                merchantOrderReference, failureReasonCode, null, null);
    }
}
