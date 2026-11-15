package com.swiftdrop.delivery.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Spring application event published when a courier is assigned (or reassigned)
 * to a parcel task
 * (ADR 0003, ADR 0004 §3). Emitted within the assignment transaction so
 * listeners can process it
 * after commit.
 *
 * @param eventId           unique identifier for this assignment occurrence (used
 *                          for idempotency)
 * @param parcelId          the parcel being assigned
 * @param trackingNumber    the human-readable tracking number
 * @param courierId         the assigned courier user ID
 * @param dispatcherId      the dispatcher or admin who made the assignment
 * @param assignmentType    the type of assignment task (PICKUP, DELIVERY, RETURN)
 * @param occurredAt        the timestamp when the assignment was recorded
 * @param previousCourierId the previous courier user ID if this was a reassignment, or {@code null}
 */
public record ParcelCourierAssigned(
        UUID eventId,
        UUID parcelId,
        String trackingNumber,
        UUID courierId,
        UUID dispatcherId,
        ParcelAssignmentType assignmentType,
        Instant occurredAt,
        UUID previousCourierId) {

    public ParcelCourierAssigned(
            UUID eventId,
            UUID parcelId,
            String trackingNumber,
            UUID courierId,
            UUID dispatcherId,
            ParcelAssignmentType assignmentType,
            Instant occurredAt) {
        this(eventId, parcelId, trackingNumber, courierId, dispatcherId, assignmentType, occurredAt, null);
    }

    public ParcelCourierAssigned {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(parcelId, "parcelId must not be null");
        Objects.requireNonNull(trackingNumber, "trackingNumber must not be null");
        Objects.requireNonNull(courierId, "courierId must not be null");
        Objects.requireNonNull(dispatcherId, "dispatcherId must not be null");
        Objects.requireNonNull(assignmentType, "assignmentType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
