package com.swiftdrop.delivery.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Read-only activity projection owned by delivery and consumed through {@code delivery.api}. */
public record DispatchActivity(
        String id,
        String trackingNumber,
        Instant occurredAt,
        DispatchActivityAction actionType,
        UUID actorId,
        DispatchActivityDetails details
) {
    public DispatchActivity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(details, "details");
    }
}
