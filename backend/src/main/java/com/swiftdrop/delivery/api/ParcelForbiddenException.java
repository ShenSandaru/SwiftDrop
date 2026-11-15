package com.swiftdrop.delivery.api;

/**
 * The caller is not allowed to act on this parcel because it is not in the hands of one of their
 * hubs (ADR 0012 §3). Answered with 403; the caller can already see that the parcel exists.
 */
public class ParcelForbiddenException extends RuntimeException {

    public ParcelForbiddenException(String message) {
        super(message);
    }
}
