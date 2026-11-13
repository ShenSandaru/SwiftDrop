package com.swiftdrop.notification.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for scheduled pending event reconciliation (ADR 0004 §4.3).
 */
@ConfigurationProperties(prefix = "swiftdrop.notification.reconciliation")
public record NotificationReconciliationProperties(
        boolean enabled,
        Duration interval,
        Duration gracePeriod,
        int batchSize,
        int maxAttempts) {

    public NotificationReconciliationProperties {
        if (interval == null) {
            interval = Duration.ofSeconds(30);
        }
        if (gracePeriod == null) {
            gracePeriod = Duration.ofSeconds(15);
        }
        if (batchSize < 1) {
            batchSize = 50;
        }
        if (maxAttempts < 1) {
            maxAttempts = 5;
        }
    }

    public static NotificationReconciliationProperties defaults() {
        return new NotificationReconciliationProperties(
                true,
                Duration.ofSeconds(30),
                Duration.ofSeconds(15),
                50,
                5);
    }
}

