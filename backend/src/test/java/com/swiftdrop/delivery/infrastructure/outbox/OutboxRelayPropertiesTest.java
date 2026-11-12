package com.swiftdrop.delivery.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class OutboxRelayPropertiesTest {

    private static OutboxRelayProperties with(Duration pollInterval, int batchSize, Duration confirmTimeout,
            Duration backoffMax, Duration retention) {
        return new OutboxRelayProperties(pollInterval, batchSize, confirmTimeout, Duration.ofSeconds(1), backoffMax,
                retention, Duration.ofSeconds(30), Duration.ofMinutes(2), 10, Duration.ofMinutes(1));
    }

    @Test
    void defaultsAreTheAdrValues() {
        OutboxRelayProperties defaults = OutboxRelayProperties.defaults();

        assertThat(defaults.pollInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(defaults.batchSize()).isEqualTo(100);
        assertThat(defaults.confirmTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(defaults.retention()).isEqualTo(Duration.ofDays(7));
        assertThat(defaults.alertAttempts()).isEqualTo(10);
    }

    @Test
    void backoffDoublesUpToTheCap() {
        OutboxRelayProperties defaults = OutboxRelayProperties.defaults();

        assertThat(defaults.backoff(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(defaults.backoff(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(defaults.backoff(4)).isEqualTo(Duration.ofSeconds(8));
        assertThat(defaults.backoff(7)).isEqualTo(Duration.ofMinutes(1));
        assertThat(defaults.backoff(1000)).isEqualTo(Duration.ofMinutes(1));
        assertThatThrownBy(() -> defaults.backoff(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valuesOutsideTheAdrBoundsStopStartup() {
        Duration second = Duration.ofSeconds(1);
        Duration week = Duration.ofDays(7);
        Duration minute = Duration.ofMinutes(1);

        assertThatThrownBy(() -> with(Duration.ofMillis(100), 100, Duration.ofSeconds(5), minute, week))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("poll-interval");
        assertThatThrownBy(() -> with(second, 501, Duration.ofSeconds(5), minute, week))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("batch-size");
        assertThatThrownBy(() -> with(second, 100, Duration.ofSeconds(31), minute, week))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("confirm-timeout");
        assertThatThrownBy(() -> with(second, 100, Duration.ofSeconds(5), Duration.ofMinutes(6), week))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("backoff-max");
        assertThatThrownBy(() -> with(second, 100, Duration.ofSeconds(5), minute, Duration.ofHours(12)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("retention");
    }

    @Test
    void initialBackoffCannotExceedTheCap() {
        assertThatThrownBy(() -> new OutboxRelayProperties(Duration.ofSeconds(1), 100, Duration.ofSeconds(5),
                Duration.ofMinutes(2), Duration.ofMinutes(1), Duration.ofDays(7), Duration.ofSeconds(30),
                Duration.ofMinutes(2), 10, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("backoff-initial");
    }
}
