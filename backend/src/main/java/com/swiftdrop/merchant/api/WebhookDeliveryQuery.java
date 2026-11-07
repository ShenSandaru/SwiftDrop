package com.swiftdrop.merchant.api;

import java.util.UUID;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;

/**
 * Read-side query interface for merchant webhook delivery history (docs/merchant-contracts.md).
 * Owned by Member B; consumed by Member A for portal views.
 */
public interface WebhookDeliveryQuery {

    /**
     * Finds webhook deliveries for a merchant, optionally filtered by endpoint ID.
     *
     * @param merchantId the merchant ID
     * @param endpointId optional endpoint ID filter, may be null
     * @param page pagination parameters
     * @return page of delivery views
     */
    PageResult<WebhookDeliveryView> findDeliveriesByMerchant(
            UUID merchantId,
            UUID endpointId,
            PageRequest page);
}

