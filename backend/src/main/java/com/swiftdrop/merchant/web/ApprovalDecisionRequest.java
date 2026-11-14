package com.swiftdrop.merchant.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for administrator approval decisions (Phase 3).
 * Reason is required when decision is REJECT or SUSPEND.
 */
public record ApprovalDecisionRequest(
        @NotNull(message = "decision is required")
        Decision decision,

        @Size(max = 500, message = "reason must not exceed 500 characters")
        String reason
) {
    public enum Decision {
        APPROVE,
        REJECT,
        SUSPEND
    }
}

