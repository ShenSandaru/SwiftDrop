package com.swiftdrop.merchant.api;

import java.util.Optional;

/**
 * Authenticates merchant API keys (ADR 0008). Used by the security chain in {@code config} for
 * {@code /api/v1/merchant-orders/**}.
 */
public interface MerchantAuthentication {

    /**
     * Returns the merchant behind {@code apiKey} if the key is well formed, known, not revoked and
     * its merchant is {@code APPROVED}; empty for every other case, without saying which check failed.
     */
    Optional<AuthenticatedMerchant> authenticate(String apiKey);
}
