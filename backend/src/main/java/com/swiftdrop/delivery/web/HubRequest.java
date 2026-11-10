package com.swiftdrop.delivery.web;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.swiftdrop.delivery.application.HubAdministration.HubDetails;

/**
 * Creates or changes a hub. The code is used on creation only; updates ignore it.
 *
 * @param zoneId the pricing zone the hub serves as its branch; may be empty for the central hub
 * @param active on update only; {@code null} keeps the hub active
 */
record HubRequest(
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String city,
        UUID zoneId,
        boolean central,
        Boolean active) {

    HubDetails details() {
        return new HubDetails(name, city, zoneId, central);
    }

    boolean activeOrDefault() {
        return active == null || active;
    }
}
