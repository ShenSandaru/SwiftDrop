package com.swiftdrop.delivery.api;

/** Dispatcher-visible parcel activity types. */
public enum DispatchActivityAction {
    PICKUP_ASSIGNED,
    DELIVERY_ASSIGNED,
    RETURN_ASSIGNED,
    COURIER_REASSIGNED,
    HUB_SCAN,
    TRANSIT,
    FAILURE,
    RETURN_INITIATED,
    CANCELLATION
}
