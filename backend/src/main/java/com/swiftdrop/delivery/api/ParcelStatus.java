package com.swiftdrop.delivery.api;

/**
 * Status lifecycle vocabulary of a parcel delivery (D-06, ADR 0003, ADR 0009 §2), in lifecycle
 * order. Vocabulary only; state transition enforcement lives in the delivery domain.
 */
public enum ParcelStatus {
    CREATED,
    PICKED_UP,
    /** Scanned in at a hub of the parcel's route; the parcel records which hub. */
    AT_HUB,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    /** An attempt failed; the parcel goes back to the destination hub and may be tried again. */
    DELIVERY_ATTEMPT_FAILED,
    DELIVERED,
    RETURNING,
    RETURNED,
    /** Cancelled before pickup. */
    CANCELLED,
    /** Cannot be delivered or returned (lost, damaged); set by a dispatcher. */
    FAILED;

    /** No further status change is possible. */
    public boolean isFinal() {
        return this == DELIVERED || this == RETURNED || this == CANCELLED || this == FAILED;
    }

    /** Statuses that carry a failure reason (ADR 0006 §4, ADR 0009 §4). */
    public boolean needsFailureReason() {
        return this == FAILED || this == DELIVERY_ATTEMPT_FAILED;
    }
}
