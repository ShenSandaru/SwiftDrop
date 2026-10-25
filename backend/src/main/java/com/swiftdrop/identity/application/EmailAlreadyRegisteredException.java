package com.swiftdrop.identity.application;

public class EmailAlreadyRegisteredException extends RuntimeException {

    EmailAlreadyRegisteredException() {
        super("Email address is already registered");
    }
}
