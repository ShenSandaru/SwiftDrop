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
 * @param retryAfter the raw {@code Retry-After} header of a 429 or 503 response, else null; parsed by the sender
 */
public record WebhookCallResult(Outcome outcome, Integer httpStatus, Duration duration, String retryAfter) {

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

    public WebhookCallResult(Outcome outcome, Integer httpStatus, Duration duration) {
        this(outcome, httpStatus, duration, null);
    }

    static WebhookCallResult responded(int httpStatus, Duration duration, String retryAfter) {
        return new WebhookCallResult(Outcome.RESPONDED, httpStatus, duration, retryAfter);
    }

    static WebhookCallResult failed(Outcome outcome, Duration duration) {
        return new WebhookCallResult(outcome, null, duration, null);
    }
}
