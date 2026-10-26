package com.swiftdrop.identity.application;

/**
 * Thrown when an invalid or disallowed role is submitted.
 */
public class InvalidRoleException extends RuntimeException {
    public InvalidRoleException(String message) {
        super(message);
    }
}
