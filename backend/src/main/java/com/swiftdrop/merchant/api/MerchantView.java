package com.swiftdrop.merchant.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Public view of a merchant profile for other modules and components.
 */
public record MerchantView(
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
        Instant createdAt) {

    public MerchantView {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(businessName, "businessName must not be null");
        Objects.requireNonNull(businessType, "businessType must not be null");
        Objects.requireNonNull(businessRegistrationNo, "businessRegistrationNo must not be null");
        Objects.requireNonNull(contactPhone, "contactPhone must not be null");
        Objects.requireNonNull(businessAddress, "businessAddress must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public boolean isApproved() {
        return status == MerchantStatus.APPROVED;
    }
}
