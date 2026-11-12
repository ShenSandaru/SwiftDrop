package com.swiftdrop.pricing.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.pricing.api.PricingConflictException;
import com.swiftdrop.pricing.api.PricingNotFoundException;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;

/**
 * Administrative operations on delivery zones (ADM-3, contract §4.2).
 * Admin-only: create, update, and toggle active status.
 */
@Service
public class ZoneAdministration {

    private final ZoneRepository zones;
    private final Clock clock;

    @Autowired
    public ZoneAdministration(ZoneRepository zones) {
        this(zones, Clock.systemUTC());
    }

    ZoneAdministration(ZoneRepository zones, Clock clock) {
        this.zones = zones;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Zone> list() {
        return zones.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Zone get(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return zones.findById(id).orElseThrow(() -> new PricingNotFoundException("Zone not found"));
    }

    @Transactional
    public Zone create(String code, String name) {
        String normalizedCode = Zone.normalizeCode(code);
        String normalizedName = Zone.normalizeName(name);

        if (zones.existsByCodeIgnoreCase(normalizedCode)) {
            throw new PricingConflictException("A zone with code '" + normalizedCode + "' already exists");
        }
        if (zones.existsByNameIgnoreCase(normalizedName)) {
            throw new PricingConflictException("A zone with name '" + normalizedName + "' already exists");
        }

        Zone zone = new Zone(normalizedCode, normalizedName);
        return save(zone);
    }

    @Transactional
    public Zone update(UUID id, String code, String name, boolean active, long expectedVersion) {
        Zone zone = get(id);
        if (zone.getVersion() != expectedVersion) {
            throw new PricingConflictException("Zone has been modified by another transaction. Please reload.");
        }

        if (code != null && !code.isBlank()) {
            String normalizedCode = Zone.normalizeCode(code);
            if (!zone.getCode().equalsIgnoreCase(normalizedCode) && zones.existsByCodeIgnoreCase(normalizedCode)) {
                throw new PricingConflictException("A zone with code '" + normalizedCode + "' already exists");
            }
            zone.setCode(normalizedCode);
        }

        String normalizedName = Zone.normalizeName(name);
        if (!zone.getName().equalsIgnoreCase(normalizedName) && zones.existsByNameIgnoreCase(normalizedName)) {
            throw new PricingConflictException("A zone with name '" + normalizedName + "' already exists");
        }
        zone.setName(normalizedName);
        zone.setActive(active);

        return save(zone);
    }

    private Zone save(Zone zone) {
        try {
            return zones.saveAndFlush(zone);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new PricingConflictException("Zone has been modified by another transaction. Please reload.");
        } catch (DataIntegrityViolationException e) {
            throw new PricingConflictException("The zone conflicts with an existing zone (duplicate code or name)");
        }
    }
}

