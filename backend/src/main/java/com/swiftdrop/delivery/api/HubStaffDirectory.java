package com.swiftdrop.delivery.api;

import java.util.Set;
import java.util.UUID;

/**
 * Which staff work for which hubs (ADR 0012), for {@code dispatch}: a hub dispatcher sees and assigns
 * only the couriers of their own hubs.
 */
public interface HubStaffDirectory {

    /** Whether the user works for every hub: an admin or a head office dispatcher. */
    boolean worksEverywhere(UUID userId);

    /**
     * The users who work for at least one hub the given user works for, the user included. Empty
     * for someone with no hub. Staff who work everywhere are not listed; check {@link #worksEverywhere}.
     */
    Set<UUID> colleaguesOf(UUID userId);
}
