package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.swiftdrop.merchant.api.MerchantStatus;

/**
 * Detailed view of a merchant profile including full approval audit history (Phase 3).
 */
public record MerchantDetailResponse(
        UUID id,
        UUID userId,
        String businessName,
        String businessType,
        String businessRegistrationNo,
        String contactPhone,
        String businessAddress,
        String businessDescription,
        String websiteUrl,
        MerchantStatus status,
        String statusReason,
        UUID decidedBy,
        Instant decidedAt,
        Instant createdAt,
        Instant updatedAt,
        long version,
        List<StatusHistoryItem> statusHistory
) {
    public record StatusHistoryItem(
            UUID id,
            UUID merchantId,
            MerchantStatus fromStatus,
            MerchantStatus toStatus,
            UUID actorUserId,
            String reason,
            Instant createdAt
    ) {}
}

