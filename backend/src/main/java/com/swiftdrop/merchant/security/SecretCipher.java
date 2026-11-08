package com.swiftdrop.merchant.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cryptographic utility providing authenticated AES-256-GCM encryption and decryption
 * for webhook signing secrets at rest (Phase 4, docs/merchant-contracts.md).
 * <p>
 * Secrets are encrypted with a unique 96-bit IV per encryption and an authenticated 128-bit tag.
 * Key versioning allows future zero-downtime key rotation.
 */
@Component
public class SecretCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12; // 96 bits standard for GCM
    private static final int GCM_TAG_LENGTH_BITS = 128;
    public static final int CURRENT_KEY_VERSION = 1;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec masterKeySpec;

    public SecretCipher(@Value("${swiftdrop.merchant.webhook.secret-key}") String masterKeyString) {
        Objects.requireNonNull(masterKeyString, "swiftdrop.merchant.webhook.secret-key must not be null");
        byte[] rawKeyBytes = masterKeyString.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = sha256(rawKeyBytes);
        this.masterKeySpec = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts a raw webhook signing secret using AES-GCM with a newly generated IV.
     *
     * @param plainSecret the unencrypted secret (e.g. "whsec_...")
     * @return {@link EncryptedSecret} containing Base64 ciphertext, IV, key version, and 4-character hint
     */
    public EncryptedSecret encrypt(String plainSecret) {
        Objects.requireNonNull(plainSecret, "plainSecret must not be null");
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKeySpec, parameterSpec);

            byte[] ciphertextBytes = cipher.doFinal(plainSecret.getBytes(StandardCharsets.UTF_8));

            String ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertextBytes);
            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String hint = extractHint(plainSecret);

            return new EncryptedSecret(ciphertextBase64, ivBase64, CURRENT_KEY_VERSION, hint);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt webhook secret", e);
        }
    }

    /**
     * Decrypts an encrypted webhook signing secret using AES-GCM.
     *
     * @param ciphertextBase64 Base64-encoded ciphertext + tag
     * @param ivBase64         Base64-encoded initialization vector
     * @param keyVersion       the key version used during encryption
     * @return the plain secret string
     */
    public String decrypt(String ciphertextBase64, String ivBase64, int keyVersion) {
        Objects.requireNonNull(ciphertextBase64, "ciphertextBase64 must not be null");
        Objects.requireNonNull(ivBase64, "ivBase64 must not be null");

        if (keyVersion != CURRENT_KEY_VERSION) {
            throw new IllegalArgumentException("Unsupported secret key version: " + keyVersion);
        }

        try {
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKeySpec, parameterSpec);

            byte[] plainBytes = cipher.doFinal(ciphertext);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt webhook secret", e);
        }
    }

    /**
     * Extracts the last 4 characters of the raw secret to serve as a non-sensitive UI display hint.
     */
    public static String extractHint(String secret) {
        if (secret == null || secret.length() < 4) {
            return "....";
        }
        return secret.substring(secret.length() - 4);
    }

    private static byte[] sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record EncryptedSecret(
            String ciphertext,
            String iv,
            int keyVersion,
            String hint
    ) {}
}

