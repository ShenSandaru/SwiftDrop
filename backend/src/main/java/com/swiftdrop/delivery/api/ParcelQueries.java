package com.swiftdrop.delivery.api;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read access to parcels for other modules. Merchant queries always take the merchant ID, so a
 * merchant can never see another merchant's parcels (PR-02).
 */
public interface ParcelQueries {

    /** Parcels in {@link ParcelStatus#CREATED} without a courier, oldest first (dispatch, ADM-1). */
    PageResult<ParcelSummary> findUnassignedParcels(PageRequest page);

    /**
     * Number of non-terminal parcels per assigned courier user ID (dispatch, ADM-2). Couriers with
     * no active parcels are absent from the map.
     */
    Map<UUID, Long> countActiveParcelsByCourier();

    /**
     * The merchant's parcels, newest first (merchant dashboard, MER-4).
     *
     * @param status optional filter; {@code null} returns all statuses
     */
    PageResult<ParcelSummary> findParcelsByMerchant(UUID merchantId, ParcelStatus status, PageRequest page);

    /** The merchant's parcel for one of its order references; empty for another merchant's. */
    Optional<ParcelSummary> findByMerchantReference(UUID merchantId, String merchantOrderReference);

    /** Public view for a tracking number, matched case-insensitively (tracking, CUS-6). */
    Optional<PublicTrackingView> findPublicTrackingView(String trackingNumber);
}
