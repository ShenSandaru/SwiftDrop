package com.swiftdrop.delivery.api;

import java.util.UUID;

/**
 * Operations a dispatcher or admin performs on a parcel in the hub network (ADR 0009 §3, §6),
 * called by {@code dispatch}. Each one is a status change: it locks the parcel, checks the
 * lifecycle rules, and writes history and an outbox event in one transaction (ADR 0006 §8).
 *
 * <p>Every method throws {@link ParcelNotFoundException} for an unknown parcel and
 * {@link ParcelConflictException} when the parcel's status or route does not allow the operation.
 */
public interface ParcelHubOperations {

    /**
     * Scans the parcel in at a hub: the origin hub after pickup, the next hub after a line-haul
     * leg, or the destination hub after a failed delivery attempt.
     */
    void scanAtHub(UUID parcelId, UUID hubId, Staff staff);

    /** Sends the parcel from its current hub to the next hub of its route. */
    void sendToNextHub(UUID parcelId, Staff staff);

    /**
     * Starts the return to the sender, at the destination hub after a failed attempt, once the
     * attempt limit is reached or the last reason was a refusal or an unknown address.
     */
    void startReturn(UUID parcelId, Staff staff);

    /**
     * Ends the parcel as {@code FAILED} (lost, damaged beyond use). Not possible before pickup;
     * cancel instead.
     *
     * @param note optional, at most 500 characters; stays in history, never in events
     */
    void markFailed(UUID parcelId, FailureReason reason, String note, Staff staff);

    /** Cancels a parcel that has not been picked up yet. */
    void cancel(UUID parcelId, Staff staff);
}
