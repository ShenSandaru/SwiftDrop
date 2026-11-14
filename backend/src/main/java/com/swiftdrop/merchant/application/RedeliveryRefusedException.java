package com.swiftdrop.merchant.application;

/** A manual redelivery is not allowed in the record's current state; answered with 409. */
public class RedeliveryRefusedException extends RuntimeException {

    public RedeliveryRefusedException(String message) {
        super(message);
    }
}
