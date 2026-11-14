package com.swiftdrop.merchant.web;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for merchant profile updates and resubmissions (Phase 3).
 * If the merchant's status is REJECTED, updating the profile automatically resubmits it to PENDING.
 */
public record MerchantProfileUpdateRequest(
        @Size(min = 2, max = 150, message = "businessName must be between 2 and 150 characters")
        String businessName,

        @Size(max = 50, message = "businessType must be at most 50 characters")
        String businessType,

        @Pattern(regexp = "^(\\+94|0)[0-9]{9}$", message = "contactPhone must be a valid Sri Lankan phone number (+94... or 07...)")
        String contactPhone,

        @Size(min = 5, max = 500, message = "businessAddress must be between 5 and 500 characters")
        String businessAddress,

        @Size(max = 500, message = "businessDescription must be at most 500 characters")
        String businessDescription,

        @Size(max = 2048, message = "websiteUrl must be at most 2048 characters")
        String websiteUrl
) {}

