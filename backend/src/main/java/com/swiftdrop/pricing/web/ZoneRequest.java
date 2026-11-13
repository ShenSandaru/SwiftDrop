package com.swiftdrop.pricing.web;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating or updating a delivery zone.
 */
public record ZoneRequest(
        @NotBlank(message = "code is required")
        @Size(max = 50, message = "code must be at most 50 characters")
        String code,

        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        UUID hubId,
        Boolean active,
        Long version
) {
    public ZoneRequest(String code, String name, Boolean active, Long version) {
        this(code, name, null, active, version);
    }
}

