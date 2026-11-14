package com.swiftdrop.pricing.api;

import java.util.Objects;
import java.util.UUID;

/** An active delivery zone as other modules may see it: no pricing details. */
public record ServiceZone(UUID id, String code, String name) {

    public ServiceZone {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
