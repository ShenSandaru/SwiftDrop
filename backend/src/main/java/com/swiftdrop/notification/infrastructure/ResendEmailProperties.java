package com.swiftdrop.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Resend email delivery.
 */
@ConfigurationProperties(prefix = "swiftdrop.notification.email.resend")
public record ResendEmailProperties(
        String apiKey,
        String fromAddress,
        String baseUrl) {

    public static final String DEFAULT_BASE_URL = "https://api.resend.com";
    public static final String DEFAULT_FROM_ADDRESS = "onboarding@resend.dev";

    public ResendEmailProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            fromAddress = DEFAULT_FROM_ADDRESS;
        }
    }
}
