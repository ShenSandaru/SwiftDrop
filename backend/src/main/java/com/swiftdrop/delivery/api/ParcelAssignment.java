package com.swiftdrop.delivery.api;

import java.util.UUID;

/**
 * Records courier assignments decided by {@code dispatch} (ADM-1). {@code dispatch} checks that
 * the courier is eligible; {@code delivery} enforces the lifecycle rules (ADR 0003, ADR 0006 §3).
 */
public interface ParcelAssignment {

    /**
     * Assigns or reassigns a courier. Allowed only while the parcel is
     * {@link ParcelStatus#CREATED}. Not a status change, so no event is emitted.
     *
     * @throws ParcelNotFoundException if the parcel does not exist
     * @throws ParcelConflictException if the parcel is past {@code CREATED}
     */
    void assignCourier(UUID parcelId, UUID courierUserId, UUID dispatcherUserId);

    /**
     * Assigns a courier to a pickup or delivery task. Pickup assignment is limited to {@code CREATED};
     * delivery assignment is limited to {@code PICKED_UP} parcels with no active courier.
     *
     * @return the persisted assignment record
     */
    ParcelAssignmentSummary assignCourier(UUID parcelId, UUID courierUserId, UUID dispatcherUserId,
            ParcelAssignmentType type);
}
