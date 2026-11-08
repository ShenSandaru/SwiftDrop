package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpoint;

/**
 * Response payload returned exclusively upon creating a webhook endpoint.
 * Contains the raw signing secret, which is never returned or stored in plaintext again.
 */
public record CreateWebhookEndpointResponse(
        UUID id,
        UUID merchantId,
        String url,
        String description,
        boolean enabled,
        Set<ParcelStatus> events,
        String secret,
        String secretHint,
        Instant createdAt,
        Instant updatedAt
) {
    public static CreateWebhookEndpointResponse of(MerchantWebhookEndpoint endpoint, String rawSecret) {
        return new CreateWebhookEndpointResponse(
                endpoint.getId(),
                endpoint.getMerchantId(),
                endpoint.getUrl(),
                endpoint.getDescription(),
                endpoint.isEnabled(),
                endpoint.getEvents(),
                rawSecret,
                endpoint.getSecretHint(),
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt()
        );
    }
}

