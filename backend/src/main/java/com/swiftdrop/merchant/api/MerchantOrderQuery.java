package com.swiftdrop.merchant.api;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;

/**
 * Read-side query interface for the merchant dashboard and order lists (docs/merchant-contracts.md).
 * Owned by the delivery/parcel module; consumed by Member A for portal views.
 */
public interface MerchantOrderQuery {

    /**
     * Lists parcels owned by the merchant, newest first.
     *
     * @param merchantId the merchant ID
     * @param status optional status filter; null returns all statuses
     * @param page pagination parameters
     * @return page of parcel summaries
     */
    PageResult<ParcelSummary> findParcelsByMerchant(UUID merchantId, ParcelStatus status, PageRequest page);

    /**
     * Finds a single merchant parcel by the merchant's external order reference.
     *
     * @param merchantId the merchant ID
     * @param merchantOrderReference the merchant's order reference
     * @return parcel summary if found, or empty
     */
    Optional<ParcelSummary> findByMerchantReference(UUID merchantId, String merchantOrderReference);

    /**
     * Counts parcels owned by this merchant grouped by parcel status.
     *
     * @param merchantId the merchant ID
     * @return map of status to parcel count
     */
    Map<ParcelStatus, Long> countParcelsByStatus(UUID merchantId);
}

