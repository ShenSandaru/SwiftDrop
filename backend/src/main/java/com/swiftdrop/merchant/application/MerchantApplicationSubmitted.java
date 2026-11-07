package com.swiftdrop.merchant.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Spring application event published upon successful merchant application submission.
 * Serves as an integration hook for future admin notifications.
 */
public record MerchantApplicationSubmitted(
        UUID merchantId,
        UUID userId,
        String businessName,
        String email,
        Instant submittedAt) {

    public MerchantApplicationSubmitted {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(businessName, "businessName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
    }
}

