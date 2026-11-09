package com.swiftdrop.delivery.infrastructure.outbox;

/**
 * The result of publishing one outbox row (ADR 0004 §3, §4.2). Only {@link #CONFIRMED} marks the row
 * published; every other outcome leaves it unpublished for a later retry.
 */
enum PublishOutcome {

    /** Positive publisher confirm and no return. */
    CONFIRMED(null, false),
    /** Negative publisher confirm. */
    NACKED("nack", false),
    /** Returned as unroutable: no queue is bound for the routing key. */
    RETURNED("returned", false),
    /** No confirm within the timeout; every following row would most likely wait as long. */
    CONFIRM_TIMEOUT("confirm-timeout", true),
    /** Connection or channel failure; every following row would fail the same way. */
    BROKER_UNAVAILABLE("broker-unavailable", true);

    private final String failureCategory;
    private final boolean endsPoll;

    PublishOutcome(String failureCategory, boolean endsPoll) {
        this.failureCategory = failureCategory;
        this.endsPoll = endsPoll;
    }

    /** Stored in {@code last_failure_category}; {@code null} for {@link #CONFIRMED}. */
    String failureCategory() {
        return failureCategory;
    }

    /** Broker-wide failures stop the current poll at once (ADR 0004 §4.2). */
    boolean endsPoll() {
        return endsPoll;
    }
}
