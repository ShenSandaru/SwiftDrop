package com.swiftdrop.delivery.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.ParcelAssignmentStatus;
import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.domain.ActorRole;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.HubRoute;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAssignmentRecord;
import com.swiftdrop.delivery.domain.ParcelAssignmentRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;

/**
 * Status changes by couriers (COU-3, COU-4). Each change needs the courier's active task for that
 * step (ADR 0009 §5): the pickup task to pick up, a delivery task to go out, deliver or fail an
 * attempt, the return task to hand the parcel back. Dispatchers' hub steps are in
 * {@link ParcelHubService}.
 */
@Service
public class ParcelStatusService {

    private final ParcelRepository parcels;
    private final ParcelAssignmentRepository assignments;
    private final DeliveryProofRepository proofs;
    private final ParcelEventRecorder recorder;
    private final HubNetwork hubNetwork;
    private final DeliveryPolicy policy;
    private final Clock clock;

    @Autowired
    public ParcelStatusService(ParcelRepository parcels, ParcelAssignmentRepository assignments,
            DeliveryProofRepository proofs, ParcelEventRecorder recorder, HubNetwork hubNetwork,
            DeliveryPolicy policy) {
        this(parcels, assignments, proofs, recorder, hubNetwork, policy, Clock.systemUTC());
    }

    ParcelStatusService(ParcelRepository parcels, ParcelAssignmentRepository assignments, DeliveryProofRepository proofs,
            ParcelEventRecorder recorder, HubNetwork hubNetwork, DeliveryPolicy policy, Clock clock) {
        this.parcels = parcels;
        this.assignments = assignments;
        this.proofs = proofs;
        this.recorder = recorder;
        this.hubNetwork = hubNetwork;
        this.policy = policy;
        this.clock = clock;
    }

    /**
     * Applies a status change by a courier with an active task on the parcel.
     *
     * @throws ParcelNotFoundException if the parcel does not exist or the courier has no task on it
     * @throws ParcelConflictException if the change breaks a lifecycle rule
     * @throws IllegalArgumentException if the request itself is invalid (reason for a step without
     *         one, note too long)
     */
    @Transactional
    public ParcelStatusChanged changeStatus(UUID parcelId, StatusChange change, Actor courier) {
        Objects.requireNonNull(change, "change");
        if (courier.role() != ActorRole.COURIER) {
            throw new IllegalArgumentException("Only couriers change a parcel's status");
        }
        ParcelStatus target = change.toStatus();
        if (change.failureReason() != null && !target.needsFailureReason()) {
            throw new IllegalArgumentException("A failure reason is only allowed for a failed attempt or FAILED");
        }
        Parcel parcel = parcels.findByIdForUpdate(parcelId).orElseThrow(ParcelNotFoundException::new);
        ParcelStatus previous = parcel.getStatus();
        boolean legacy = parcel.isLegacy();
        ParcelAssignmentRecord task = activeTaskOf(parcel, courier.userId());
        requireTaskFor(task, parcel, target);
        Instant now = clock.instant();

        if (legacy) {
            // ADR 0006: the same courier from pickup to the end
            boolean proof = target == ParcelStatus.DELIVERED && proofs.existsByParcelId(parcelId);
            parcel.changeStatus(target, courier.userId(), change.failureReason(), proof, now);
            if (target == ParcelStatus.DELIVERED || target == ParcelStatus.FAILED) {
                task.complete(now);
            }
            return recorder.record(parcel, previous, courier, change.note(), change.failureReason(), now);
        }

        Integer attempt = null;
        switch (target) {
            case PICKED_UP -> {
                parcel.pickUp(courier.userId(), routeOrNull(parcel), now);
                // With a route the parcel now goes to its origin hub; without one this courier keeps it
                if (!parcel.isLegacy()) {
                    task.complete(now);
                }
            }
            case OUT_FOR_DELIVERY -> {
                parcel.startDelivery(courier.userId(), policy.maxDeliveryAttempts(), now);
                attempt = parcel.getDeliveryAttempts();
            }
            case DELIVERED -> {
                parcel.deliver(courier.userId(), proofs.existsByParcelId(parcelId), now);
                attempt = parcel.getDeliveryAttempts();
                task.complete(now);
            }
            case DELIVERY_ATTEMPT_FAILED -> {
                parcel.failAttempt(courier.userId(), change.failureReason(), now);
                attempt = parcel.getDeliveryAttempts();
                // Proof from this attempt must not justify a later delivery (ADR 0006 §5)
                proofs.findByParcelId(parcelId).ifPresent(proofs::delete);
                task.complete(now);
            }
            case RETURNED -> {
                parcel.completeReturn(courier.userId(), proofs.existsByParcelId(parcelId), now);
                task.complete(now);
            }
            default -> throw new ParcelConflictException("Illegal status transition");
        }
        return recorder.record(parcel, previous, courier, change.note(), change.failureReason(), now, null, attempt);
    }

    /** The route for a pickup, or {@code null} if the zones are not served (the parcel then follows ADR 0006). */
    private HubRoute routeOrNull(Parcel parcel) {
        try {
            return hubNetwork.routeFor(parcel.getPickup().getZoneId(), parcel.getDropoff().getZoneId());
        } catch (ParcelConflictException notServed) {
            return null;
        }
    }

    /**
     * The courier's active task on the parcel. A courier who never had a task on it gets 404, so
     * parcels of other couriers stay invisible (ADR 0006 §7); a courier who had one may see the
     * parcel and gets 409 instead.
     */
    private ParcelAssignmentRecord activeTaskOf(Parcel parcel, UUID courierId) {
        ParcelAssignmentRecord task = assignments
                .findFirstByParcelIdAndStatus(parcel.getId(), ParcelAssignmentStatus.ACTIVE)
                .filter(active -> active.getCourierId().equals(courierId))
                .orElse(null);
        if (task != null) {
            return task;
        }
        boolean involved = courierId.equals(parcel.getAssignedCourierId())
                || assignments.existsByParcelIdAndCourierId(parcel.getId(), courierId);
        if (!involved) {
            throw new ParcelNotFoundException();
        }
        if (parcel.getStatus().isFinal()) {
            throw new ParcelConflictException("Parcel is already in a final status");
        }
        throw new ParcelConflictException("The parcel has no active courier task");
    }

    private static void requireTaskFor(ParcelAssignmentRecord task, Parcel parcel, ParcelStatus target) {
        ParcelAssignmentType needed;
        if (parcel.getStatus() == ParcelStatus.CREATED) {
            needed = ParcelAssignmentType.PICKUP;
        } else if (parcel.isLegacy()) {
            return; // the pickup task, or a migrated delivery task, carries the parcel to the end
        } else if (target == ParcelStatus.RETURNED) {
            needed = ParcelAssignmentType.RETURN;
        } else {
            needed = ParcelAssignmentType.DELIVERY;
        }
        if (task.getType() != needed) {
            throw new ParcelConflictException("The current courier task does not match the parcel status");
        }
    }
}
