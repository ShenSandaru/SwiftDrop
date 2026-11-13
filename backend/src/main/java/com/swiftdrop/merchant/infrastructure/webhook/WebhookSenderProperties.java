package com.swiftdrop.merchant.infrastructure.webhook;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the webhook sender (SD-411) with the ADR 0004 §9 defaults; values outside the bounds stop
 * startup. {@code swiftdrop.merchant.webhooks.sender.enabled} switches the sender and is read by the configuration.
 *
 * @param maxAttempts  automatic attempts per record, 3 to 15
 * @param backoffBase  delay after the first failed attempt, doubled each time, 1 s to 5 min
 * @param backoffCap   longest delay between attempts, at most 1 hour; also caps {@code Retry-After}
 * @param jitter       random spread of each delay, 0 to 0.5 (0.2 = ±20 %)
 * @param pollInterval how often the sender looks for due records
 * @param batchSize    records claimed per poll, 1 to 100
 * @param lease        how long a claim is valid, at least 1 min (longer than connect + response timeout)
 */
@ConfigurationProperties("swiftdrop.merchant.webhooks")
public record WebhookSenderProperties(
        Integer maxAttempts,
        Duration backoffBase,
        Duration backoffCap,
        Double jitter,
        Duration pollInterval,
        Integer batchSize,
        Duration lease) {

    public WebhookSenderProperties {
        maxAttempts = maxAttempts == null ? 8 : maxAttempts;
        backoffBase = backoffBase == null ? Duration.ofSeconds(30) : backoffBase;
        backoffCap = backoffCap == null ? Duration.ofHours(1) : backoffCap;
        jitter = jitter == null ? 0.2 : jitter;
        pollInterval = pollInterval == null ? Duration.ofSeconds(5) : pollInterval;
        batchSize = batchSize == null ? 20 : batchSize;
        lease = lease == null ? Duration.ofMinutes(2) : lease;

        require(maxAttempts >= 3 && maxAttempts <= 15, "max-attempts must be 3 to 15");
        require(backoffBase.compareTo(Duration.ofSeconds(1)) >= 0 && backoffBase.compareTo(Duration.ofMinutes(5)) <= 0,
                "backoff-base must be 1s to 5min");
        require(backoffCap.compareTo(backoffBase) >= 0 && backoffCap.compareTo(Duration.ofHours(1)) <= 0,
                "backoff-cap must be at least backoff-base and at most 1h");
        require(jitter >= 0 && jitter <= 0.5, "jitter must be 0 to 0.5");
        require(!pollInterval.isNegative() && !pollInterval.isZero(), "poll-interval must be positive");
        require(batchSize >= 1 && batchSize <= 100, "batch-size must be 1 to 100");
        require(lease.compareTo(Duration.ofMinutes(1)) >= 0, "lease must be at least 1min");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("swiftdrop.merchant.webhooks: " + message);
        }
    }
}
