package com.swiftdrop.merchant.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Spring application event published when a merchant's status changes (docs/merchant-contracts.md).
 * Consumed by Member B to revoke/disable machine API keys when status is SUSPENDED or REJECTED.
 */
public record MerchantStatusChanged(
        UUID eventId,
        UUID merchantId,
        UUID userId,
        MerchantStatus fromStatus,
        MerchantStatus toStatus,
        UUID actorUserId,
        String reason,
        Instant occurredAt) {

    public MerchantStatusChanged {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(toStatus, "toStatus must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * Backward-compatible constructor that generates a random eventId.
     */
    public MerchantStatusChanged(
            UUID merchantId,
            UUID userId,
            MerchantStatus fromStatus,
            MerchantStatus toStatus,
            UUID actorUserId,
            String reason,
            Instant occurredAt) {
        this(UUID.randomUUID(), merchantId, userId, fromStatus, toStatus, actorUserId, reason, occurredAt);
    }

    /**
     * Returns true if this transition suspends or rejects the merchant.
     */
    public boolean isRevocation() {
        return toStatus == MerchantStatus.SUSPENDED || toStatus == MerchantStatus.REJECTED;
    }
}

