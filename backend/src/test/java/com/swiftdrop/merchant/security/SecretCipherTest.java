package com.swiftdrop.merchant.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

    private SecretCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = new SecretCipher("test-master-encryption-key-32-chars-long!");
    }

    @Test
    @DisplayName("Encrypt and decrypt round trip succeeds")
    void encryptAndDecryptRoundTrip() {
        String originalSecret = "whsec_test_secret_value_1234567890abcdef";

        SecretCipher.EncryptedSecret encrypted = cipher.encrypt(originalSecret);

        assertThat(encrypted.ciphertext()).isNotBlank();
        assertThat(encrypted.iv()).isNotBlank();
        assertThat(encrypted.keyVersion()).isEqualTo(SecretCipher.CURRENT_KEY_VERSION);
        assertThat(encrypted.hint()).isEqualTo("cdef");
        assertThat(encrypted.ciphertext()).isNotEqualTo(originalSecret);

        String decrypted = cipher.decrypt(encrypted.ciphertext(), encrypted.iv(), encrypted.keyVersion());
        assertThat(decrypted).isEqualTo(originalSecret);
    }

    @Test
    @DisplayName("Subsequent encryptions use unique IVs and produce distinct ciphertexts")
    void encryptionProducesUniqueIVs() {
        String secret = "whsec_constant_secret_string";

        SecretCipher.EncryptedSecret enc1 = cipher.encrypt(secret);
        SecretCipher.EncryptedSecret enc2 = cipher.encrypt(secret);

        assertThat(enc1.iv()).isNotEqualTo(enc2.iv());
        assertThat(enc1.ciphertext()).isNotEqualTo(enc2.ciphertext());

        assertThat(cipher.decrypt(enc1.ciphertext(), enc1.iv(), enc1.keyVersion())).isEqualTo(secret);
        assertThat(cipher.decrypt(enc2.ciphertext(), enc2.iv(), enc2.keyVersion())).isEqualTo(secret);
    }

    @Test
    @DisplayName("Decryption with wrong key version throws IllegalArgumentException")
    void decryptWithWrongKeyVersionThrows() {
        SecretCipher.EncryptedSecret encrypted = cipher.encrypt("whsec_secret");

        assertThatThrownBy(() -> cipher.decrypt(encrypted.ciphertext(), encrypted.iv(), 99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported secret key version");
    }

    @Test
    @DisplayName("Decryption with tampered ciphertext fails")
    void decryptWithTamperedCiphertextThrows() {
        SecretCipher.EncryptedSecret encrypted = cipher.encrypt("whsec_secret");
        String tamperedCiphertext = encrypted.ciphertext().substring(0, encrypted.ciphertext().length() - 4) + "AAAA";

        assertThatThrownBy(() -> cipher.decrypt(tamperedCiphertext, encrypted.iv(), encrypted.keyVersion()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to decrypt webhook secret");
    }

    @Test
    @DisplayName("extractHint extracts last 4 characters or fallback")
    void extractHint() {
        assertThat(SecretCipher.extractHint("whsec_123456")).isEqualTo("3456");
        assertThat(SecretCipher.extractHint("abcd")).isEqualTo("abcd");
        assertThat(SecretCipher.extractHint("ab")).isEqualTo("....");
        assertThat(SecretCipher.extractHint(null)).isEqualTo("....");
    }
}

