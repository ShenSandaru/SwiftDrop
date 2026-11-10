package com.swiftdrop.delivery.api;

import java.util.Objects;
import java.util.UUID;

/**
 * The dispatcher or admin performing a hub operation, as recorded in the status history.
 * {@code dispatch} has already checked the caller's role.
 */
public record Staff(UUID userId, boolean admin) {

    public Staff {
        Objects.requireNonNull(userId, "userId must not be null");
    }

    public static Staff dispatcher(UUID userId) {
        return new Staff(userId, false);
    }

    public static Staff admin(UUID userId) {
        return new Staff(userId, true);
    }
}
