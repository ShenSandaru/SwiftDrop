package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpoint;

/**
 * Standard webhook endpoint representation returned by listing and detail endpoints.
 * Never contains the raw signing secret; only the last 4 characters are shown as a hint (NFR-04).
 */
public record WebhookEndpointResponse(
        UUID id,
        UUID merchantId,
        String url,
        String description,
        boolean enabled,
        Set<ParcelStatus> events,
        String secretHint,
        Instant createdAt,
        Instant updatedAt
) {
    public static WebhookEndpointResponse from(MerchantWebhookEndpoint endpoint) {
        return new WebhookEndpointResponse(
                endpoint.getId(),
                endpoint.getMerchantId(),
                endpoint.getUrl(),
                endpoint.getDescription(),
                endpoint.isEnabled(),
                endpoint.getEvents(),
                endpoint.getSecretHint(),
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt()
        );
    }
}

