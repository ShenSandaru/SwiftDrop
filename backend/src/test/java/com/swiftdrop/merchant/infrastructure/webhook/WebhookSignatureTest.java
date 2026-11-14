package com.swiftdrop.merchant.infrastructure.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR 0004 §7.2 signatures and the reference verifier (ADR 0004 §11). */
class WebhookSignatureTest {

    private static final String SECRET = "whsec_test-secret";
    private static final long TIMESTAMP = 1_763_200_000L;
    private static final byte[] BODY = "{\"eventId\":\"e-1\"}".getBytes(StandardCharsets.UTF_8);
    /** Computed independently with .NET's HMACSHA256 over "1763200000.{\"eventId\":\"e-1\"}". */
    private static final String VECTOR = "v1=0ab77d22fb464e12f9c7c983d1fb0298f9fde911f7a8c71582f945f0691dfb9e";
    private static final Instant NOW = Instant.ofEpochSecond(TIMESTAMP + 30);

    @Test
    @DisplayName("Matches a fixed test vector: hex HMAC-SHA256 of \"<timestamp>.<body>\" keyed with the whole secret")
    void testVector() {
        assertThat(WebhookSignature.sign(SECRET, TIMESTAMP, BODY)).isEqualTo(VECTOR);
    }

    @Test
    @DisplayName("A fresh timestamp changes the signature; the same inputs give the same signature")
    void freshTimestamp() {
        assertThat(WebhookSignature.sign(SECRET, TIMESTAMP + 1, BODY)).isNotEqualTo(VECTOR);
        assertThat(WebhookSignature.sign(SECRET, TIMESTAMP, BODY)).isEqualTo(VECTOR);
    }

    @Test
    @DisplayName("The reference verifier accepts the valid signature within the tolerance")
    void verifies() {
        assertThat(WebhookSignature.verify(VECTOR, SECRET, Long.toString(TIMESTAMP), BODY, NOW,
                WebhookSignature.DEFAULT_TOLERANCE)).isTrue();
    }

    @Test
    @DisplayName("The verifier rejects a changed body, a wrong secret, an old or future timestamp and bad headers")
    void rejects() {
        Duration tolerance = WebhookSignature.DEFAULT_TOLERANCE;
        String ts = Long.toString(TIMESTAMP);
        byte[] changed = "{\"eventId\":\"e-2\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(WebhookSignature.verify(VECTOR, SECRET, ts, changed, NOW, tolerance)).isFalse();
        assertThat(WebhookSignature.verify(VECTOR, "whsec_other", ts, BODY, NOW, tolerance)).isFalse();
        assertThat(WebhookSignature.verify(VECTOR, SECRET, ts, BODY, NOW.plus(Duration.ofMinutes(6)), tolerance)).isFalse();
        assertThat(WebhookSignature.verify(VECTOR, SECRET, ts, BODY, NOW.minus(Duration.ofMinutes(6)), tolerance)).isFalse();
        assertThat(WebhookSignature.verify(VECTOR.replace("v1=", "v2="), SECRET, ts, BODY, NOW, tolerance)).isFalse();
        assertThat(WebhookSignature.verify("v1=zz", SECRET, ts, BODY, NOW, tolerance)).isFalse();
        assertThat(WebhookSignature.verify(VECTOR, SECRET, "yesterday", BODY, NOW, tolerance)).isFalse();
        assertThat(WebhookSignature.verify(null, SECRET, ts, BODY, NOW, tolerance)).isFalse();
    }
}
