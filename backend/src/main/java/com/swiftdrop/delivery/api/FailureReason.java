package com.swiftdrop.delivery.api;

/**
 * Standard failure reason codes for undeliverable parcels (D-06, D-19), with the retry rule of
 * ADR 0009 §4.
 */
public enum FailureReason {
    RECIPIENT_UNAVAILABLE,
    ADDRESS_NOT_FOUND,
    REFUSED,
    DAMAGED,
    OTHER;

    /** After a failed attempt with this reason, the parcel may be tried again (until the attempt limit). */
    public boolean allowsRetry() {
        return this == RECIPIENT_UNAVAILABLE || this == OTHER;
    }

    /** A reason a courier may give for a failed attempt; damage is a dispatcher's {@code FAILED} instead. */
    public boolean isAttemptReason() {
        return this != DAMAGED;
    }
}
