package com.swiftdrop.delivery.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The hubs a parcel passes through (ADR 0009 §1): its origin branch, the central hub when the
 * parcel travels between two branches that are both not central, and its destination branch.
 *
 * @param via the central hub, or {@code null} when the parcel does not pass through it
 */
public record HubRoute(UUID origin, UUID via, UUID destination) {

    public HubRoute {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        if (via != null && (via.equals(origin) || via.equals(destination))) {
            throw new IllegalArgumentException("via must differ from origin and destination");
        }
        if (via != null && origin.equals(destination)) {
            throw new IllegalArgumentException("a route within one hub has no via hub");
        }
    }

    /**
     * The route between two branches.
     *
     * @param central the central hub, needed only when neither branch is central and they differ
     * @throws IllegalStateException if the route needs a central hub and none is given
     */
    public static HubRoute between(Hub origin, Hub destination, Hub central) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        boolean sameHub = origin.getId().equals(destination.getId());
        if (sameHub || origin.isCentral() || destination.isCentral()) {
            return new HubRoute(origin.getId(), null, destination.getId());
        }
        if (central == null) {
            throw new IllegalStateException("No central hub is configured");
        }
        return new HubRoute(origin.getId(), central.getId(), destination.getId());
    }

    /** The hubs in travel order, without repeats. */
    public List<UUID> hubs() {
        List<UUID> hubs = new ArrayList<>(3);
        hubs.add(origin);
        if (via != null) {
            hubs.add(via);
        }
        if (!destination.equals(origin)) {
            hubs.add(destination);
        }
        return List.copyOf(hubs);
    }

    /** The hub after {@code hub} on the route, or {@code null} if {@code hub} is the destination. */
    public UUID nextAfter(UUID hub) {
        List<UUID> hubs = hubs();
        int index = hubs.indexOf(hub);
        if (index < 0) {
            throw new IllegalArgumentException("hub is not on the route");
        }
        return index + 1 < hubs.size() ? hubs.get(index + 1) : null;
    }

    /** The route back to the sender: the same hubs in reverse order. */
    public HubRoute reversed() {
        return new HubRoute(destination, via, origin);
    }
}
