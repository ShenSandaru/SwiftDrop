package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.merchant.api.MerchantOrderDetailView;

/** {@code GET /api/v1/merchants/me/orders/{id}}: the order and its status timeline, oldest first. */
public record MerchantOrderDetailResponse(UUID id, String trackingNumber, String merchantOrderReference,
        ParcelStatus status, String size, int weightGrams, String pickupCity, String dropoffCity,
        boolean courierAssigned, Instant createdAt, List<StatusEvent> history) {

    /** One timeline step; {@code hubName} and {@code attempt} are set only for hub and delivery steps. */
    public record StatusEvent(ParcelStatus status, Instant occurredAt, String hubName, Integer attempt) {
    }

    static MerchantOrderDetailResponse from(MerchantOrderDetailView view) {
        return new MerchantOrderDetailResponse(view.id(), view.trackingNumber(), view.merchantOrderReference(),
                view.status(), view.size(), view.weightGrams(), view.pickupCity(), view.dropoffCity(),
                view.courierAssigned(), view.createdAt(),
                view.history().stream()
                        .map(event -> new StatusEvent(event.status(), event.occurredAt(), event.hubName(),
                                event.attempt()))
                        .toList());
    }
}
