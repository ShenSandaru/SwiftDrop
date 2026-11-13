package com.swiftdrop.assistant.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the AI assistant (ADR 0010). A missing API key does not stop the application: the
 * assistant then answers with its "unavailable" message.
 *
 * @param apiKey             Gemini API key (from {@code SWIFTDROP_GEMINI_API_KEY}); never logged
 * @param model              Gemini model name (from {@code SWIFTDROP_ASSISTANT_MODEL})
 * @param baseUrl            Gemini API base URL
 * @param maxToolCalls       tool calls allowed for one message
 * @param rateLimitPerMinute messages one client may send per minute
 * @param timeout            how long to wait for the model
 */
@ConfigurationProperties("swiftdrop.assistant")
public record AssistantProperties(String apiKey, String model, String baseUrl, int maxToolCalls,
        int rateLimitPerMinute, Duration timeout) {

    static final String DEFAULT_MODEL = "gemini-2.5-flash";
    static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    public AssistantProperties {
        apiKey = apiKey != null ? apiKey.trim() : "";
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
        maxToolCalls = maxToolCalls > 0 ? maxToolCalls : 3;
        rateLimitPerMinute = rateLimitPerMinute > 0 ? rateLimitPerMinute : 10;
        timeout = timeout != null && !timeout.isNegative() && !timeout.isZero() ? timeout : Duration.ofSeconds(20);
    }

    public boolean hasApiKey() {
        return !apiKey.isEmpty();
    }

    /** Never includes the API key. */
    @Override
    public String toString() {
        return "AssistantProperties[model=" + model + ", baseUrl=" + baseUrl + ", maxToolCalls=" + maxToolCalls
                + ", rateLimitPerMinute=" + rateLimitPerMinute + ", timeout=" + timeout
                + ", apiKey=" + (hasApiKey() ? "set" : "missing") + "]";
    }
}
