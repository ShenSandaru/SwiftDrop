package com.swiftdrop.merchant.application;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.swiftdrop.merchant.domain.MerchantApiKeyFormat;

/** Generates merchant API keys in the ADR 0008 format from {@link SecureRandom}. */
@Component
class ApiKeyGenerator {

    private static final char[] PREFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private final SecureRandom random;

    ApiKeyGenerator() {
        this(new SecureRandom());
    }

    ApiKeyGenerator(SecureRandom random) {
        this.random = random;
    }

    /** A new key {@code sdk_<prefix>_<secret>}. */
    String generate() {
        char[] prefix = new char[MerchantApiKeyFormat.PREFIX_LENGTH];
        for (int i = 0; i < prefix.length; i++) {
            prefix[i] = PREFIX_ALPHABET[random.nextInt(PREFIX_ALPHABET.length)];
        }
        byte[] secret = new byte[MerchantApiKeyFormat.SECRET_BYTES];
        random.nextBytes(secret);
        return MerchantApiKeyFormat.MARKER + new String(prefix) + "_"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }
}
