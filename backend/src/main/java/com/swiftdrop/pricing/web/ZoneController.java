package com.swiftdrop.pricing.web;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.pricing.application.ZoneAdministration;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;

/**
 * Delivery zones: active zones for booking forms (contract §4.2), and zone administration for admins (ADM-3).
 */
@RestController
class ZoneController {

    private final ZoneRepository zones;
    private final ZoneAdministration zoneAdministration;

    ZoneController(ZoneRepository zones, ZoneAdministration zoneAdministration) {
        this.zones = zones;
        this.zoneAdministration = zoneAdministration;
    }

    /** Active delivery zones for booking forms (contract §4.2). */
    @GetMapping("/api/v1/zones")
    List<ZoneResponse> activeZones() {
        return zones.findByActiveTrue().stream()
                .map(ZoneResponse::from)
                .sorted(Comparator.comparing(ZoneResponse::name))
                .toList();
    }

    /** All zones (active and inactive) for admin management. */
    @GetMapping(value = "/api/v1/zones", params = "all=true")
    @PreAuthorize("hasRole('ADMIN')")
    List<ZoneAdminResponse> allZones() {
        return zoneAdministration.list().stream()
                .map(ZoneAdminResponse::from)
                .toList();
    }

    @GetMapping("/api/v1/zones/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ZoneAdminResponse getZone(@PathVariable UUID id) {
        return ZoneAdminResponse.from(zoneAdministration.get(id));
    }

    @PostMapping("/api/v1/zones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    ZoneAdminResponse createZone(@Valid @RequestBody ZoneRequest request) {
        Zone zone = zoneAdministration.create(request.code(), request.name(), request.hubId());
        return ZoneAdminResponse.from(zone);
    }

    @PutMapping("/api/v1/zones/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ZoneAdminResponse updateZone(@PathVariable UUID id, @Valid @RequestBody ZoneRequest request) {
        long version = request.version() != null ? request.version() : 0L;
        boolean active = request.active() != null ? request.active() : true;
        Zone zone = zoneAdministration.update(id, request.code(), request.name(), active, version);
        return ZoneAdminResponse.from(zone);
    }

    @DeleteMapping("/api/v1/zones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void deleteZone(@PathVariable UUID id) {
        zoneAdministration.delete(id);
    }
}
