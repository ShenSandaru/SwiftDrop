package com.swiftdrop.merchant.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the merchant event consumer (SD-410) with the ADR 0004 §9 defaults; values outside the
 * ADR bounds stop startup. {@code swiftdrop.merchant.events.consumer.enabled} switches the consumer on or
 * off and is read by the configuration, not here.
 *
 * @param attempts             listener attempts per message in total (first try included), 1 to 5
 * @param retryInitialDelay    delay before the first retry, doubled each time
 * @param retryMaxDelay        longest delay between retries, at most 30 s
 * @param gracePeriod          how long a pending event waits before its gap is reconciled, 10 s to 10 min
 * @param reconcileInterval    how often the reconciler looks for due pending events
 * @param reconcileAttempts    failed reconciliations before a parcel is marked stuck, 3 to 10
 * @param reconcileBackoff     delay after the first failed reconciliation, doubled each time
 * @param reconcileBatchSize   parcels handled per reconciler run
 * @param processedRetention   how long processed event IDs are kept, at least 1 day
 * @param retentionBatchSize   processed IDs deleted per transaction
 */
@ConfigurationProperties("swiftdrop.merchant.events")
public record MerchantEventProperties(
        Integer attempts,
        Duration retryInitialDelay,
        Duration retryMaxDelay,
        Duration gracePeriod,
        Duration reconcileInterval,
        Integer reconcileAttempts,
        Duration reconcileBackoff,
        Integer reconcileBatchSize,
        Duration processedRetention,
        Integer retentionBatchSize) {

    public MerchantEventProperties {
        attempts = attempts == null ? 3 : attempts;
        retryInitialDelay = retryInitialDelay == null ? Duration.ofSeconds(1) : retryInitialDelay;
        retryMaxDelay = retryMaxDelay == null ? Duration.ofSeconds(10) : retryMaxDelay;
        gracePeriod = gracePeriod == null ? Duration.ofSeconds(60) : gracePeriod;
        reconcileInterval = reconcileInterval == null ? Duration.ofSeconds(30) : reconcileInterval;
        reconcileAttempts = reconcileAttempts == null ? 5 : reconcileAttempts;
        reconcileBackoff = reconcileBackoff == null ? Duration.ofMinutes(1) : reconcileBackoff;
        reconcileBatchSize = reconcileBatchSize == null ? 50 : reconcileBatchSize;
        processedRetention = processedRetention == null ? Duration.ofDays(30) : processedRetention;
        retentionBatchSize = retentionBatchSize == null ? 1_000 : retentionBatchSize;

        require(attempts >= 1 && attempts <= 5, "attempts must be 1 to 5");
        require(positive(retryInitialDelay) && positive(retryMaxDelay)
                && retryMaxDelay.compareTo(Duration.ofSeconds(30)) <= 0
                && retryInitialDelay.compareTo(retryMaxDelay) <= 0,
                "retry delays must be positive, initial <= max <= 30s");
        require(gracePeriod.compareTo(Duration.ofSeconds(10)) >= 0 && gracePeriod.compareTo(Duration.ofMinutes(10)) <= 0,
                "grace-period must be 10s to 10min");
        require(positive(reconcileInterval), "reconcile-interval must be positive");
        require(reconcileAttempts >= 3 && reconcileAttempts <= 10, "reconcile-attempts must be 3 to 10");
        require(positive(reconcileBackoff), "reconcile-backoff must be positive");
        require(reconcileBatchSize >= 1 && reconcileBatchSize <= 500, "reconcile-batch-size must be 1 to 500");
        require(processedRetention.compareTo(Duration.ofDays(1)) >= 0, "processed-retention must be at least 1 day");
        require(retentionBatchSize >= 1 && retentionBatchSize <= 10_000, "retention-batch-size must be 1 to 10000");
    }

    /** Backoff before the next reconciliation after {@code failures} failed ones, capped at 1 hour. */
    public Duration reconcileBackoffAfter(int failures) {
        long factor = 1L << Math.min(Math.max(failures - 1, 0), 10);
        Duration delay = reconcileBackoff.multipliedBy(factor);
        return delay.compareTo(Duration.ofHours(1)) > 0 ? Duration.ofHours(1) : delay;
    }

    private static boolean positive(Duration duration) {
        return !duration.isNegative() && !duration.isZero();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("swiftdrop.merchant.events: " + message);
        }
    }
}
