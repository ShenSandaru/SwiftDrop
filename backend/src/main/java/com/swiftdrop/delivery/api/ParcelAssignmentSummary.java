package com.swiftdrop.delivery.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ParcelAssignmentSummary(
        UUID id,
        UUID parcelId,
        UUID courierId,
        ParcelAssignmentType type,
        ParcelAssignmentStatus status,
        Instant assignedAt,
        Instant completedAt,
        UUID dispatcherId
) {
    public ParcelAssignmentSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(courierId, "courierId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(assignedAt, "assignedAt");
    }
}
