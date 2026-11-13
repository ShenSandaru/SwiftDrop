package com.swiftdrop.delivery.api;

import java.util.UUID;

/** Non-sensitive context for one dispatcher activity. */
public record DispatchActivityDetails(
        ParcelAssignmentType assignmentType,
        UUID courierId,
        ParcelStatus fromStatus,
        ParcelStatus toStatus,
        String hubCode,
        Integer attempt,
        FailureReason failureReason
) {
}
