package com.swiftdrop.merchant.infrastructure.webhook;

import java.time.Duration;

/**
 * Outcome of one webhook request, in the categories merchants may see (ADR 0004 §7.3): an HTTP
 * status, a timeout, a network error or a refused destination. No response body or connection
 * detail is kept.
 *
 * @param outcome    what happened
 * @param httpStatus the response status when {@code outcome} is {@link Outcome#RESPONDED}, else null
 * @param duration   time from the start of the check until the outcome
 */
public record WebhookCallResult(Outcome outcome, Integer httpStatus, Duration duration) {

    public enum Outcome {
        /** The endpoint answered; see {@code httpStatus}. Redirects are reported, not followed. */
        RESPONDED,
        /** Connecting or waiting for the response took longer than configured. */
        TIMEOUT,
        /** Name resolution, connection or TLS failed. */
        NETWORK_ERROR,
        /** The URL or one of its addresses is not allowed; nothing was sent. */
        REFUSED_DESTINATION
    }

    static WebhookCallResult responded(int httpStatus, Duration duration) {
        return new WebhookCallResult(Outcome.RESPONDED, httpStatus, duration);
    }

    static WebhookCallResult failed(Outcome outcome, Duration duration) {
        return new WebhookCallResult(outcome, null, duration);
    }
}
