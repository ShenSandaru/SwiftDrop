package com.swiftdrop.merchant.application;

/** The key does not exist or belongs to another merchant; both answer 404. */
public class ApiKeyNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ApiKeyNotFoundException() {
        super("API key not found");
    }
}
