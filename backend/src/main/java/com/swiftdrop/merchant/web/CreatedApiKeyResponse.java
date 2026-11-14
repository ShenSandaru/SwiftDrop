package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.merchant.application.MerchantApiKeyService.IssuedApiKey;

/**
 * Response to creating a key. {@code apiKey} is the full key: it is shown only in this response and
 * cannot be retrieved again.
 */
public record CreatedApiKeyResponse(UUID id, String label, String keyPrefix, String apiKey, Instant createdAt) {

    static CreatedApiKeyResponse from(IssuedApiKey issued) {
        return new CreatedApiKeyResponse(issued.key().getId(), issued.key().getLabel(), issued.key().getKeyPrefix(),
                issued.apiKey(), issued.key().getCreatedAt());
    }

    /** Never includes the full key. */
    @Override
    public String toString() {
        return "CreatedApiKeyResponse[id=" + id + ", keyPrefix=" + keyPrefix + "]";
    }
}
