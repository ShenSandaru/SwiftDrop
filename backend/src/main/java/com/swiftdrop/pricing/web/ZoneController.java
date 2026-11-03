package com.swiftdrop.pricing.web;

import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.pricing.domain.ZoneRepository;

/**
 * Active delivery zones for the booking form (contract §4.2). Any authenticated user may read them;
 * public access, as the contract proposes, needs a rule in {@code SecurityConfiguration}.
 */
@RestController
class ZoneController {

    private final ZoneRepository zones;

    ZoneController(ZoneRepository zones) {
        this.zones = zones;
    }

    @GetMapping("/api/v1/zones")
    List<ZoneResponse> activeZones() {
        return zones.findByActiveTrue().stream()
                .map(ZoneResponse::from)
                .sorted(Comparator.comparing(ZoneResponse::name))
                .toList();
    }
}
