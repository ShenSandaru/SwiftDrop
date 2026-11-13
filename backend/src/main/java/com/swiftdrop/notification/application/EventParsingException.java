package com.swiftdrop.notification.application;

/**
 * Thrown when an incoming event payload is malformed, missing required fields, or violates schema contracts.
 */
public class EventParsingException extends RuntimeException {

    public EventParsingException(String message) {
        super(message);
    }

    public EventParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}

