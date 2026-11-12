package com.swiftdrop.merchant.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Public lookup facade for resolving merchant identity and checking approval status (MER-1, docs/merchant-contracts.md).
 * Used by Member B for machine API credentials and webhook delivery.
 */
public interface MerchantLookup {

    /**
     * Looks up a merchant profile by merchant ID.
     *
     * @param merchantId the merchant ID
     * @return the merchant view if found, or empty
     */
    Optional<MerchantView> findById(UUID merchantId);

    /**
     * Looks up a merchant profile by user ID.
     *
     * @param userId the user ID of the merchant user
     * @return the merchant view if found, or empty
     */
    Optional<MerchantView> findByUserId(UUID userId);

    /**
     * Checks if the merchant is in {@link MerchantStatus#APPROVED} state.
     *
     * @param merchantId the merchant ID
     * @return true if the merchant exists and is approved
     */
    boolean isApproved(UUID merchantId);
}

