package com.swiftdrop.dispatch.application;

public class DispatchCourierNotFoundException extends RuntimeException {

    public DispatchCourierNotFoundException() {
        super("Courier account not found");
    }
}
