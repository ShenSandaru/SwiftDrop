package com.swiftdrop.assistant.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.swiftdrop.shared.web.ClientRateLimiter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssistantProperties.class)
class AssistantConfiguration {

    /** One limiter for the chat endpoint and the MCP tool calls (ADR 0010 §4). */
    @Bean
    ClientRateLimiter assistantRateLimiter(AssistantProperties properties) {
        return new ClientRateLimiter(properties.rateLimitPerMinute());
    }
}
