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

import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelAssignmentStatus;
import com.swiftdrop.delivery.api.ParcelAssignmentSummary;
import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAssignmentRecord;
import com.swiftdrop.delivery.domain.ParcelAssignmentRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;

/**
 * {@link ParcelAssignment} for {@code dispatch} (ADM-1). {@code dispatch} has already checked that
 * the courier is eligible; this records the task under optimistic locking so competing requests
 * cannot silently overwrite assignments or race pickups (SD-317). Pickup tasks before pickup,
 * delivery tasks at the destination hub, return tasks while returning (ADR 0009 §5); each may be
 * reassigned before the courier starts it.
 */
@Service
class ParcelAssignmentService implements ParcelAssignment {

    private static final Logger log = LoggerFactory.getLogger(ParcelAssignmentService.class);

    private final ParcelRepository parcels;
    private final ParcelAssignmentRepository assignments;
    private final DeliveryPolicy policy;
    private final Clock clock;

    @Autowired
    ParcelAssignmentService(ParcelRepository parcels, ParcelAssignmentRepository assignments, DeliveryPolicy policy) {
        this(parcels, assignments, policy, Clock.systemUTC());
    }

    ParcelAssignmentService(ParcelRepository parcels, ParcelAssignmentRepository assignments, DeliveryPolicy policy,
            Clock clock) {
        this.parcels = parcels;
        this.assignments = assignments;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void assignCourier(UUID parcelId, UUID courierUserId, UUID dispatcherUserId) {
        assignCourier(parcelId, courierUserId, dispatcherUserId, ParcelAssignmentType.PICKUP);
    }

    @Override
    @Transactional
    public ParcelAssignmentSummary assignCourier(UUID parcelId, UUID courierUserId, UUID dispatcherUserId,
            ParcelAssignmentType type) {
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(courierUserId, "courierUserId");
        Objects.requireNonNull(dispatcherUserId, "dispatcherUserId");
        Objects.requireNonNull(type, "type");
        Parcel parcel = parcels.findById(parcelId).orElseThrow(ParcelNotFoundException::new);
        Instant now = clock.instant();

        // The parcel decides whether this kind of task is possible now (ADR 0009 §5)
        if (type == ParcelAssignmentType.PICKUP && parcel.getStatus() != ParcelStatus.CREATED) {
            throw new ParcelConflictException("A courier can only be assigned before pickup");
        }

        ParcelAssignmentRecord current = assignments
                .findFirstByParcelIdAndStatus(parcelId, ParcelAssignmentStatus.ACTIVE)
                .orElse(null);

        if (current != null) {
            if (current.getType() != type) {
                throw new ParcelConflictException("The parcel has another active courier task");
            }
            if (current.getCourierId().equals(courierUserId)) {
                return current.toSummary();
            }
            if (!current.getCourierId().equals(parcel.getAssignedCourierId())) {
                throw new ParcelConflictException("The current parcel assignment does not match its history");
            }
            current.reassign(now);
            assignments.saveAndFlush(current);
        } else if (parcel.getAssignedCourierId() != null) {
            throw new ParcelConflictException("The current parcel assignment has no active history record");
        }

        switch (type) {
            case PICKUP -> parcel.assignPickupCourier(courierUserId, now);
            case DELIVERY -> parcel.assignDeliveryCourier(courierUserId, policy.maxDeliveryAttempts(), now);
            case RETURN -> parcel.assignReturnCourier(courierUserId, now);
        }
        parcels.saveAndFlush(parcel);

        ParcelAssignmentRecord assignment = assignments.saveAndFlush(
                new ParcelAssignmentRecord(parcelId, courierUserId, type, dispatcherUserId, now));
        // IDs only: no personal data in logs
        log.info("Parcel {} {} task assigned to courier {} by dispatcher {}", parcelId, type, courierUserId,
                dispatcherUserId);
        return assignment.toSummary();
    }
}
