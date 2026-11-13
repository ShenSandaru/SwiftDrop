package com.swiftdrop.pricing.application;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.pricing.api.ServiceZone;
import com.swiftdrop.pricing.api.ZoneDirectory;
import com.swiftdrop.pricing.domain.ZoneRepository;

@Service
@Transactional(readOnly = true)
class ActiveZoneDirectory implements ZoneDirectory {

    private final ZoneRepository zones;

    ActiveZoneDirectory(ZoneRepository zones) {
        this.zones = zones;
    }

    @Override
    public List<ServiceZone> findActiveZones() {
        return zones.findByActiveTrue().stream()
                .map(zone -> new ServiceZone(zone.getId(), zone.getCode(), zone.getName()))
                .sorted(Comparator.comparing(ServiceZone::name))
                .toList();
    }
}
