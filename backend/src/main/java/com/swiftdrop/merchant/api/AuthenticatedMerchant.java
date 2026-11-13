package com.swiftdrop.merchant.api;

import java.util.Objects;
import java.util.UUID;

/**
 * The principal of a request authenticated with a merchant API key (ADR 0008 §5). It carries only the
 * merchant and the key, never a user or role; endpoints take the merchant ID from here, never from the
 * request body.
 *
 * @param merchantId the merchant that owns the key
 * @param apiKeyId   the key used
 * @param keyPrefix  the key's public prefix, safe to log
 */
public record AuthenticatedMerchant(UUID merchantId, UUID apiKeyId, String keyPrefix) {

    /** The authority granted to API-key requests. */
    public static final String AUTHORITY = "MERCHANT_API_KEY";

    public AuthenticatedMerchant {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(apiKeyId, "apiKeyId");
        Objects.requireNonNull(keyPrefix, "keyPrefix");
    }
}
