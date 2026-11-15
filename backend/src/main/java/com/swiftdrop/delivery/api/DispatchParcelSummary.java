package com.swiftdrop.delivery.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.swiftdrop.pricing.api.ParcelSize;

/**
 * Operational parcel view for dispatch; address contact details remain behind the parcel detail API.
 *
 * @param originHubCode route hubs (ADR 0009 §1); {@code null} before pickup and for legacy parcels
 * @param viaHubCode the central hub when the route passes through it, otherwise {@code null}
 * @param currentHubCode the hub the parcel is at, while {@code AT_HUB}
 * @param nextHubCode the hub it travels to, while {@code IN_TRANSIT}
 * @param expectedHubId the hub to scan it in at next, when {@link DispatchAction#SCAN_AT_HUB} applies
 * @param deliveryAttempts delivery attempts started so far
 * @param readOnly the caller's hubs are on the parcel's route but not responsible for it now (ADR 0012
 *                 §3): they may follow it, so no actions are offered
 * @param actions what a dispatcher may do next
 */
public record DispatchParcelSummary(
        UUID id,
        String trackingNumber,
        ParcelStatus status,
        ParcelSource source,
        String merchantOrderReference,
        UUID assignedCourierId,
        ParcelSize size,
        int weightGrams,
        String pickupCity,
        String dropoffCity,
        Instant createdAt,
        Instant updatedAt,
        String originHubCode,
        String viaHubCode,
        String destinationHubCode,
        String currentHubCode,
        String nextHubCode,
        UUID expectedHubId,
        String expectedHubCode,
        int deliveryAttempts,
        FailureReason lastFailureReason,
        boolean readOnly,
        Set<DispatchAction> actions
) {
    public DispatchParcelSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(pickupCity, "pickupCity");
        Objects.requireNonNull(dropoffCity, "dropoffCity");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        actions = actions == null ? Set.of() : Set.copyOf(actions);
    }

    /** Without hub details and actions, as before ADR 0009. */
    public DispatchParcelSummary(UUID id, String trackingNumber, ParcelStatus status, ParcelSource source,
            String merchantOrderReference, UUID assignedCourierId, ParcelSize size, int weightGrams, String pickupCity,
            String dropoffCity, Instant createdAt, Instant updatedAt) {
        this(id, trackingNumber, status, source, merchantOrderReference, assignedCourierId, size, weightGrams,
                pickupCity, dropoffCity, createdAt, updatedAt, null, null, null, null, null, null, null, 0, null, false,
                Set.of());
    }
}
