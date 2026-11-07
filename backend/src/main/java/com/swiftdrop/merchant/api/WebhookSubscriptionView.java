package com.swiftdrop.merchant.api;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * View of an active webhook subscription including decrypted signing secret for payload HMAC calculation.
 */
public record WebhookSubscriptionView(
        UUID id,
        UUID merchantId,
        String url,
        Set<ParcelStatus> eventTypes,
        String signingSecret) {

    public WebhookSubscriptionView {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(signingSecret, "signingSecret must not be null");
        eventTypes = eventTypes != null ? Set.copyOf(eventTypes) : Set.of();
    }
}

