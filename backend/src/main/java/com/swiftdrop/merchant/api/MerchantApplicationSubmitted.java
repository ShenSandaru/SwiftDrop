package com.swiftdrop.merchant.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Spring application event published upon successful merchant application submission (ADR 0003, ADR 0004 §3).
 * Exposed through {@code merchant.api} so the notification module can alert enabled administrators.
 */
public record MerchantApplicationSubmitted(
        UUID eventId,
        UUID merchantId,
        UUID userId,
        String businessName,
        String email,
        Instant submittedAt) {

    public MerchantApplicationSubmitted {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(businessName, "businessName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
    }

    /**
     * Backward-compatible constructor that generates a random {@code eventId}.
     */
    public MerchantApplicationSubmitted(
            UUID merchantId,
            UUID userId,
            String businessName,
            String email,
            Instant submittedAt) {
        this(UUID.randomUUID(), merchantId, userId, businessName, email, submittedAt);
    }
}

