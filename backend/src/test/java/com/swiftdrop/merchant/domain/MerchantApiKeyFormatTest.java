package com.swiftdrop.merchant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class MerchantApiKeyFormatTest {

    static final String KEY = "sdk_k7m2q9xw4ht1_" + "A".repeat(40) + "_-9";

    @Test
    @DisplayName("A 60-character sdk_<prefix>_<base64url secret> key is well formed")
    void wellFormed() {
        assertThat(KEY).hasSize(MerchantApiKeyFormat.KEY_LENGTH);
        assertThat(MerchantApiKeyFormat.isWellFormed(KEY)).isTrue();
        assertThat(MerchantApiKeyFormat.prefixOf(KEY)).isEqualTo("k7m2q9xw4ht1");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @MethodSource("malformedKeys")
    @DisplayName("Anything else is malformed")
    void malformed(String key) {
        assertThat(MerchantApiKeyFormat.isWellFormed(key)).isFalse();
    }

    static Stream<String> malformedKeys() {
        String secret = "A".repeat(43);
        return Stream.of(
                "sdk_k7m2q9xw4ht1_" + "A".repeat(42),           // secret one short
                "sdk_k7m2q9xw4ht1_" + "A".repeat(44),           // secret one long
                "sdk_K7M2Q9XW4HT1_" + secret,                   // uppercase prefix
                "sdk_k7m2q9xw4h_1_" + secret,                   // underscore in prefix
                "key_k7m2q9xw4ht1_" + secret,                   // wrong marker
                "sdk_k7m2q9xw4ht1_" + "A".repeat(42) + "=",     // padding
                "sdk_k7m2q9xw4ht1_" + "A".repeat(42) + "+",     // standard, not URL-safe, base64
                "sdk_k7m2q9xw4ht1-" + secret,                   // wrong separator
                " sdk_k7m2q9xw4ht1_" + "A".repeat(42),          // leading space, same length
                "Bearer sdk_k7m2q9xw4ht1_" + secret);
    }

    @Test
    @DisplayName("The hash is the lowercase hex SHA-256 of the full key")
    void hash() {
        // SHA-256("abc"), FIPS 180-2 test vector
        assertThat(MerchantApiKeyFormat.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(MerchantApiKeyFormat.hash(KEY)).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("matches accepts the right key and rejects any other key with the same prefix")
    void matches() {
        String stored = MerchantApiKeyFormat.hash(KEY);
        String otherSecret = KEY.substring(0, KEY.length() - 1) + "8";

        assertThat(MerchantApiKeyFormat.matches(KEY, stored)).isTrue();
        assertThat(MerchantApiKeyFormat.matches(otherSecret, stored)).isFalse();
    }
}
