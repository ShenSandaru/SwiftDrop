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

    /** Dispatcher parcel list with explicit current-assignment and source/status filtering. */
    PageResult<DispatchParcelSummary> findDispatchParcels(
            ParcelStatus status, ParcelSource source, Boolean assigned, String search, PageRequest page);

    /** Active assignment rows of one task type, oldest first. */
    PageResult<ParcelAssignmentSummary> findActiveAssignments(ParcelAssignmentType type, PageRequest page);

    /** Assignment history, empty when the parcel does not exist. */
    Optional<ParcelAssignmentHistory> findAssignmentHistory(UUID parcelId);

    /** Dispatcher/admin assignment and status activity, newest first. */
    PageResult<DispatchActivity> findDispatchActivityHistory(
            DispatchActivityFilter filter, PageRequest page);

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
