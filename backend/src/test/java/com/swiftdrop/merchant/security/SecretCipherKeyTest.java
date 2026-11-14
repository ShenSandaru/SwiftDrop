package com.swiftdrop.merchant.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The webhook secret encryption key has no default (IMP-6). */
class SecretCipherKeyTest {

    @Test
    @DisplayName("Startup fails without a key or with one shorter than 32 bytes")
    void missingOrShortKey() {
        for (String key : new String[] {null, "", "   ", "only-31-bytes-long-key-12345678"}) {
            assertThatThrownBy(() -> new SecretCipher(key))
                    .as(String.valueOf(key))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SWIFTDROP_WEBHOOK_SECRET_KEY");
        }
    }
}
