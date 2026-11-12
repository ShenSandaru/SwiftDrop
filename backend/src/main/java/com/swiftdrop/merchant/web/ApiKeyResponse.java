package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.merchant.domain.MerchantApiKey;

/** An API key as listed to its merchant: never the hash or the secret. */
public record ApiKeyResponse(UUID id, String label, String keyPrefix, Instant createdAt, Instant lastUsedAt,
        Instant revokedAt, boolean active) {

    static ApiKeyResponse from(MerchantApiKey key) {
        return new ApiKeyResponse(key.getId(), key.getLabel(), key.getKeyPrefix(), key.getCreatedAt(),
                key.getLastUsedAt(), key.getRevokedAt(), key.isActive());
    }
}
