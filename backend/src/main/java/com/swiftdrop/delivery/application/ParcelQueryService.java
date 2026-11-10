package com.swiftdrop.delivery.application;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.DispatchParcelSummary;
import com.swiftdrop.delivery.api.ParcelAssignmentHistory;
import com.swiftdrop.delivery.api.ParcelAssignmentStatus;
import com.swiftdrop.delivery.api.ParcelAssignmentSummary;
import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.delivery.api.PublicStatusEvent;
import com.swiftdrop.delivery.api.PublicTrackingView;
import com.swiftdrop.delivery.domain.Hub;
import com.swiftdrop.delivery.domain.HubRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAssignmentRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryEntry;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.delivery.domain.TrackingNumberGenerator;

/**
 * {@link ParcelQueries} for {@code dispatch}, {@code merchant} and {@code tracking}. Returns
 * {@code delivery.api} records only, never entities; merchant queries are always scoped by
 * merchant (PR-02).
 */
@Service
@Transactional(readOnly = true)
class ParcelQueryService implements ParcelQueries {

    private static final Sort OLDEST_FIRST = Sort.by(Sort.Direction.ASC, "createdAt");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Sort ASSIGNED_OLDEST_FIRST = Sort.by(Sort.Direction.ASC, "assignedAt");
    /** Final statuses (ADR 0009 §2): no task can follow. */
    private static final EnumSet<ParcelStatus> TERMINAL = EnumSet.of(ParcelStatus.DELIVERED, ParcelStatus.RETURNED,
            ParcelStatus.CANCELLED, ParcelStatus.FAILED);

    private final ParcelRepository parcels;
    private final ParcelAssignmentRepository assignments;
    private final ParcelStatusHistoryRepository history;
    private final HubRepository hubs;

    ParcelQueryService(ParcelRepository parcels, ParcelAssignmentRepository assignments,
            ParcelStatusHistoryRepository history, HubRepository hubs) {
        this.parcels = parcels;
        this.assignments = assignments;
        this.history = history;
        this.hubs = hubs;
    }

    @Override
    public PageResult<ParcelSummary> findUnassignedParcels(PageRequest page) {
        Objects.requireNonNull(page, "page");
        return toResult(parcels.findByStatusAndAssignedCourierIdIsNull(ParcelStatus.CREATED,
                pageable(page, OLDEST_FIRST)), page);
    }

