package com.swiftdrop.merchant.domain;

/**
 * State of a webhook delivery record (ADR 0004 §7.1). The consumer (SD-410) creates records as
 * {@link #PENDING}; the sender (SD-411) moves them on. Stored by name; the database check lists the same values.
 */
public enum WebhookDeliveryStatus {
    /** Waiting to be sent, now or at {@code next_attempt_at}. */
    PENDING,
    /** Claimed by the sender under a lease. */
    IN_PROGRESS,
    /** The endpoint answered with a 2xx status. */
    SUCCEEDED,
    /** No further automatic attempt: attempts exhausted or a non-retryable outcome. */
    FAILED,
    /** The endpoint's signing secret could not be decrypted; nothing was sent. */
    SECRET_UNAVAILABLE
}
