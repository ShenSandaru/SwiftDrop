package com.swiftdrop.merchant.application;

/**
 * A message on the merchant event queue that is not a valid {@code parcel.status-changed} event of a
 * supported schema version. Never retried: the message goes to the dead-letter queue at once (ADR 0004 §6).
 */
public class InvalidMerchantEventException extends RuntimeException {

    public InvalidMerchantEventException(String message) {
        super(message);
    }

    public InvalidMerchantEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
