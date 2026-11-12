package com.swiftdrop.pricing.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.pricing.domain.Zone;

/**
 * Delivery zone representation for admin management, including active flag and optimistic locking version.
 */
public record ZoneAdminResponse(
        UUID id,
        String code,
        String name,
        boolean active,
        long version,
        Instant createdAt
) {

    public static ZoneAdminResponse from(Zone zone) {
        return new ZoneAdminResponse(
                zone.getId(),
                zone.getCode(),
                zone.getName(),
                zone.isActive(),
                zone.getVersion(),
                zone.getCreatedAt()
        );
    }
}

