package com.swiftdrop.merchant.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.delivery.api.PublicTrackingView;
import com.swiftdrop.merchant.api.MerchantOrderDetailView;
import com.swiftdrop.merchant.api.MerchantOrderQuery;

/**
 * {@link MerchantOrderQuery} on top of the delivery module's public {@link ParcelQueries} (docs/merchant-contracts.md
 * §3.3). Every call is scoped by the merchant ID, which callers take from the authenticated principal (PR-02).
 *
 * <p>Delivery does not yet offer a status count or a by-ID lookup for merchants, so this adapter builds them from
 * the paged merchant query. When delivery adds dedicated queries, only this class changes; the portal services and
 * controllers keep coding against the interface.
 */
@Component
class ParcelQueriesMerchantOrderQuery implements MerchantOrderQuery {

    /** Page size used while searching the merchant's own parcels for one ID (the maximum delivery allows). */
    static final int SCAN_PAGE_SIZE = PageRequest.MAX_SIZE;

    private final ParcelQueries parcels;

    ParcelQueriesMerchantOrderQuery(ParcelQueries parcels) {
        this.parcels = Objects.requireNonNull(parcels, "parcels must not be null");
    }

    @Override
    public PageResult<ParcelSummary> findParcelsByMerchant(UUID merchantId, ParcelStatus status, PageRequest page) {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(page, "page must not be null");
        return parcels.findParcelsByMerchant(merchantId, status, page);
    }

    @Override
    public Optional<ParcelSummary> findByMerchantReference(UUID merchantId, String merchantOrderReference) {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        if (merchantOrderReference == null || merchantOrderReference.isBlank()) {
            return Optional.empty();
        }
        return parcels.findByMerchantReference(merchantId, merchantOrderReference.trim());
    }

    @Override
    public Optional<MerchantOrderDetailView> findOrderDetail(UUID merchantId, UUID parcelId) {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(parcelId, "parcelId must not be null");
        return findOwnParcel(merchantId, parcelId).map(this::toDetail);
    }

    @Override
    public Map<ParcelStatus, Long> countParcelsByStatus(UUID merchantId) {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Map<ParcelStatus, Long> counts = new EnumMap<>(ParcelStatus.class);
        for (ParcelStatus status : ParcelStatus.values()) {
            counts.put(status, parcels.findParcelsByMerchant(merchantId, status, new PageRequest(0, 1)).totalItems());
        }
        return counts;
    }

    /** Searches only this merchant's parcels, so another merchant's ID is simply not found. */
    private Optional<ParcelSummary> findOwnParcel(UUID merchantId, UUID parcelId) {
        int page = 0;
        PageResult<ParcelSummary> batch;
        do {
            batch = parcels.findParcelsByMerchant(merchantId, null, new PageRequest(page, SCAN_PAGE_SIZE));
            Optional<ParcelSummary> match = batch.items().stream()
                    .filter(parcel -> parcelId.equals(parcel.id()))
                    .findFirst();
            if (match.isPresent()) {
                return match;
            }
            page++;
        } while (page < batch.totalPages());
        return Optional.empty();
    }

    private MerchantOrderDetailView toDetail(ParcelSummary parcel) {
        Optional<PublicTrackingView> tracking = parcels.findPublicTrackingView(parcel.trackingNumber());
        List<MerchantOrderDetailView.StatusEvent> history = tracking
                .map(view -> view.history().stream()
                        .map(event -> new MerchantOrderDetailView.StatusEvent(
                                event.status(), event.occurredAt(), event.hubName(), event.attempt()))
                        .toList())
                .orElse(List.of());
        return new MerchantOrderDetailView(
                parcel.id(),
                parcel.trackingNumber(),
                parcel.merchantOrderReference(),
                parcel.status(),
                parcel.size().name(),
                parcel.weightGrams(),
                tracking.map(PublicTrackingView::pickupCity).orElse(null),
                tracking.map(PublicTrackingView::dropoffCity).orElse(null),
                parcel.assignedCourierId() != null,
                parcel.createdAt(),
                history);
    }
}
