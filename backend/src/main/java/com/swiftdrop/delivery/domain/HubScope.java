package com.swiftdrop.delivery.domain;

import java.util.Set;
import java.util.UUID;

/**
 * The hubs a staff member works for (ADR 0012 §2): everywhere (an admin or a head office
 * dispatcher), or the given hubs. No hubs means no access.
 */
public record HubScope(boolean everywhere, Set<UUID> hubIds) {

    public static final HubScope EVERYWHERE = new HubScope(true, Set.of());
    public static final HubScope NONE = new HubScope(false, Set.of());

    public HubScope {
        hubIds = hubIds == null ? Set.of() : Set.copyOf(hubIds);
    }

    public static HubScope of(Set<UUID> hubIds) {
        return hubIds == null || hubIds.isEmpty() ? NONE : new HubScope(false, hubIds);
    }

    public boolean includes(UUID hubId) {
        return everywhere || (hubId != null && hubIds.contains(hubId));
    }

    public boolean isEmpty() {
        return !everywhere && hubIds.isEmpty();
    }
}
