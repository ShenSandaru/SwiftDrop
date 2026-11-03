package com.swiftdrop.delivery.application;

import java.util.Objects;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * A requested status change (COU-3, COU-4).
 *
 * @param failureReason required for {@code FAILED}
 * @param note optional courier note, at most 500 characters; personal data, stays in history
 */
public record StatusChange(ParcelStatus toStatus, FailureReason failureReason, String note) {

    public StatusChange {
        Objects.requireNonNull(toStatus, "toStatus");
    }

    /** Never includes the note. */
    @Override
    public String toString() {
        return "StatusChange[toStatus=" + toStatus + ", failureReason=" + failureReason + "]";
    }
}
