package com.swiftdrop.delivery.api;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Optional filters for the dispatch activity history query. The upper time bound is exclusive. */
public record DispatchActivityFilter(
        Instant from,
        Instant to,
        DispatchActivityAction actionType,
        String trackingNumber,
        UUID dispatcherId
) {
    public DispatchActivityFilter {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be earlier than to");
        }
        trackingNumber = trackingNumber == null || trackingNumber.isBlank()
                ? null
                : trackingNumber.trim().toUpperCase(Locale.ROOT);
    }
}
