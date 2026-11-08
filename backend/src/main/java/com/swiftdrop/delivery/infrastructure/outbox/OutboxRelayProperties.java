package com.swiftdrop.delivery.infrastructure.outbox;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbox relay settings (ADR 0004 §9). Values outside the ADR's bounds stop the application at
 * startup. The relay itself is switched on and off with {@code swiftdrop.outbox.relay.enabled}.
 *
 * @param pollInterval          pause between polls (default 1 s, 0.5 to 10 s)
 * @param batchSize             unpublished rows read per poll (default 100, 1 to 500)
 * @param confirmTimeout        wait for the broker's publisher confirm (default 5 s, 1 to 30 s)
 * @param maxBackoff            longest wait between attempts of one row (default 1 min, at most 5 min)
 * @param retention             how long published rows are kept (default 7 days, at least 1 day)
 * @param attemptAlertThreshold attempts after which a failing row is logged as an error (default 10)
 * @param oldestAlertAge        age of the oldest unpublished row that is logged as an error (default 2 min)
 * @param stalledPollAlert      time without a completed poll, while rows wait, that is logged as an
 *                              error (default 1 min)
 */
@ConfigurationProperties("swiftdrop.outbox.relay")
public record OutboxRelayProperties(Duration pollInterval, Integer batchSize, Duration confirmTimeout,
        Duration maxBackoff, Duration retention, Integer attemptAlertThreshold, Duration oldestAlertAge,
        Duration stalledPollAlert) {

    public OutboxRelayProperties {
        pollInterval = orDefault(pollInterval, Duration.ofSeconds(1));
        batchSize = batchSize == null ? 100 : batchSize;
        confirmTimeout = orDefault(confirmTimeout, Duration.ofSeconds(5));
        maxBackoff = orDefault(maxBackoff, Duration.ofMinutes(1));
        retention = orDefault(retention, Duration.ofDays(7));
        attemptAlertThreshold = attemptAlertThreshold == null ? 10 : attemptAlertThreshold;
        oldestAlertAge = orDefault(oldestAlertAge, Duration.ofMinutes(2));
        stalledPollAlert = orDefault(stalledPollAlert, Duration.ofMinutes(1));

        requireBetween(pollInterval, Duration.ofMillis(500), Duration.ofSeconds(10), "poll-interval");
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalStateException("swiftdrop.outbox.relay.batch-size must be 1 to 500");
        }
        requireBetween(confirmTimeout, Duration.ofSeconds(1), Duration.ofSeconds(30), "confirm-timeout");
        requireBetween(maxBackoff, Duration.ofSeconds(1), Duration.ofMinutes(5), "max-backoff");
        if (retention.compareTo(Duration.ofDays(1)) < 0) {
            throw new IllegalStateException("swiftdrop.outbox.relay.retention must be at least 1 day");
        }
        if (attemptAlertThreshold < 1) {
            throw new IllegalStateException("swiftdrop.outbox.relay.attempt-alert-threshold must be positive");
        }
    }

    private static Duration orDefault(Duration value, Duration fallback) {
        return value == null ? fallback : value;
    }

    private static void requireBetween(Duration value, Duration min, Duration max, String name) {
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalStateException("swiftdrop.outbox.relay." + name + " must be between " + min + " and " + max);
        }
    }
}
