package com.swiftdrop.delivery.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.ParcelAssignmentStatus;
import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAssignmentRecord;
import com.swiftdrop.delivery.domain.ParcelAssignmentRepository;
import com.swiftdrop.delivery.domain.ParcelLifecycle;
import com.swiftdrop.delivery.domain.ParcelRepository;

/**
 * A courier's run for the day (COU-2, D-09): every task they still have (pickup, delivery attempt,
 * return), plus the tasks they finished today. Tasks, not the parcel's current courier, decide the
 * list, because the parcel changes hands at hubs (ADR 0009 §5). "Today" starts at midnight in the
 * business time zone ({@code Asia/Colombo} by default), not in UTC.
 */
@Service
@Transactional(readOnly = true)
public class CourierParcelService {

    private final ParcelRepository parcels;
    private final ParcelAssignmentRepository assignments;
    private final DeliveryProofRepository proofs;
    private final ZoneId businessZone;
    private final Clock clock;

    @Autowired
    public CourierParcelService(ParcelRepository parcels, ParcelAssignmentRepository assignments,
            DeliveryProofRepository proofs,
            @Value("${swiftdrop.delivery.business-time-zone:Asia/Colombo}") String businessZone) {
        this(parcels, assignments, proofs, ZoneId.of(businessZone), Clock.systemUTC());
    }

    CourierParcelService(ParcelRepository parcels, ParcelAssignmentRepository assignments,
            DeliveryProofRepository proofs, ZoneId businessZone, Clock clock) {
        this.parcels = parcels;
        this.assignments = assignments;
        this.proofs = proofs;
        this.businessZone = businessZone;
        this.clock = clock;
    }

    /**
     * One task on the courier's list.
     *
     * @param task what the courier does with the parcel
     * @param taskDone the courier finished this task today; nothing more to do
     * @param nextStatuses the statuses this courier may set next; empty when the task is done
     */
    public record CourierParcel(Parcel parcel, boolean proofCaptured, ParcelAssignmentType task, boolean taskDone,
            Set<ParcelStatus> nextStatuses) {

        public CourierParcel {
            Objects.requireNonNull(parcel, "parcel");
            Objects.requireNonNull(task, "task");
            nextStatuses = Set.copyOf(nextStatuses);
        }

        /** Never includes personal data. */
        @Override
        public String toString() {
            return "CourierParcel[parcel=" + parcel + ", task=" + task + ", taskDone=" + taskDone + "]";
        }
    }

    public List<CourierParcel> today(UUID courierId) {
        Objects.requireNonNull(courierId, "courierId");
        // The latest task per parcel; a parcel delivered on a second attempt shows once
        Map<UUID, ParcelAssignmentRecord> tasks = assignments.findCourierDay(courierId, startOfToday()).stream()
                .collect(Collectors.toMap(ParcelAssignmentRecord::getParcelId, Function.identity(),
                        (first, later) -> later, LinkedHashMap::new));
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<UUID, Parcel> byId = parcels.findAllById(tasks.keySet()).stream()
                .collect(Collectors.toMap(Parcel::getId, Function.identity()));
        Set<UUID> withProof = proofs.findParcelIdsWithProof(List.copyOf(tasks.keySet()));
        return tasks.values().stream()
                .filter(task -> byId.containsKey(task.getParcelId()))
                .map(task -> {
                    Parcel parcel = byId.get(task.getParcelId());
                    boolean done = task.getStatus() != ParcelAssignmentStatus.ACTIVE;
                    return new CourierParcel(parcel, withProof.contains(parcel.getId()), task.getType(), done,
                            done ? Set.of() : courierSteps(parcel, task.getType()));
                })
                .toList();
    }

    /** The statuses a courier with this task may set next (ADR 0009 §3; ADR 0006 for legacy parcels). */
    static Set<ParcelStatus> courierSteps(Parcel parcel, ParcelAssignmentType task) {
        if (parcel.isLegacy()) {
            return ParcelLifecycle.nextStatuses(parcel.getStatus(), true);
        }
        ParcelStatus status = parcel.getStatus();
        return switch (task) {
            case PICKUP -> status == ParcelStatus.CREATED ? Set.of(ParcelStatus.PICKED_UP) : Set.of();
            case DELIVERY -> parcel.isAtDestinationHub() ? Set.of(ParcelStatus.OUT_FOR_DELIVERY)
                    : status == ParcelStatus.OUT_FOR_DELIVERY
                            ? EnumSet.of(ParcelStatus.DELIVERED, ParcelStatus.DELIVERY_ATTEMPT_FAILED)
                            : Set.of();
            case RETURN -> status == ParcelStatus.RETURNING ? Set.of(ParcelStatus.RETURNED) : Set.of();
        };
    }

    Instant startOfToday() {
        return LocalDate.now(clock.withZone(businessZone)).atStartOfDay(businessZone).toInstant();
    }
}
