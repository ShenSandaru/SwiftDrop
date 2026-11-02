package com.swiftdrop.delivery.api;

import java.time.Instant;
import java.util.Objects;

/** One entry of the public status timeline: status and time only, never the actor or notes. */
public record PublicStatusEvent(ParcelStatus status, Instant occurredAt) {

    public PublicStatusEvent {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
