package com.swiftdrop.delivery.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Spring application event published when an active courier assignment is cancelled
 * (ADR 0003, ADR 0004 §3). Emitted within the cancellation transaction so listeners can
 * process it after commit.
 *
 * @param eventId        unique identifier for this cancellation occurrence (used for idempotency)
 * @param parcelId       the parcel whose assignment was cancelled
 * @param trackingNumber the human-readable tracking number
 * @param courierId      the affected courier user ID
 * @param assignmentType the type of assignment task (PICKUP, DELIVERY, RETURN)
 * @param occurredAt     the timestamp when the cancellation was recorded
 */
public record ParcelCourierAssignmentCancelled(
        UUID eventId,
        UUID parcelId,
        String trackingNumber,
        UUID courierId,
        ParcelAssignmentType assignmentType,
        Instant occurredAt) {

    public ParcelCourierAssignmentCancelled {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(parcelId, "parcelId must not be null");
        Objects.requireNonNull(trackingNumber, "trackingNumber must not be null");
        Objects.requireNonNull(courierId, "courierId must not be null");
        Objects.requireNonNull(assignmentType, "assignmentType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}

