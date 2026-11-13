package com.swiftdrop.delivery.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelAssignmentStatus;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelHubOperations;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.Staff;
import com.swiftdrop.delivery.domain.Hub;
import com.swiftdrop.delivery.domain.HubRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAssignmentRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;

/**
 * The dispatcher's steps in the hub network (ADR 0009 §3, §6), and cancellation before pickup by
 * the owning customer. Like courier steps, each one locks the parcel and records history and an
 * outbox event in one transaction (ADR 0006 §8).
 */
@Service
public class ParcelHubService implements ParcelHubOperations {

    private static final Logger log = LoggerFactory.getLogger(ParcelHubService.class);

    private final ParcelRepository parcels;
    private final HubRepository hubs;
    private final ParcelAssignmentRepository assignments;
    private final ParcelEventRecorder recorder;
    private final DeliveryPolicy policy;
    private final Clock clock;

    @Autowired
    public ParcelHubService(ParcelRepository parcels, HubRepository hubs, ParcelAssignmentRepository assignments,
            ParcelEventRecorder recorder, DeliveryPolicy policy) {
        this(parcels, hubs, assignments, recorder, policy, Clock.systemUTC());
    }

    ParcelHubService(ParcelRepository parcels, HubRepository hubs, ParcelAssignmentRepository assignments,
            ParcelEventRecorder recorder, DeliveryPolicy policy, Clock clock) {
        this.parcels = parcels;
        this.hubs = hubs;
        this.assignments = assignments;
        this.recorder = recorder;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void scanAtHub(UUID parcelId, UUID hubId, Staff staff) {
        Objects.requireNonNull(hubId, "hubId");
        Hub hub = hubs.findById(hubId).orElseThrow(() -> new ParcelConflictException("Unknown hub"));
        Parcel parcel = lock(parcelId);
        ParcelStatus previous = parcel.getStatus();
        Instant now = clock.instant();
        parcel.scanAtHub(hub.getId(), now);
        record(parcel, previous, staff, null, null, now, hub.getCode());
    }

    @Override
    @Transactional
    public void sendToNextHub(UUID parcelId, Staff staff) {
        Parcel parcel = lock(parcelId);
        ParcelStatus previous = parcel.getStatus();
        UUID leaving = parcel.getCurrentHubId();
        Instant now = clock.instant();
        parcel.sendToNextHub(now);
        record(parcel, previous, staff, null, null, now, hubCode(leaving));
    }

    @Override
    @Transactional
    public void startReturn(UUID parcelId, Staff staff) {
        Parcel parcel = lock(parcelId);
        ParcelStatus previous = parcel.getStatus();
        Instant now = clock.instant();
        parcel.startReturn(policy.maxDeliveryAttempts(), now);
        record(parcel, previous, staff, null, null, now, null);
    }

    @Override
    @Transactional
    public void markFailed(UUID parcelId, FailureReason reason, String note, Staff staff) {
        Parcel parcel = lock(parcelId);
        ParcelStatus previous = parcel.getStatus();
        Instant now = clock.instant();
        parcel.markFailed(reason, now);
        cancelActiveTask(parcel, now);
        record(parcel, previous, staff, note, reason, now, null);
    }

    @Override
    @Transactional
    public void cancel(UUID parcelId, Staff staff) {
        cancel(lock(parcelId), Actor.staff(Objects.requireNonNull(staff, "staff")));
    }

    /**
     * Cancellation by the customer who booked the parcel (ADR 0009 §3).
     *
     * @throws ParcelNotFoundException if the parcel does not exist or belongs to someone else
     */
    @Transactional
    public ParcelStatusChanged cancelForCustomer(UUID parcelId, UUID customerId) {
        Objects.requireNonNull(customerId, "customerId");
        Parcel parcel = lock(parcelId);
        if (!customerId.equals(parcel.getCustomerId())) {
            throw new ParcelNotFoundException();
        }
        return cancel(parcel, Actor.customer(customerId));
    }

    private ParcelStatusChanged cancel(Parcel parcel, Actor actor) {
        ParcelStatus previous = parcel.getStatus();
        Instant now = clock.instant();
        parcel.cancel(now);
        cancelActiveTask(parcel, now);
        // IDs only: no personal data in logs
        log.info("Parcel {} cancelled by {} {}", parcel.getId(), actor.role(), actor.userId());
        return recorder.record(parcel, previous, actor, null, null, now);
    }

    private void cancelActiveTask(Parcel parcel, Instant now) {
        assignments.findFirstByParcelIdAndStatus(parcel.getId(), ParcelAssignmentStatus.ACTIVE)
                .ifPresent(task -> task.cancel(now));
    }

    private Parcel lock(UUID parcelId) {
        Objects.requireNonNull(parcelId, "parcelId");
        return parcels.findByIdForUpdate(parcelId).orElseThrow(ParcelNotFoundException::new);
    }

    private String hubCode(UUID hubId) {
        return hubId == null ? null : hubs.findById(hubId).map(Hub::getCode).orElse(null);
    }

    private void record(Parcel parcel, ParcelStatus previous, Staff staff, String note, FailureReason reason,
            Instant now, String hubCode) {
        Objects.requireNonNull(staff, "staff");
        recorder.record(parcel, previous, Actor.staff(staff), note, reason, now, hubCode, null);
    }
}
