package com.swiftdrop.delivery.api;

import java.util.List;
import java.util.UUID;

/**
 * Rebuilds past status events from the parcel history, for consumers that detect a gap in the
 * sequence numbers they received (ADR 0004 §4.3).
 */
public interface ParcelEvents {

    /**
     * Events of one parcel with sequence numbers from {@code fromSequence} to {@code toSequence},
     * both inclusive, in sequence order. Each has the same event ID and data as the original
     * outbox event. Sequences that do not exist yet are simply absent.
     *
     * @throws ParcelNotFoundException if the parcel does not exist
     * @throws IllegalArgumentException if {@code fromSequence < 1} or {@code toSequence < fromSequence}
     */
    List<ParcelStatusChanged> eventsBetween(UUID parcelId, int fromSequence, int toSequence);
}
