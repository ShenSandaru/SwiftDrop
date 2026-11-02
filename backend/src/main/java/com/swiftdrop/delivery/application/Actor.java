package com.swiftdrop.delivery.application;

import java.util.Objects;
import java.util.UUID;

import com.swiftdrop.delivery.domain.ActorRole;

/**
 * Who performs a parcel operation, as recorded in the status history.
 *
 * @param userId {@code null} only for the merchant API, which acts without a user
 */
public record Actor(UUID userId, ActorRole role) {

    public Actor {
        Objects.requireNonNull(role, "role");
        if (userId == null && role != ActorRole.MERCHANT) {
            throw new IllegalArgumentException("Only the merchant API acts without a user");
        }
    }

    public static Actor customer(UUID userId) {
        return new Actor(Objects.requireNonNull(userId, "userId"), ActorRole.CUSTOMER);
    }

    public static Actor courier(UUID userId) {
        return new Actor(Objects.requireNonNull(userId, "userId"), ActorRole.COURIER);
    }

    public static Actor merchantApi() {
        return new Actor(null, ActorRole.MERCHANT);
    }
}
