package com.swiftdrop.merchant.application;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import com.swiftdrop.merchant.api.WebhookUrlValidator;

/**
 * Default registration-time webhook URL validator (Phase 4).
 * <p>
 * Enforces basic syntactic sanity, HTTPS protocol requirement, length limits, and prevents
 * embedded credentials (userinfo) in candidate URLs.
 * <p>
 * <b>Security Note (SSRF Architecture):</b>
 * This configuration-time URL validation is intended solely for immediate user feedback.
 * The authoritative SSRF defense (validating DNS resolution against internal/private IP ranges,
 * CIDR blocklists, and DNS rebinding mitigation) is executed by Member B's delivery dispatcher
 * immediately before each HTTP webhook payload is dispatched (SD-409).
 */
@Component
@ConditionalOnMissingBean(name = "memberBWebhookUrlValidator")
public class DefaultWebhookUrlValidator implements WebhookUrlValidator {

    private static final int MAX_URL_LENGTH = 2048;
    private static final String REQUIRED_SCHEME = "https";

    @Override
    public ValidationResult validate(String url) {
        if (url == null || url.isBlank()) {
            return ValidationResult.invalid("Webhook URL must not be blank");
        }

        String trimmed = url.trim();

        if (trimmed.length() > MAX_URL_LENGTH) {
            return ValidationResult.invalid(
                    String.format("Webhook URL exceeds maximum length of %d characters", MAX_URL_LENGTH));
        }

        try {
            URI uri = URI.create(trimmed);

            if (uri.getScheme() == null || !REQUIRED_SCHEME.equalsIgnoreCase(uri.getScheme())) {
                return ValidationResult.invalid("Webhook URL must use the secure HTTPS protocol (e.g. https://...)");
            }

            if (uri.getHost() == null || uri.getHost().isBlank()) {
                return ValidationResult.invalid("Webhook URL must include a valid host");
            }

            if (uri.getUserInfo() != null) {
                return ValidationResult.invalid("Webhook URL must not contain embedded user credentials");
            }

            return ValidationResult.valid();
        } catch (IllegalArgumentException e) {
            return ValidationResult.invalid("Invalid webhook URL syntax: " + e.getMessage());
        }
    }
}

