package com.swiftdrop.delivery.application;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.delivery.api.PublicStatusEvent;
import com.swiftdrop.delivery.api.PublicTrackingView;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelRepository;
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
    private static final EnumSet<ParcelStatus> TERMINAL = EnumSet.of(ParcelStatus.DELIVERED, ParcelStatus.FAILED);

    private final ParcelRepository parcels;
    private final ParcelStatusHistoryRepository history;

    ParcelQueryService(ParcelRepository parcels, ParcelStatusHistoryRepository history) {
        this.parcels = parcels;
        this.history = history;
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
                history.findByParcelIdOrderBySequenceAsc(parcel.getId()).stream()
                        .map(entry -> new PublicStatusEvent(entry.getToStatus(), entry.getOccurredAt()))
                        .toList()));
    }

    static ParcelSummary toSummary(Parcel parcel) {
        return new ParcelSummary(parcel.getId(), parcel.getTrackingNumber(), parcel.getStatus(), parcel.getSize(),
                parcel.getWeightGrams(), parcel.getSource(), parcel.getCustomerId(), parcel.getMerchantId(),
                parcel.getMerchantOrderReference(), parcel.getAssignedCourierId(), parcel.getCreatedAt());
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
