package com.swiftdrop.delivery.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Versioned {@code parcel.status-changed} envelope (ADR 0004 §2). Only {@code delivery} creates
 * it: it is written to the outbox with the status change, relayed to RabbitMQ, and published
 * in-process after commit for {@code tracking}.
 *
 * @param eventId unique and stable; consumers deduplicate by it
 */
public record ParcelStatusChanged(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String producer,
        ParcelStatusChangedData data
) {

    public static final String EVENT_TYPE = "parcel.status-changed";
    public static final int SCHEMA_VERSION = 1;
    public static final String PRODUCER = "delivery";

    public ParcelStatusChanged {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(producer, "producer must not be null");
        Objects.requireNonNull(data, "data must not be null");
    }

    public static ParcelStatusChanged of(UUID eventId, Instant occurredAt, ParcelStatusChangedData data) {
        return new ParcelStatusChanged(eventId, EVENT_TYPE, SCHEMA_VERSION, occurredAt, PRODUCER, data);
    }
}
