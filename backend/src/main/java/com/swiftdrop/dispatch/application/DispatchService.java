package com.swiftdrop.dispatch.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.DispatchParcelSummary;
import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.HubStaffDirectory;
import com.swiftdrop.delivery.api.NewWalkInParcel;
import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelAssignmentHistory;
import com.swiftdrop.delivery.api.ParcelAssignmentSummary;
import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelCreation;
import com.swiftdrop.delivery.api.ParcelHubOperations;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.Staff;
import com.swiftdrop.delivery.api.StaffScopeView;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;

@Service
public class DispatchService {

    private final ParcelQueries parcels;
    private final ParcelAssignment assignments;
    private final ParcelHubOperations hubOperations;
    private final ParcelCreation parcelCreation;
    private final UserDirectory users;
    private final HubStaffDirectory hubStaff;
    private final Duration staleAfter;
    private final Clock clock;

    @Autowired
    public DispatchService(ParcelQueries parcels, ParcelAssignment assignments, ParcelHubOperations hubOperations,
            ParcelCreation parcelCreation, UserDirectory users, HubStaffDirectory hubStaff,
            @Value("${swiftdrop.dispatch.stale-after-hours:24}") int staleAfterHours) {
        this(parcels, assignments, hubOperations, parcelCreation, users, hubStaff, Duration.ofHours(staleAfterHours),
                Clock.systemUTC());
    }

    DispatchService(ParcelQueries parcels, ParcelAssignment assignments, ParcelHubOperations hubOperations,
            ParcelCreation parcelCreation, UserDirectory users, HubStaffDirectory hubStaff, Duration staleAfter,
            Clock clock) {
        if (staleAfter.isZero() || staleAfter.isNegative()) {
            throw new IllegalStateException("swiftdrop.dispatch.stale-after-hours must be at least 1");
        }
        this.parcels = parcels;
        this.assignments = assignments;
        this.hubOperations = hubOperations;
        this.parcelCreation = parcelCreation;
        this.users = users;
        this.hubStaff = hubStaff;
        this.staleAfter = staleAfter;
        this.clock = clock;
    }

    /**
     * Unfinished parcels nobody has touched for the configured time, longest waiting first, so a
     * missed scan or a lost parcel does not stay hidden.
     */
    @Transactional(readOnly = true)
    public PageResult<DispatchParcelSummary> staleParcels(Staff staff, PageRequest page) {
        return parcels.findStaleParcels(staff.userId(), clock.instant().minus(staleAfter), page);
    }

    @Transactional(readOnly = true)
    public PageResult<DispatchParcelSummary> parcels(Staff staff,
            ParcelStatus status, ParcelSource source, Boolean assigned, String search, PageRequest page) {
        return parcels(staff, status, source, assigned, search, null, page);
    }

    @Transactional(readOnly = true)
    public PageResult<DispatchParcelSummary> parcels(Staff staff,
            ParcelStatus status, ParcelSource source, Boolean assigned, String search, Boolean terminal,
            PageRequest page) {
        return parcels.findDispatchParcels(staff.userId(), status, source, assigned, search, terminal, page);
    }

    @Transactional(readOnly = true)
    public PageResult<ParcelAssignmentSummary> activeAssignments(Staff staff, ParcelAssignmentType type,
            PageRequest page) {
        return parcels.findActiveAssignments(staff.userId(), type, page);
    }

    /** Where the caller works, so the portal can explain a short or empty list (ADR 0012 §5). */
    @Transactional(readOnly = true)
    public StaffScopeView scope(Staff staff) {
        return staff.admin() ? new StaffScopeView(true, List.of()) : hubStaff.scopeOf(staff.userId());
    }

