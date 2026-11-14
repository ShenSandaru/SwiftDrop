package com.swiftdrop.merchant.domain;

import java.util.UUID;

/**
 * Thrown when a merchant webhook endpoint cannot be found by ID for the requesting merchant (Phase 4).
 */
public class WebhookEndpointNotFoundException extends RuntimeException {

    private final UUID endpointId;

    public WebhookEndpointNotFoundException(UUID endpointId) {
        super(String.format("Webhook endpoint %s not found", endpointId));
        this.endpointId = endpointId;
    }

    public UUID getEndpointId() {
        return endpointId;
    }
}

