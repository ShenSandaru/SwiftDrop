package com.swiftdrop.delivery.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryEntry;

/**
 * A parcel with its timeline, as one viewer may see it.
 *
 * @param notesVisible whether the viewer may see courier notes in the history
 * @param recipientName from the proof; set only on receipts of delivered or returned parcels
 * @param hubNames names of the hubs in the history, by code (ADR 0009 §7)
 */
public record ParcelView(Parcel parcel, List<ParcelStatusHistoryEntry> history, boolean notesVisible,
        String recipientName, Map<String, String> hubNames) {

    public ParcelView {
        Objects.requireNonNull(parcel, "parcel");
        history = history == null ? List.of() : List.copyOf(history);
        hubNames = hubNames == null ? Map.of() : Map.copyOf(hubNames);
    }

    public ParcelView(Parcel parcel, List<ParcelStatusHistoryEntry> history, boolean notesVisible,
            String recipientName) {
        this(parcel, history, notesVisible, recipientName, Map.of());
    }

    /** The hub's name, or its code if unknown; {@code null} for {@code null}. */
    public String hubName(String hubCode) {
        return hubCode == null ? null : hubNames.getOrDefault(hubCode, hubCode);
    }

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "ParcelView[parcel=" + parcel + ", historyEntries=" + history.size() + "]";
    }
}
