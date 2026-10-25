package com.swiftdrop.identity.application;

public class InvalidPasswordException extends RuntimeException {

    InvalidPasswordException(String message) {
        super(message);
    }
}