    @Override
    public Map<UUID, Long> countActiveParcelsByCourier() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : parcels.countActiveByCourier(TERMINAL)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return Map.copyOf(counts);
    }

    @Override
    public PageResult<DispatchParcelSummary> findDispatchParcels(
            ParcelStatus status, ParcelSource source, Boolean assigned, String search, PageRequest page) {
        Objects.requireNonNull(page, "page");
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        UUID parcelId = parseParcelId(normalizedSearch);
        Pageable pageable = pageable(page, NEWEST_FIRST);
        Page<Parcel> found = normalizedSearch == null
                ? parcels.findForDispatch(status, source, assigned, pageable)
                : parcels.findForDispatchBySearch(status, source, assigned, normalizedSearch, parcelId, pageable);
        return PageResult.of(found.getContent().stream().map(ParcelQueryService::toDispatchSummary).toList(),
                page, found.getTotalElements());
    }

    @Override
    public PageResult<ParcelAssignmentSummary> findActiveAssignments(ParcelAssignmentType type, PageRequest page) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(page, "page");
        Page<ParcelAssignmentSummary> found = assignments
                .findByTypeAndStatus(type, ParcelAssignmentStatus.ACTIVE, pageable(page, ASSIGNED_OLDEST_FIRST))
                .map(assignment -> assignment.toSummary());
        return PageResult.of(found.getContent(), page, found.getTotalElements());
    }

    @Override
    public Optional<ParcelAssignmentHistory> findAssignmentHistory(UUID parcelId) {
        Objects.requireNonNull(parcelId, "parcelId");
        if (!parcels.existsById(parcelId)) {
            return Optional.empty();
        }
        return Optional.of(new ParcelAssignmentHistory(parcelId,
                assignments.findByParcelIdOrderByAssignedAtAsc(parcelId).stream()
                        .map(assignment -> assignment.toSummary())
                        .toList()));
    }

    @Override
    public PageResult<ParcelSummary> findParcelsByMerchant(UUID merchantId, ParcelStatus status, PageRequest page) {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(page, "page");
        Pageable pageable = pageable(page, NEWEST_FIRST);
        return toResult(status == null
                ? parcels.findByMerchantId(merchantId, pageable)
                : parcels.findByMerchantIdAndStatus(merchantId, status, pageable), page);
    }

    @Override
    public Optional<ParcelSummary> findByMerchantReference(UUID merchantId, String merchantOrderReference) {
        if (merchantId == null || merchantOrderReference == null || merchantOrderReference.isBlank()) {
            return Optional.empty();
        }
        return parcels.findByMerchantIdAndMerchantOrderReference(merchantId, merchantOrderReference.trim())
                .map(ParcelQueryService::toSummary);
    }

    @Override
    public Optional<PublicTrackingView> findPublicTrackingView(String trackingNumber) {
        if (trackingNumber == null) {
            return Optional.empty();
        }
        String normalized = TrackingNumberGenerator.normalize(trackingNumber);
        // Malformed input never reaches the database
        if (!TrackingNumberGenerator.isWellFormed(normalized)) {
            return Optional.empty();
        }
        return parcels.findByTrackingNumber(normalized).map(parcel -> new PublicTrackingView(
                parcel.getTrackingNumber(),
                parcel.getStatus(),
                parcel.getPickup().getCity(),
                parcel.getDropoff().getCity(),
                publicTimeline(parcel)));
    }

    /** Status, time, hub name and attempt per step (ADR 0009 §7); hub names, never people or notes. */
    private List<PublicStatusEvent> publicTimeline(Parcel parcel) {
        List<ParcelStatusHistoryEntry> entries = history.findByParcelIdOrderBySequenceAsc(parcel.getId());
        Map<String, String> hubNames = entries.stream().anyMatch(entry -> entry.getHubCode() != null)
                ? hubs.findAll().stream().collect(Collectors.toMap(Hub::getCode, Hub::getName))
                : Map.of();
        return entries.stream()
                .map(entry -> new PublicStatusEvent(entry.getToStatus(), entry.getOccurredAt(),
                        entry.getHubCode() == null ? null : hubNames.getOrDefault(entry.getHubCode(), entry.getHubCode()),
                        entry.getAttempt()))
                .toList();
    }

    static ParcelSummary toSummary(Parcel parcel) {
        return new ParcelSummary(parcel.getId(), parcel.getTrackingNumber(), parcel.getStatus(), parcel.getSize(),
                parcel.getWeightGrams(), parcel.getSource(), parcel.getCustomerId(), parcel.getMerchantId(),
                parcel.getMerchantOrderReference(), parcel.getAssignedCourierId(), parcel.getCreatedAt());
    }

    private static DispatchParcelSummary toDispatchSummary(Parcel parcel) {
        return new DispatchParcelSummary(parcel.getId(), parcel.getTrackingNumber(), parcel.getStatus(),
                parcel.getSource(), parcel.getMerchantOrderReference(), parcel.getAssignedCourierId(),
                parcel.getSize(), parcel.getWeightGrams(), parcel.getPickup().getCity(),
                parcel.getDropoff().getCity(), parcel.getCreatedAt(), parcel.getUpdatedAt());
    }

    private static UUID parseParcelId(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static PageResult<ParcelSummary> toResult(Page<Parcel> found, PageRequest page) {
        return PageResult.of(found.getContent().stream().map(ParcelQueryService::toSummary).toList(), page,
                found.getTotalElements());
    }

    // Spring's PageRequest shares its name with delivery.api.PageRequest
    private static Pageable pageable(PageRequest page, Sort sort) {
        return org.springframework.data.domain.PageRequest.of(page.page(), page.size(), sort);
    }
}
