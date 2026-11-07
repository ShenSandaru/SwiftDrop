package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.merchant.api.MerchantStatus;

/**
 * Status response for GET /api/v1/merchants/me and admin views.
 * Decision reason is exposed only for REJECTED or SUSPENDED merchants.
 */
public record MerchantStatusResponse(
        UUID merchantId,
        MerchantStatus status,
        String businessName,
        String businessType,
        String businessRegistrationNo,
        String contactPhone,
        String businessAddress,
        String businessDescription,
        String websiteUrl,
        Instant submittedAt,
        Instant decidedAt,
        String decisionReason) {
}

