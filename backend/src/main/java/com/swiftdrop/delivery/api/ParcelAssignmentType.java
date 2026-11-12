package com.swiftdrop.delivery.api;

/** A courier task (ADR 0008, ADR 0009 §5). */
public enum ParcelAssignmentType {
    /** Collect the parcel from the sender. */
    PICKUP,
    /** Deliver from the destination hub to the recipient; one task per attempt. */
    DELIVERY,
    /** Take the parcel back from the destination hub to the sender. */
    RETURN
}
