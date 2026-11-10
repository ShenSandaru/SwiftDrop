package com.swiftdrop.delivery.api;

/** State of a courier task (ADR 0008, ADR 0009 §5). */
public enum ParcelAssignmentStatus {
    ACTIVE,
    COMPLETED,
    REASSIGNED,
    /** The parcel was cancelled, or failed, before the task was done. */
    CANCELLED
}
