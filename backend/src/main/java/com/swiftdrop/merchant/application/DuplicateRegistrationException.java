package com.swiftdrop.merchant.application;

/**
 * Thrown when a merchant application conflicts with an existing email or business registration number.
 */
public class DuplicateRegistrationException extends RuntimeException {

    public DuplicateRegistrationException(String message) {
        super(message);
    }

    public DuplicateRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}

