package com.swiftdrop.merchant.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A merchant API key (ADR 0008). Holds only the public prefix and the SHA-256 hash; the full key is
 * shown once when created and never stored.
 */
@Entity
@Table(name = "merchant_api_keys")
public class MerchantApiKey {

    public static final int MAX_LABEL_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "key_prefix", nullable = false, updatable = false, length = 12)
    private String keyPrefix;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "key_hash", nullable = false, updatable = false, length = 64)
    private String keyHash;

    @Column(name = "label", nullable = false, length = MAX_LABEL_LENGTH)
    private String label;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected MerchantApiKey() {
    }

    public MerchantApiKey(UUID merchantId, String keyPrefix, String keyHash, String label, UUID createdByUserId,
            Instant createdAt) {
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
        this.keyHash = Objects.requireNonNull(keyHash, "keyHash");
        this.label = requireLabel(label);
        this.createdByUserId = Objects.requireNonNull(createdByUserId, "createdByUserId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Revokes the key; revoking an already revoked key keeps the first revocation time. */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = Objects.requireNonNull(now, "now");
        }
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    private static String requireLabel(String label) {
        String trimmed = label == null ? "" : label.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("Label must be 1 to " + MAX_LABEL_LENGTH + " characters");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getLabel() {
        return label;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    /** Never includes the hash. */
    @Override
    public String toString() {
        return "MerchantApiKey[id=" + id + ", merchantId=" + merchantId + ", keyPrefix=" + keyPrefix + "]";
    }
}
