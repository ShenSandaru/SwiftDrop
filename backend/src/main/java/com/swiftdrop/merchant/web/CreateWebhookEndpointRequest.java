package com.swiftdrop.merchant.web;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * Request payload for creating a new merchant webhook endpoint subscription (Phase 4).
 */
public record CreateWebhookEndpointRequest(
        @NotBlank(message = "Webhook URL is required")
        @Size(max = 2048, message = "Webhook URL must not exceed 2048 characters")
        String url,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description,

        @NotEmpty(message = "At least one parcel status event must be subscribed")
        Set<ParcelStatus> events
) {}

