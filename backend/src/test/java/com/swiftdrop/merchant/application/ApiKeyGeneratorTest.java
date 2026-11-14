package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.swiftdrop.merchant.domain.MerchantApiKeyFormat;

class ApiKeyGeneratorTest {

    private final ApiKeyGenerator generator = new ApiKeyGenerator();

    @Test
    @DisplayName("Generated keys follow the ADR 0008 format with a 32-byte secret")
    void format() {
        String key = generator.generate();

        assertThat(MerchantApiKeyFormat.isWellFormed(key)).isTrue();
        assertThat(key).startsWith("sdk_").hasSize(60);
        String secret = key.substring("sdk_".length() + MerchantApiKeyFormat.PREFIX_LENGTH + 1);
        assertThat(Base64.getUrlDecoder().decode(secret)).hasSize(32);
    }

    @Test
    @DisplayName("Every call produces a different key and prefix")
    void unique() {
        Set<String> keys = new HashSet<>();
        Set<String> prefixes = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String key = generator.generate();
            keys.add(key);
            prefixes.add(MerchantApiKeyFormat.prefixOf(key));
        }
        assertThat(keys).hasSize(1_000);
        assertThat(prefixes).hasSize(1_000);
    }
}
