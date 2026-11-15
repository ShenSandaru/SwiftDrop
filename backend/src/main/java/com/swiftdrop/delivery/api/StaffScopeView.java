package com.swiftdrop.delivery.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Where a staff member works (ADR 0012 §2), for the dispatcher portal to say why a list is short or
 * empty.
 *
 * @param everywhere an admin or a head office dispatcher
 * @param hubs       the hubs they work for; empty when {@code everywhere} or when they have none
 */
public record StaffScopeView(boolean everywhere, List<HubRef> hubs) {

    public StaffScopeView {
        hubs = hubs == null ? List.of() : List.copyOf(hubs);
    }

    public record HubRef(UUID id, String code, String name) {

        public HubRef {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(name, "name");
        }
    }
}
