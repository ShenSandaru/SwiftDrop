package com.swiftdrop.tracking.application;

import java.time.Instant;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;

/**
 * Payload of a live {@code status} event (ADR 0007): the new status, when, and its sequence so
 * clients can ignore updates already contained in their snapshot. Nothing else.
 */
public record StatusUpdate(ParcelStatus status, Instant occurredAt, int sequence) {

    static StatusUpdate from(ParcelStatusChanged event) {
        return new StatusUpdate(event.data().newStatus(), event.occurredAt(), event.data().sequence());
    }
}
