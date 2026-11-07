package com.swiftdrop.merchant.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload submitted by an administrator to approve or reject a merchant application.
 */
public record MerchantApprovalRequest(
        @NotNull(message = "Decision is required")
        Decision decision,

        @Size(max = 500, message = "Reason must be at most 500 characters")
        String reason) {

    public enum Decision {
        APPROVE,
        REJECT
    }
}

