package com.swiftdrop.dispatch.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.swiftdrop.delivery.api.FailureReason;

/**
 * Ends a parcel as lost or damaged (ADR 0009 §3).
 *
 * @param note optional; personal data may appear, so it stays in history and never reaches events
 */
record ParcelFailureRequest(@NotNull FailureReason reason, @Size(max = 500) String note) {

    /** Never includes the note. */
    @Override
    public String toString() {
        return "ParcelFailureRequest[reason=" + reason + "]";
    }
}
