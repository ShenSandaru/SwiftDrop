package com.swiftdrop.merchant.infrastructure.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Webhook request signatures (ADR 0004 §7.2). {@code X-SwiftDrop-Signature: v1=<hex>} is the lowercase hex
 * HMAC-SHA256 of {@code "<timestamp>." + raw body bytes}, keyed with the UTF-8 bytes of the endpoint's signing
 * secret exactly as shown to the merchant ({@code whsec_…}). Every attempt has a fresh timestamp; body and event
 * ID stay the same. The {@code v1=} prefix lets a later scheme be added alongside.
 */
public final class WebhookSignature {

    public static final String EVENT_ID_HEADER = "X-SwiftDrop-Event-Id";
    public static final String EVENT_TYPE_HEADER = "X-SwiftDrop-Event-Type";
    public static final String TIMESTAMP_HEADER = "X-SwiftDrop-Timestamp";
    public static final String SIGNATURE_HEADER = "X-SwiftDrop-Signature";
    /** How far a receiver accepts timestamps from its own clock (ADR 0004 §9). */
    public static final Duration DEFAULT_TOLERANCE = Duration.ofMinutes(5);

    private static final String SCHEME = "v1=";
    private static final String ALGORITHM = "HmacSHA256";

    private WebhookSignature() {
    }

    /** The {@code X-SwiftDrop-Signature} value for this secret, timestamp and body. */
    public static String sign(String secret, long timestampSeconds, byte[] body) {
        return SCHEME + HexFormat.of().formatHex(hmac(secret, timestampSeconds, body));
    }

    /**
     * Reference verification for receivers: compares in constant time over the raw body and rejects timestamps
     * further than {@code tolerance} from {@code now}. Deduplication by event ID is the receiver's own job.
     */
    public static boolean verify(String signatureHeader, String secret, String timestampHeader, byte[] body,
            Instant now, Duration tolerance) {
        if (signatureHeader == null || !signatureHeader.startsWith(SCHEME) || timestampHeader == null) {
            return false;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(now.getEpochSecond() - timestamp) > tolerance.toSeconds()) {
            return false;
        }
        byte[] expected = hmac(secret, timestamp, body);
        byte[] presented;
        try {
            presented = HexFormat.of().parseHex(signatureHeader.substring(SCHEME.length()));
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, presented);
    }

    private static byte[] hmac(String secret, long timestampSeconds, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            mac.update((timestampSeconds + ".").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(body);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
