package com.swiftdrop.delivery.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.domain.Hub;
import com.swiftdrop.delivery.domain.HubRepository;
import com.swiftdrop.delivery.domain.HubRoute;

/**
 * Routes parcels through the hub network (ADR 0009 §1): every pickup and drop-off zone must be
 * served by an active branch hub, and a parcel between two branches that are not central passes
 * through the central hub.
 */
@Service
public class HubNetwork {

    private final HubRepository hubs;

    public HubNetwork(HubRepository hubs) {
        this.hubs = hubs;
    }

    /**
     * The route between two zones.
     *
     * @throws ParcelConflictException if a zone has no active hub, or the route needs a central hub
     *                                 and none is active
     */
    @Transactional(readOnly = true)
    public HubRoute routeFor(UUID pickupZoneId, UUID dropoffZoneId) {
        Hub origin = branchOf(pickupZoneId, "pickup");
        Hub destination = branchOf(dropoffZoneId, "drop-off");
        Hub central = hubs.findFirstByCentralTrueAndActiveTrue().orElse(null);
        try {
            return HubRoute.between(origin, destination, central);
        } catch (IllegalStateException e) {
            throw new ParcelConflictException("No central hub is configured");
        }
    }

    /** Checks at booking that both zones are served (ADR 0009 §1). */
    public void requireServed(UUID pickupZoneId, UUID dropoffZoneId) {
        routeFor(pickupZoneId, dropoffZoneId);
    }

    private Hub branchOf(UUID zoneId, String which) {
        Objects.requireNonNull(zoneId, "zoneId");
        return hubs.findByZoneIdAndActiveTrue(zoneId)
                .orElseThrow(() -> new ParcelConflictException("The " + which + " zone is not served by a hub"));
    }
}
