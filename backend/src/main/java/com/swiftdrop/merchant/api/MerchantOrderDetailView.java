package com.swiftdrop.merchant.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * One of the merchant's orders with its status timeline, for the merchant portal (Phase 5). Built only from
 * {@code delivery.api} types, and only from what the public tracking view already shows: no names, phone
 * numbers, street addresses, notes or actors.
 *
 * @param history status timeline, oldest first
 */
public record MerchantOrderDetailView(
        UUID id,
        String trackingNumber,
        String merchantOrderReference,
        ParcelStatus status,
        String size,
        int weightGrams,
        String pickupCity,
        String dropoffCity,
        boolean courierAssigned,
        Instant createdAt,
        List<StatusEvent> history) {

    public MerchantOrderDetailView {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(trackingNumber, "trackingNumber must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        history = history != null ? List.copyOf(history) : List.of();
    }

    /**
     * One step of the timeline.
     *
     * @param hubName the hub of a hub step, or {@code null}
     * @param attempt the delivery attempt of a delivery step, or {@code null}
     */
    public record StatusEvent(ParcelStatus status, Instant occurredAt, String hubName, Integer attempt) {

        public StatusEvent {
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }
}
