package com.swiftdrop.delivery.api;

/**
 * Status lifecycle vocabulary of a parcel delivery (D-06, ADR 0003).
 * Vocabulary only; state transition enforcement lives in the delivery domain.
 */
public enum ParcelStatus {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED
}
