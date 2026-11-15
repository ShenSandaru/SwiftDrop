package com.swiftdrop.pricing.api;

/**
 * The requested pricing entity (e.g. zone or pricing rule) does not exist.
 * Mapped to HTTP 404 Not Found.
 */
public class PricingNotFoundException extends RuntimeException {

    public PricingNotFoundException(String message) {
        super(message);
    }
}

