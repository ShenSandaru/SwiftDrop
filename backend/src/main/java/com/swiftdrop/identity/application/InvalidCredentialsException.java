package com.swiftdrop.identity.application;

/** Thrown for any failed login, so callers cannot tell whether the account exists. */
public class InvalidCredentialsException extends RuntimeException {

    InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
