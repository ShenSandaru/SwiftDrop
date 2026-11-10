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
    public PageResult<ParcelAssignmentSummary> activeAssignments(ParcelAssignmentType type, PageRequest page) {
        return parcels.findActiveAssignments(type, page);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> courierWorkloads() {
        return parcels.countActiveParcelsByCourier();
    }

    @Transactional(readOnly = true)
    public List<UserSummary> activeCouriers() {
        return users.findEnabledCouriers();
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
