package com.swiftdrop.tracking.application;

/** The per-parcel or total limit of open tracking streams is reached (ADR 0007 §6); answered 503. */
public class TooManyStreamsException extends RuntimeException {

    TooManyStreamsException() {
        super("Too many live tracking connections, please retry later");
    }
}
