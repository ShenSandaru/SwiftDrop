package com.swiftdrop.merchant.web;

import java.util.Set;

import jakarta.validation.constraints.Size;

import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * Request payload for updating an existing merchant webhook endpoint (Phase 4).
 * All fields are optional; non-null fields will update the corresponding configuration.
 */
public record UpdateWebhookEndpointRequest(
        @Size(max = 2048, message = "Webhook URL must not exceed 2048 characters")
        String url,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description,

        Boolean enabled,

        Set<ParcelStatus> events
) {}

