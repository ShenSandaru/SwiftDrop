package com.swiftdrop.identity.application;

/**
 * Thrown when an administrator attempts to disable their own account.
 */
public class SelfDisablingException extends RuntimeException {
    public SelfDisablingException(String message) {
        super(message);
    }
}
