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
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAssignmentRecord;
import com.swiftdrop.delivery.domain.ParcelAssignmentRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;

/**
 * {@link ParcelAssignment} for {@code dispatch} (ADM-1). {@code dispatch} has already checked that
 * the courier is eligible; this records the assignment under the parcel lock so it cannot race a
 * pickup.
 */
@Service
class ParcelAssignmentService implements ParcelAssignment {

    private static final Logger log = LoggerFactory.getLogger(ParcelAssignmentService.class);

    private final ParcelRepository parcels;
    private final ParcelAssignmentRepository assignments;
    private final Clock clock;

    @Autowired
    ParcelAssignmentService(ParcelRepository parcels, ParcelAssignmentRepository assignments) {
        this(parcels, assignments, Clock.systemUTC());
    }

    ParcelAssignmentService(ParcelRepository parcels, ParcelAssignmentRepository assignments, Clock clock) {
        this.parcels = parcels;
        this.assignments = assignments;
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
        Parcel parcel = parcels.findByIdForUpdate(parcelId).orElseThrow(ParcelNotFoundException::new);
        Instant now = clock.instant();
        ParcelAssignmentRecord current = assignments
                .findFirstByParcelIdAndStatus(parcelId, ParcelAssignmentStatus.ACTIVE)
                .orElse(null);

        if (type == ParcelAssignmentType.PICKUP) {
            if (current != null) {
                if (current.getType() != ParcelAssignmentType.PICKUP) {
                    throw new ParcelConflictException("The parcel already has a different active task");
                }
                if (current.getCourierId().equals(courierUserId)) {
                    return current.toSummary();
                }
                if (!current.getCourierId().equals(parcel.getAssignedCourierId())) {
                    throw new ParcelConflictException("The current parcel assignment does not match its history");
                }
                current.reassign(now);
            } else if (parcel.getAssignedCourierId() != null) {
                throw new ParcelConflictException("The current parcel assignment has no active history record");
            }
            parcel.assignPickupCourier(courierUserId, now);
        } else {
            if (current != null || parcel.getAssignedCourierId() != null) {
                throw new ParcelConflictException("The parcel already has an active courier task");
            }
            parcel.assignDeliveryCourier(courierUserId, now);
        }

        ParcelAssignmentRecord assignment = assignments.saveAndFlush(
                new ParcelAssignmentRecord(parcelId, courierUserId, type, dispatcherUserId, now));
        // IDs only: no personal data in logs
        log.info("Parcel {} assigned to courier {} by dispatcher {}", parcelId, courierUserId, dispatcherUserId);
        return assignment.toSummary();
    }
}
