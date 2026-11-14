package com.swiftdrop.delivery.web;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.delivery.application.HubNetwork;
import com.swiftdrop.pricing.api.ServiceZone;
import com.swiftdrop.pricing.api.ZoneDirectory;

/**
 * Active delivery zones that are currently served by an active hub, for customer booking forms.
 */
@RestController
class DeliveryZoneController {

    private final ZoneDirectory zones;
    private final HubNetwork hubs;

    DeliveryZoneController(ZoneDirectory zones, HubNetwork hubs) {
        this.zones = zones;
        this.hubs = hubs;
    }

    @GetMapping("/api/v1/delivery-zones")
    List<ServiceZone> availableZones() {
        Set<UUID> served = hubs.servedZoneIds();
        return zones.findActiveZones().stream()
                .filter(zone -> served.contains(zone.id()))
                .toList();
    }
}

