package com.swiftdrop.merchant.api;

import java.util.List;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * Query contract for resolving active webhook subscriptions for a given event (docs/merchant-contracts.md).
 * Consumed by Member B when dispatching status change webhooks.
 */
public interface WebhookSubscriptionQuery {

    /**
     * Finds all active (enabled and not soft-deleted) webhook endpoints for a merchant
     * subscribed to the given parcel event type. The returned signing secret is decrypted.
     *
     * @param merchantId the merchant ID
     * @param eventType the parcel status event type
     * @return list of active subscriptions with decrypted secrets
     */
    List<WebhookSubscriptionView> findActiveFor(UUID merchantId, ParcelStatus eventType);
}

