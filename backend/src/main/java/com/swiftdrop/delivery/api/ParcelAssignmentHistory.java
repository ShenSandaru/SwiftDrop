package com.swiftdrop.delivery.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ParcelAssignmentHistory(UUID parcelId, List<ParcelAssignmentSummary> assignments) {
    public ParcelAssignmentHistory {
        Objects.requireNonNull(parcelId, "parcelId");
        assignments = assignments == null ? List.of() : List.copyOf(assignments);
    }
}
