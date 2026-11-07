package com.swiftdrop.merchant.application;

/** The merchant already has the maximum number of active keys (ADR 0008 §6). */
public class ApiKeyLimitReachedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ApiKeyLimitReachedException(int limit) {
        super("A merchant can have at most " + limit + " active API keys; revoke one first");
    }
}
