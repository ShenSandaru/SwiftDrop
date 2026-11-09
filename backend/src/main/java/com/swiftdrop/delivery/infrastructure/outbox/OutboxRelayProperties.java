package com.swiftdrop.delivery.infrastructure.outbox;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Outbox relay settings. Defaults and allowed ranges come from ADR 0004 §9; startup fails if a value
 * is outside its range.
 *
 * @param pollInterval        pause between two polls
 * @param batchSize           unpublished rows read per poll
 * @param confirmTimeout      how long to wait for the broker's publisher confirm
 * @param backoffInitial      wait before the first retry of a failed row; doubles per attempt
 * @param backoffMax          longest wait between two attempts of one row
 * @param retention           how long published rows are kept before the daily cleanup deletes them
 * @param monitorInterval     how often the monitor refreshes the metrics and checks the thresholds
 * @param alertOldestAge      alert when the oldest unpublished row is older than this
 * @param alertAttempts       alert when a row has at least this many failed attempts
 * @param alertPollStale      alert when no poll completed for this long while rows are unpublished
 */
@ConfigurationProperties("swiftdrop.outbox.relay")
public record OutboxRelayProperties(
        @DefaultValue("1s") Duration pollInterval,
        @DefaultValue("100") int batchSize,
        @DefaultValue("5s") Duration confirmTimeout,
        @DefaultValue("1s") Duration backoffInitial,
        @DefaultValue("1m") Duration backoffMax,
        @DefaultValue("7d") Duration retention,
        @DefaultValue("30s") Duration monitorInterval,
        @DefaultValue("2m") Duration alertOldestAge,
        @DefaultValue("10") int alertAttempts,
        @DefaultValue("1m") Duration alertPollStale) {

    private static final String PREFIX = "swiftdrop.outbox.relay.";

    public OutboxRelayProperties {
        requireBetween("poll-interval", pollInterval, Duration.ofMillis(500), Duration.ofSeconds(10));
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalStateException(PREFIX + "batch-size must be between 1 and 500");
        }
        requireBetween("confirm-timeout", confirmTimeout, Duration.ofSeconds(1), Duration.ofSeconds(30));
        requireBetween("backoff-max", backoffMax, Duration.ofMillis(1), Duration.ofMinutes(5));
        requireBetween("backoff-initial", backoffInitial, Duration.ofMillis(1), backoffMax);
        requireBetween("retention", retention, Duration.ofDays(1), Duration.ofDays(3650));
        requireBetween("monitor-interval", monitorInterval, Duration.ofSeconds(1), Duration.ofMinutes(10));
        requireBetween("alert-oldest-age", alertOldestAge, Duration.ofSeconds(1), Duration.ofDays(1));
        requireBetween("alert-poll-stale", alertPollStale, Duration.ofSeconds(1), Duration.ofDays(1));
        if (alertAttempts < 1) {
            throw new IllegalStateException(PREFIX + "alert-attempts must be at least 1");
        }
    }

    /** The defaults of ADR 0004 §9. */
    public static OutboxRelayProperties defaults() {
        return new OutboxRelayProperties(Duration.ofSeconds(1), 100, Duration.ofSeconds(5), Duration.ofSeconds(1),
                Duration.ofMinutes(1), Duration.ofDays(7), Duration.ofSeconds(30), Duration.ofMinutes(2), 10,
                Duration.ofMinutes(1));
    }

    /** Exponential backoff after the given failed attempt (1-based): initial, ×2 each time, capped. */
    public Duration backoff(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1");
        }
        Duration wait = backoffInitial;
        for (int i = 1; i < attempt && wait.compareTo(backoffMax) < 0; i++) {
            wait = wait.multipliedBy(2);
        }
        return wait.compareTo(backoffMax) > 0 ? backoffMax : wait;
    }

    private static void requireBetween(String name, Duration value, Duration min, Duration max) {
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalStateException(PREFIX + name + " must be between " + min + " and " + max);
        }
    }
}
