package com.swiftdrop.merchant.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Format and hashing of merchant API keys (ADR 0008 §2, §3):
 * {@code sdk_<12 chars [a-z0-9]>_<32 bytes base64url without padding>}, 60 characters, stored only as
 * the lowercase hex SHA-256 of the full key.
 */
public final class MerchantApiKeyFormat {

    public static final String MARKER = "sdk_";
    public static final int PREFIX_LENGTH = 12;
    public static final int SECRET_BYTES = 32;
    public static final int KEY_LENGTH = 60;

    private static final Pattern FORMAT = Pattern.compile("sdk_[a-z0-9]{12}_[A-Za-z0-9_-]{43}");

    private MerchantApiKeyFormat() {
    }

    public static boolean isWellFormed(String key) {
        return key != null && key.length() == KEY_LENGTH && FORMAT.matcher(key).matches();
    }

    /** The public prefix of a well-formed key. */
    public static String prefixOf(String key) {
        return key.substring(MARKER.length(), MARKER.length() + PREFIX_LENGTH);
    }

    /** Lowercase hex SHA-256 of the full key (UTF-8). */
    public static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }

    /** Compares the hash of {@code key} with {@code storedHash} in constant time. */
    public static boolean matches(String key, String storedHash) {
        return MessageDigest.isEqual(
                hash(key).getBytes(StandardCharsets.US_ASCII),
                storedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
