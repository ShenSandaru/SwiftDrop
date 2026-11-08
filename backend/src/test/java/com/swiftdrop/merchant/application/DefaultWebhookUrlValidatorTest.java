package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.swiftdrop.merchant.api.WebhookUrlValidator;

class DefaultWebhookUrlValidatorTest {

    private final DefaultWebhookUrlValidator validator = new DefaultWebhookUrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/webhook",
            "https://api.merchant.com/events/v1",
            "https://sub.domain.co.uk:8443/swiftdrop-hook"
    })
    @DisplayName("Valid HTTPS URLs pass validation")
    void validHttpsUrls(String url) {
        WebhookUrlValidator.ValidationResult result = validator.validate(url);
        assertThat(result.isValid()).isTrue();
        assertThat(result.message()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com/webhook",
            "ftp://example.com/webhook",
            "file:///etc/passwd"
    })
    @DisplayName("Non-HTTPS URLs are rejected")
    void rejectNonHttps(String url) {
        WebhookUrlValidator.ValidationResult result = validator.validate(url);
        assertThat(result.isValid()).isFalse();
        assertThat(result.message()).contains("HTTPS");
    }

    @Test
    @DisplayName("URL with embedded user credentials is rejected")
    void rejectUserInfo() {
        WebhookUrlValidator.ValidationResult result = validator.validate("https://user:password@example.com/webhook");
        assertThat(result.isValid()).isFalse();
        assertThat(result.message()).contains("user credentials");
    }

    @Test
    @DisplayName("Blank or null URL is rejected")
    void rejectBlankOrNull() {
        assertThat(validator.validate(null).isValid()).isFalse();
        assertThat(validator.validate("").isValid()).isFalse();
        assertThat(validator.validate("   ").isValid()).isFalse();
    }

    @Test
    @DisplayName("URL exceeding 2048 characters is rejected")
    void rejectOverlongUrl() {
        String longUrl = "https://example.com/" + "a".repeat(2050);
        WebhookUrlValidator.ValidationResult result = validator.validate(longUrl);
        assertThat(result.isValid()).isFalse();
        assertThat(result.message()).contains("exceeds maximum length");
    }
}
