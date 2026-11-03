package com.swiftdrop.delivery.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Parcel status history; entries are only ever added. */
public interface ParcelStatusHistoryRepository extends JpaRepository<ParcelStatusHistoryEntry, UUID> {

    List<ParcelStatusHistoryEntry> findByParcelIdOrderBySequenceAsc(UUID parcelId);

    List<ParcelStatusHistoryEntry> findByParcelIdAndSequenceBetweenOrderBySequenceAsc(
            UUID parcelId, int fromSequence, int toSequence);
}
