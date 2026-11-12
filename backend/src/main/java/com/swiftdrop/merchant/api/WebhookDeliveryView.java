package com.swiftdrop.merchant.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * Summary view of a webhook delivery attempt for presentation in the merchant portal.
 */
public record WebhookDeliveryView(
        UUID id,
        UUID endpointId,
        String endpointUrl,
        ParcelStatus eventType,
        Integer responseStatus,
        String deliveryStatus,
        int attemptCount,
        Instant nextRetryAt,
        Instant createdAt,
        Instant completedAt) {

    public WebhookDeliveryView {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(endpointId, "endpointId must not be null");
        Objects.requireNonNull(endpointUrl, "endpointUrl must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(deliveryStatus, "deliveryStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}

