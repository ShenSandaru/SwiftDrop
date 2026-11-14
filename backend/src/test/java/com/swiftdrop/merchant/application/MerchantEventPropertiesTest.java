package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MerchantEventPropertiesTest {

    private static MerchantEventProperties defaults() {
        return new MerchantEventProperties(null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("Defaults are the ADR 0004 §9 values")
    void defaultsFollowTheAdr() {
        MerchantEventProperties p = defaults();

        assertThat(p.attempts()).isEqualTo(3);
        assertThat(p.retryInitialDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(p.retryMaxDelay()).isEqualTo(Duration.ofSeconds(10));
        assertThat(p.gracePeriod()).isEqualTo(Duration.ofSeconds(60));
        assertThat(p.reconcileAttempts()).isEqualTo(5);
        assertThat(p.reconcileBackoff()).isEqualTo(Duration.ofMinutes(1));
        assertThat(p.processedRetention()).isEqualTo(Duration.ofDays(30));
        assertThat(p.retentionBatchSize()).isEqualTo(1_000);
    }

    @Test
    @DisplayName("Reconciliation backoff is 1 min, doubled per failure, capped at 1 hour")
    void reconcileBackoff() {
        MerchantEventProperties p = defaults();

        assertThat(p.reconcileBackoffAfter(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(p.reconcileBackoffAfter(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(p.reconcileBackoffAfter(4)).isEqualTo(Duration.ofMinutes(8));
        assertThat(p.reconcileBackoffAfter(20)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("Values outside the ADR bounds stop startup")
    void bounds() {
        assertThatThrownBy(() -> new MerchantEventProperties(6, null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new MerchantEventProperties(null, null, Duration.ofSeconds(31), null, null, null, null,
                null, null, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new MerchantEventProperties(null, null, null, Duration.ofSeconds(5), null, null, null,
                null, null, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new MerchantEventProperties(null, null, null, null, null, 2, null, null, null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new MerchantEventProperties(null, null, null, null, null, null, null, null,
                Duration.ofHours(1), null)).isInstanceOf(IllegalStateException.class);
    }
}