    /** Active parcels per courier, for the couriers the caller may assign (ADR 0012 §4). */
    @Transactional(readOnly = true)
    public Map<UUID, Long> courierWorkloads(Staff staff) {
        Map<UUID, Long> all = parcels.countActiveParcelsByCourier();
        if (staff.admin() || hubStaff.worksEverywhere(staff.userId())) {
            return all;
        }
        Set<UUID> visible = hubStaff.colleaguesOf(staff.userId());
        return all.entrySet().stream().filter(entry -> visible.contains(entry.getKey()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** The couriers the caller may assign: all of them for admins, else those of their hubs. */
    @Transactional(readOnly = true)
    public List<UserSummary> activeCouriers(Staff staff) {
        List<UserSummary> couriers = users.findEnabledCouriers();
        if (staff.admin() || hubStaff.worksEverywhere(staff.userId())) {
            return couriers;
        }
        Set<UUID> visible = hubStaff.colleaguesOf(staff.userId());
        return couriers.stream().filter(courier -> visible.contains(courier.id())).toList();
    }

    @Transactional(readOnly = true)
    public ParcelAssignmentHistory assignmentHistory(UUID parcelId) {
        Objects.requireNonNull(parcelId, "parcelId");
        return parcels.findAssignmentHistory(parcelId).orElseThrow(ParcelNotFoundException::new);
    }

    @Transactional
    public ParcelAssignmentSummary assign(UUID parcelId, UUID courierId, UUID dispatcherId,
            ParcelAssignmentType type) {
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(courierId, "courierId");
        Objects.requireNonNull(dispatcherId, "dispatcherId");
        Objects.requireNonNull(type, "type");

        UserSummary courier = users.findUser(courierId).orElseThrow(DispatchCourierNotFoundException::new);
        if (!courier.hasRole("COURIER")) {
            throw new DispatchCourierNotFoundException();
        }
        if (!courier.enabled()) {
            throw new DispatchCourierUnavailableException("The courier account is disabled");
        }

        return assignments.assignCourier(parcelId, courierId, dispatcherId, type);
    }

    // Hub network steps (ADR 0009 §3, §6); delivery enforces the rules and records the history

    public CreatedParcel createWalkInParcel(UUID hubId, DeliveryContact pickup, DeliveryContact dropoff,
            String sizeName, int weightGrams, String description, Staff staff) {
        UUID targetHubId = hubId;
        if (targetHubId == null && !staff.admin()) {
            StaffScopeView view = hubStaff.scopeOf(staff.userId());
            if (view.hubs().size() == 1) {
                targetHubId = view.hubs().getFirst().id();
            }
        }
        if (targetHubId == null) {
            throw new IllegalArgumentException("hubId is required");
        }
        if (!staff.admin() && !hubStaff.worksEverywhere(staff.userId())) {
            UUID finalHubId = targetHubId;
            boolean assignedToHub = hubStaff.scopeOf(staff.userId()).hubs().stream()
                    .anyMatch(hub -> hub.id().equals(finalHubId));
            if (!assignedToHub) {
                throw new ParcelConflictException("Dispatchers can only receive parcels at their assigned hub");
            }
        }
        return parcelCreation.createWalkInParcel(NewWalkInParcel.withSizeName(
                targetHubId, pickup, dropoff, sizeName, weightGrams, description, staff));
    }

    public void receiveAtHub(UUID parcelId, UUID hubId, Staff staff) {
        hubOperations.receiveAtHub(parcelId, Objects.requireNonNull(hubId, "hubId"), staff);
    }

    public void scanAtHub(UUID parcelId, UUID hubId, Staff staff) {
        hubOperations.scanAtHub(parcelId, Objects.requireNonNull(hubId, "hubId"), staff);
    }

    public void sendToNextHub(UUID parcelId, Staff staff) {
        hubOperations.sendToNextHub(parcelId, staff);
    }

    public void startReturn(UUID parcelId, Staff staff) {
        hubOperations.startReturn(parcelId, staff);
    }

    public void markFailed(UUID parcelId, FailureReason reason, String note, Staff staff) {
        hubOperations.markFailed(parcelId, Objects.requireNonNull(reason, "reason"), note, staff);
    }

    public void cancel(UUID parcelId, Staff staff) {
        hubOperations.cancel(parcelId, staff);
    }
}
