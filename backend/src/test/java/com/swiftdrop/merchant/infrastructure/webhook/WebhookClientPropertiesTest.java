package com.swiftdrop.merchant.infrastructure.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebhookClientPropertiesTest {

    @Test
    @DisplayName("Defaults follow ADR 0004 §9: local mode off, port 443, 5 s connect, 10 s response")
    void defaults() {
        var properties = new WebhookClientProperties(false, null, null, null, null);

        assertThat(properties.allowLocal()).isFalse();
        assertThat(properties.allowedPorts()).containsExactly(443);
        assertThat(properties.blockedHosts()).isEmpty();
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.responseTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Timeouts outside the ADR bounds stop startup")
    void timeoutBounds() {
        assertThatThrownBy(() -> new WebhookClientProperties(false, null, null, Duration.ofSeconds(11), null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WebhookClientProperties(false, null, null, null, Duration.ofSeconds(31)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WebhookClientProperties(false, null, null, Duration.ZERO, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WebhookClientProperties(false, null, null, null, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Ports outside 1 to 65535 stop startup")
    void portBounds() {
        assertThatThrownBy(() -> new WebhookClientProperties(false, List.of(0), null, null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WebhookClientProperties(false, List.of(65536), null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
