package com.swiftdrop.merchant.domain;

/**
 * Outcome of one webhook attempt, in the categories merchants may see (ADR 0004 §7.3): never response
 * bodies or connection details. Stored by name; the database check lists the same values.
 */
public enum WebhookAttemptOutcome {
    /** The endpoint answered with a 2xx status. */
    SUCCEEDED,
    /** The endpoint answered with another status; see the attempt's HTTP status. */
    HTTP_ERROR,
    /** Connecting or waiting for the response took longer than configured. */
    TIMEOUT,
    /** Name resolution, connection or TLS failed. */
    NETWORK_ERROR,
    /** The URL or one of its addresses is not allowed (SSRF rules); nothing was sent. */
    REFUSED_DESTINATION,
    /** The endpoint is deleted, disabled or not the merchant's; nothing was sent. */
    ENDPOINT_INACTIVE,
    /** The merchant is not APPROVED at send time; nothing was sent. */
    MERCHANT_NOT_APPROVED,
    /** The merchant no longer exists; nothing was sent. */
    MERCHANT_NOT_FOUND
}
