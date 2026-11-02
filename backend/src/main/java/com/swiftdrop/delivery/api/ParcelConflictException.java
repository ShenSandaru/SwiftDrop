package com.swiftdrop.delivery.api;

/**
 * A valid request that breaks a parcel lifecycle rule, for example assigning a courier after
 * {@link ParcelStatus#CREATED} (ADR 0006 §3, §7). Callers map it to 409. The message is safe to
 * return to clients: it names the rule, never parcel data.
 */
public class ParcelConflictException extends RuntimeException {

    public ParcelConflictException(String message) {
        super(message);
    }
}
