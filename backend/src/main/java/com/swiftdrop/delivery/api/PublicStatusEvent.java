package com.swiftdrop.delivery.api;

import java.time.Instant;
import java.util.Objects;

/**
 * One entry of the public status timeline: status, time, and for hub steps the hub's name and for
 * delivery steps the attempt number (ADR 0009 §7). Never the actor or notes.
 *
 * @param hubName the hub reached or left, or {@code null}
 * @param attempt the delivery attempt, or {@code null}
 */
public record PublicStatusEvent(ParcelStatus status, Instant occurredAt, String hubName, Integer attempt) {

    public PublicStatusEvent {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public PublicStatusEvent(ParcelStatus status, Instant occurredAt) {
        this(status, occurredAt, null, null);
    }
}
