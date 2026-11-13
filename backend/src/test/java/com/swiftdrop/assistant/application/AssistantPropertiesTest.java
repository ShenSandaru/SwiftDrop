package com.swiftdrop.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class AssistantPropertiesTest {

    @Test
    void emptySettingsFallBackToSafeDefaults() {
        AssistantProperties properties = new AssistantProperties(null, " ", null, 0, 0, null);

        assertThat(properties.hasApiKey()).isFalse();
        assertThat(properties.model()).isEqualTo(AssistantProperties.DEFAULT_MODEL);
        assertThat(properties.baseUrl()).isEqualTo(AssistantProperties.DEFAULT_BASE_URL);
        assertThat(properties.maxToolCalls()).isEqualTo(3);
        assertThat(properties.rateLimitPerMinute()).isEqualTo(10);
        assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void trimsTheKeyAndNeverPrintsIt() {
        AssistantProperties properties = new AssistantProperties(" secret-key ", "m", "https://x", 2, 5,
                Duration.ofSeconds(5));

        assertThat(properties.apiKey()).isEqualTo("secret-key");
        assertThat(properties.toString()).doesNotContain("secret-key").contains("apiKey=set");
    }
}
