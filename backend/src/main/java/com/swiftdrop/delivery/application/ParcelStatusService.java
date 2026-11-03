package com.swiftdrop.delivery.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.domain.ActorRole;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelRepository;

/**
 * Courier status changes (COU-3, COU-4) under the rules of ADR 0006: lock the parcel, check the
 * rule, update it, and record history and outbox event, all in one transaction.
 */
@Service
public class ParcelStatusService {

    private final ParcelRepository parcels;
    private final DeliveryProofRepository proofs;
    private final ParcelEventRecorder recorder;
    private final Clock clock;

    @Autowired
    public ParcelStatusService(ParcelRepository parcels, DeliveryProofRepository proofs, ParcelEventRecorder recorder) {
        this(parcels, proofs, recorder, Clock.systemUTC());
    }

    ParcelStatusService(ParcelRepository parcels, DeliveryProofRepository proofs, ParcelEventRecorder recorder,
            Clock clock) {
        this.parcels = parcels;
        this.proofs = proofs;
        this.recorder = recorder;
        this.clock = clock;
    }

    /**
     * Applies a status change by the parcel's assigned courier. In v1 nobody else changes status
     * after creation (ADR 0006 §2).
     *
     * @throws ParcelNotFoundException if the parcel does not exist or the courier is not assigned to it
     * @throws com.swiftdrop.delivery.api.ParcelConflictException if the change breaks a lifecycle rule
     * @throws IllegalArgumentException if the request itself is invalid (reason for a non-failure,
     *         note too long)
     */
    @Transactional
    public ParcelStatusChanged changeStatus(UUID parcelId, StatusChange change, Actor courier) {
        Objects.requireNonNull(change, "change");
        if (courier.role() != ActorRole.COURIER) {
            throw new IllegalArgumentException("Only couriers change a parcel's status");
        }
        Parcel parcel = parcels.findByIdForUpdate(parcelId).orElseThrow(ParcelNotFoundException::new);
        ParcelStatus previous = parcel.getStatus();
        // Checked in this transaction, under the parcel lock (ADR 0006 §5)
        boolean proofCaptured = change.toStatus() == ParcelStatus.DELIVERED && proofs.existsByParcelId(parcelId);
        Instant now = clock.instant();

        parcel.changeStatus(change.toStatus(), courier.userId(), change.failureReason(), proofCaptured, now);
        return recorder.record(parcel, previous, courier, change.note(), change.failureReason(), now);
    }
}
