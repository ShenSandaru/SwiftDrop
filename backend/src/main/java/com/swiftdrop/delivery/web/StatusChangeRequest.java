package com.swiftdrop.delivery.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.application.StatusChange;

/**
 * Courier status update (COU-3, COU-4, contract §4.3). Unknown status or reason names are a 400.
 *
 * @param failureReason required for {@code FAILED}
 * @param note optional; stored in history, never sent in events
 */
record StatusChangeRequest(@NotNull ParcelStatus toStatus, FailureReason failureReason,
        @Size(max = 500) String note) {

    StatusChange toChange() {
        return new StatusChange(toStatus, failureReason, note);
    }

    /** Never includes the note. */
    @Override
    public String toString() {
        return "StatusChangeRequest[toStatus=" + toStatus + ", failureReason=" + failureReason + "]";
    }
}
