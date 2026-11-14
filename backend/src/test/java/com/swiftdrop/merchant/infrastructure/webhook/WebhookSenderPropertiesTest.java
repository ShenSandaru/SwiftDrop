package com.swiftdrop.merchant.infrastructure.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebhookSenderPropertiesTest {

    @Test
    @DisplayName("Defaults are the ADR 0004 §9 values")
    void defaults() {
        WebhookSenderProperties p = new WebhookSenderProperties(null, null, null, null, null, null, null);

        assertThat(p.maxAttempts()).isEqualTo(8);
        assertThat(p.backoffBase()).isEqualTo(Duration.ofSeconds(30));
        assertThat(p.backoffCap()).isEqualTo(Duration.ofHours(1));
        assertThat(p.jitter()).isEqualTo(0.2);
        assertThat(p.pollInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(p.batchSize()).isEqualTo(20);
        assertThat(p.lease()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    @DisplayName("Values outside the ADR bounds stop startup")
    void bounds() {
        assertThatThrownBy(() -> new WebhookSenderProperties(2, null, null, null, null, null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WebhookSenderProperties(16, null, null, null, null, null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WebhookSenderProperties(null, Duration.ofMinutes(6), null, null, null, null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WebhookSenderProperties(null, null, Duration.ofHours(2), null, null, null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WebhookSenderProperties(null, null, null, 0.6, null, null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WebhookSenderProperties(null, null, null, null, null, 0, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WebhookSenderProperties(null, null, null, null, null, null, Duration.ofSeconds(30)))
                .isInstanceOf(IllegalStateException.class);
    }
}
