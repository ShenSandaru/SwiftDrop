package com.swiftdrop.delivery.application;

import java.util.List;
import java.util.Objects;

import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryEntry;

/**
 * A parcel with its timeline, as one viewer may see it.
 *
 * @param notesVisible whether the viewer may see courier notes in the history
 * @param recipientName from the proof of delivery; set only on receipts of delivered parcels
 */
public record ParcelView(Parcel parcel, List<ParcelStatusHistoryEntry> history, boolean notesVisible,
        String recipientName) {

    public ParcelView {
        Objects.requireNonNull(parcel, "parcel");
        history = history == null ? List.of() : List.copyOf(history);
    }

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "ParcelView[parcel=" + parcel + ", historyEntries=" + history.size() + "]";
    }
}
