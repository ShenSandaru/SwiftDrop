package com.swiftdrop.dispatch.api;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.delivery.api.DispatchActivityAction;
import com.swiftdrop.delivery.api.DispatchActivityDetails;

/** Activity history item enriched with a display-safe actor name by the dispatch module. */
public record DispatchActivityItem(
        String id,
        String trackingNumber,
        Instant occurredAt,
        DispatchActivityAction actionType,
        UUID actorId,
        String actorName,
        DispatchActivityDetails details
) {
}
