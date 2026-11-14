package com.swiftdrop.delivery.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.HubRoute;
import com.swiftdrop.delivery.domain.Parcel;

/**
 * Estimates the delivery date from the parcel's route: a parcel that stays in one branch arrives
 * soonest, one between two hubs later, one through the central hub last. The days are counted from
 * the pickup, or from the booking while the parcel waits for pickup. An estimate, not a promise: it
 * does not move after a failed attempt, so it can lie in the past for a late parcel.
 */
@Component
class DeliveryEstimator {

    private final HubNetwork network;
    private final int sameHubDays;
    private final int directDays;
    private final int viaCentralDays;
    private final ZoneId businessZone;

    @Autowired
    DeliveryEstimator(HubNetwork network,
            @Value("${swiftdrop.delivery.estimate.same-hub-days:1}") int sameHubDays,
            @Value("${swiftdrop.delivery.estimate.direct-days:2}") int directDays,
            @Value("${swiftdrop.delivery.estimate.via-central-days:3}") int viaCentralDays,
            @Value("${swiftdrop.delivery.business-time-zone:Asia/Colombo}") String businessZone) {
        this(network, sameHubDays, directDays, viaCentralDays, ZoneId.of(businessZone));
    }

    DeliveryEstimator(HubNetwork network, int sameHubDays, int directDays, int viaCentralDays, ZoneId businessZone) {
        if (sameHubDays < 0 || directDays < sameHubDays || viaCentralDays < directDays) {
            throw new IllegalStateException("swiftdrop.delivery.estimate.* days must not be negative and must not "
                    + "get shorter for longer routes");
        }
        this.network = network;
        this.sameHubDays = sameHubDays;
        this.directDays = directDays;
        this.viaCentralDays = viaCentralDays;
        this.businessZone = businessZone;
    }

    /**
     * @param pickedUpAt when the parcel was picked up, or {@code null} while it waits for pickup
     * @return empty when the parcel is finished or on its way back, or its route is not known
     */
    Optional<LocalDate> estimate(Parcel parcel, Instant pickedUpAt) {
        ParcelStatus status = parcel.getStatus();
        if (status.isFinal() || status == ParcelStatus.RETURNING) {
            return Optional.empty();
        }
        Instant start = pickedUpAt != null ? pickedUpAt : parcel.getCreatedAt();
        return hubsOnRoute(parcel).map(hubs -> LocalDate.ofInstant(start, businessZone).plusDays(daysFor(hubs)));
    }

    private int daysFor(int hubs) {
        return switch (hubs) {
            case 1 -> sameHubDays;
            case 2 -> directDays;
            default -> viaCentralDays;
        };
    }

    /** From the stored route after pickup; before pickup, the route the parcel would get today. */
    private Optional<Integer> hubsOnRoute(Parcel parcel) {
        if (parcel.getOriginHubId() != null) {
            return Optional.of(new HubRoute(parcel.getOriginHubId(), parcel.getViaHubId(),
                    parcel.getDestinationHubId()).hubs().size());
        }
        if (parcel.getStatus() != ParcelStatus.CREATED) {
            // Picked up without a route, before the hub network (ADR 0006)
            return Optional.empty();
        }
        UUID pickupZone = parcel.getPickup().getZoneId();
        UUID dropoffZone = parcel.getDropoff().getZoneId();
        if (pickupZone == null || dropoffZone == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(network.routeFor(pickupZone, dropoffZone).hubs().size());
        } catch (ParcelConflictException notServed) {
            return Optional.empty();
        }
    }
}
