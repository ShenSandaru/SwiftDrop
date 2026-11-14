package com.swiftdrop.pricing.api;

/**
 * A valid request that breaks a pricing domain rule or conflict constraint,
 * such as a duplicate zone code/name, duplicate zone-pair pricing rule,
 * or optimistic locking version mismatch. Mapped to HTTP 409 Conflict.
 */
public class PricingConflictException extends RuntimeException {

    public PricingConflictException(String message) {
        super(message);
    }
}

