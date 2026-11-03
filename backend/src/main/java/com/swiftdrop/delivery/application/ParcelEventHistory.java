package com.swiftdrop.delivery.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.ParcelEvents;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;

/**
 * {@link ParcelEvents} for consumers repairing a gap in received sequence numbers (ADR 0004 §4.3).
 * Builds each event with {@link ParcelEventRecorder#eventFor}, the code that built the original
 * outbox event, so the rebuilt event has the same ID and data.
 */
@Service
@Transactional(readOnly = true)
class ParcelEventHistory implements ParcelEvents {

    private final ParcelRepository parcels;
    private final ParcelStatusHistoryRepository history;

    ParcelEventHistory(ParcelRepository parcels, ParcelStatusHistoryRepository history) {
        this.parcels = parcels;
        this.history = history;
    }

    @Override
    public List<ParcelStatusChanged> eventsBetween(UUID parcelId, int fromSequence, int toSequence) {
        Objects.requireNonNull(parcelId, "parcelId");
        if (fromSequence < 1 || toSequence < fromSequence) {
            throw new IllegalArgumentException("Sequences must satisfy 1 <= fromSequence <= toSequence");
        }
        Parcel parcel = parcels.findById(parcelId).orElseThrow(ParcelNotFoundException::new);
        return history.findByParcelIdAndSequenceBetweenOrderBySequenceAsc(parcelId, fromSequence, toSequence).stream()
                .map(entry -> ParcelEventRecorder.eventFor(parcel, entry))
                .toList();
    }
}
