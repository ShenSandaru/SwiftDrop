package com.swiftdrop.dispatch.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.DispatchParcelSummary;
import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelAssignmentHistory;
import com.swiftdrop.delivery.api.ParcelAssignmentSummary;
import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelHubOperations;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.Staff;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;

@Service
public class DispatchService {

    private final ParcelQueries parcels;
    private final ParcelAssignment assignments;
    private final ParcelHubOperations hubOperations;
    private final UserDirectory users;

    public DispatchService(ParcelQueries parcels, ParcelAssignment assignments, ParcelHubOperations hubOperations,
            UserDirectory users) {
        this.parcels = parcels;
        this.assignments = assignments;
        this.hubOperations = hubOperations;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageResult<DispatchParcelSummary> parcels(
            ParcelStatus status, ParcelSource source, Boolean assigned, String search, PageRequest page) {
        return parcels.findDispatchParcels(status, source, assigned, search, page);
    }

    @Transactional(readOnly = true)
    public PageResult<DispatchParcelSummary> parcels(
            ParcelStatus status, ParcelSource source, Boolean assigned, String search,
            UUID requestedHubId, Staff caller, PageRequest page) {
        UUID effectiveHubId = resolveEffectiveHubId(caller, requestedHubId);
        return parcels.findDispatchParcels(status, source, assigned, search, effectiveHubId, page);
    }

    @Transactional(readOnly = true)
    public PageResult<ParcelAssignmentSummary> activeAssignments(ParcelAssignmentType type, PageRequest page) {
        return parcels.findActiveAssignments(type, page);
    }

    @Transactional(readOnly = true)
    public PageResult<ParcelAssignmentSummary> activeAssignments(
            ParcelAssignmentType type, UUID requestedHubId, Staff caller, PageRequest page) {
        UUID effectiveHubId = resolveEffectiveHubId(caller, requestedHubId);
        return parcels.findActiveAssignments(type, effectiveHubId, page);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> courierWorkloads() {
        return parcels.countActiveParcelsByCourier();
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> courierWorkloads(UUID requestedHubId, Staff caller) {
        UUID effectiveHubId = resolveEffectiveHubId(caller, requestedHubId);
        Map<UUID, Long> all = parcels.countActiveParcelsByCourier();
        if (effectiveHubId == null) {
            return all;
        }
        java.util.Set<UUID> hubCourierIds = users.findEnabledCouriersByHub(effectiveHubId).stream()
                .map(UserSummary::id)
                .collect(java.util.stream.Collectors.toSet());
        return all.entrySet().stream()
                .filter(e -> hubCourierIds.contains(e.getKey()))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Transactional(readOnly = true)
    public List<UserSummary> activeCouriers() {
        return users.findEnabledCouriers();
    }

    @Transactional(readOnly = true)
    public List<UserSummary> activeCouriers(UUID requestedHubId, Staff caller) {
        UUID effectiveHubId = resolveEffectiveHubId(caller, requestedHubId);
        return effectiveHubId == null
                ? users.findEnabledCouriers()
                : users.findEnabledCouriersByHub(effectiveHubId);
    }

    @Transactional(readOnly = true)
    public ParcelAssignmentHistory assignmentHistory(UUID parcelId) {
        Objects.requireNonNull(parcelId, "parcelId");
        return parcels.findAssignmentHistory(parcelId).orElseThrow(ParcelNotFoundException::new);
    }

    @Transactional
    public ParcelAssignmentSummary assign(UUID parcelId, UUID courierId, UUID dispatcherId,
            ParcelAssignmentType type) {
        return assign(parcelId, courierId, Staff.dispatcher(dispatcherId), type);
    }

    @Transactional
    public ParcelAssignmentSummary assign(UUID parcelId, UUID courierId, Staff caller,
            ParcelAssignmentType type) {
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(courierId, "courierId");
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(type, "type");

        UserSummary courier = users.findUser(courierId).orElseThrow(DispatchCourierNotFoundException::new);
        if (!courier.hasRole("COURIER")) {
            throw new DispatchCourierNotFoundException();
        }
        if (!courier.enabled()) {
            throw new DispatchCourierUnavailableException("The courier account is disabled");
        }

        UUID effectiveHubId = resolveEffectiveHubId(caller, null);
        if (effectiveHubId != null) {
            if (!effectiveHubId.equals(courier.hubId())) {
                throw new DispatchCourierUnavailableException("The courier is not assigned to your hub");
            }
            if (!parcels.isParcelInHub(parcelId, effectiveHubId)) {
                throw new ParcelConflictException("Parcel does not belong to your hub");
            }
        }

        return assignments.assignCourier(parcelId, courierId, caller.userId(), type);
    }

    // Hub network steps (ADR 0009 §3, §6); delivery enforces the rules and records the history

    public void scanAtHub(UUID parcelId, UUID hubId, Staff staff) {
        Objects.requireNonNull(hubId, "hubId");
        UUID effectiveHubId = resolveEffectiveHubId(staff, null);
        if (effectiveHubId != null && !effectiveHubId.equals(hubId)) {
            throw new ParcelConflictException("Dispatchers can only scan parcels at their assigned hub");
        }
        hubOperations.scanAtHub(parcelId, hubId, staff);
    }

    public void sendToNextHub(UUID parcelId, Staff staff) {
        requireParcelInCallerHub(parcelId, staff);
        hubOperations.sendToNextHub(parcelId, staff);
    }

    public void startReturn(UUID parcelId, Staff staff) {
        requireParcelInCallerHub(parcelId, staff);
        hubOperations.startReturn(parcelId, staff);
    }

    public void markFailed(UUID parcelId, FailureReason reason, String note, Staff staff) {
        requireParcelInCallerHub(parcelId, staff);
        hubOperations.markFailed(parcelId, Objects.requireNonNull(reason, "reason"), note, staff);
    }

    public void cancel(UUID parcelId, Staff staff) {
        requireParcelInCallerHub(parcelId, staff);
        hubOperations.cancel(parcelId, staff);
    }

    private UUID resolveEffectiveHubId(Staff caller, UUID requestedHubId) {
        if (caller == null) {
            return requestedHubId;
        }
        if (caller.admin()) {
            return requestedHubId;
        }
        return users.findUser(caller.userId())
                .map(UserSummary::hubId)
                .orElse(null);
    }

    private void requireParcelInCallerHub(UUID parcelId, Staff staff) {
        UUID effectiveHubId = resolveEffectiveHubId(staff, null);
        if (effectiveHubId != null && !parcels.isParcelInHub(parcelId, effectiveHubId)) {
            throw new ParcelConflictException("Parcel does not belong to your hub");
        }
    }
}
