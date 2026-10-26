package com.swiftdrop.identity.application;

/**
 * Thrown when an exchange code is missing, malformed, expired, already redeemed, or associated
 * with an inactive account (ADR 0005).
 */
public class InvalidExchangeCodeException extends RuntimeException {

    public InvalidExchangeCodeException() {
        super("Invalid or expired exchange code");
    }
}
