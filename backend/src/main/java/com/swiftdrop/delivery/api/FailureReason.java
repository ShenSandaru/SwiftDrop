package com.swiftdrop.delivery.api;

/**
 * Standard failure reason codes for undeliverable parcels (D-06, D-19).
 */
public enum FailureReason {
    RECIPIENT_UNAVAILABLE,
    ADDRESS_NOT_FOUND,
    REFUSED,
    DAMAGED,
    OTHER
}
