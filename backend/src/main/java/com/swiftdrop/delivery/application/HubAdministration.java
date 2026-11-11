package com.swiftdrop.delivery.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.domain.Hub;
import com.swiftdrop.delivery.domain.HubRepository;

/**
 * Admins create, change and deactivate hubs (ADR 0009 §1). Uniqueness (code, name, zone, the single
 * central hub) is checked here for a clear message and enforced by the database for races.
 */
@Service
public class HubAdministration {

    private final HubRepository hubs;
    private final Clock clock;

    @Autowired
    public HubAdministration(HubRepository hubs) {
        this(hubs, Clock.systemUTC());
    }

    HubAdministration(HubRepository hubs, Clock clock) {
        this.hubs = hubs;
        this.clock = clock;
    }

    /** All hubs, active and inactive, by name. */
    @Transactional(readOnly = true)
    public List<Hub> list() {
        return hubs.findAllByOrderByNameAsc();
    }

    /**
     * @throws IllegalArgumentException if a value is invalid
     * @throws ParcelConflictException if the code, name or zone is taken, or a second central hub is requested
     */
    @Transactional
    public Hub create(HubDetails details, String code) {
        Objects.requireNonNull(details, "details");
        Instant now = clock.instant();
        Hub hub = new Hub(code, details.name(), details.city(), details.zoneId(), details.central(), now);
        if (hubs.existsByCode(hub.getCode())) {
            throw new ParcelConflictException("A hub with this code already exists");
        }
        if (hubs.existsByNameIgnoreCase(hub.getName())) {
            throw new ParcelConflictException("A hub with this name already exists");
        }
        checkZoneAndCentral(hub.getId(), details);
        return save(hub);
    }

    /**
     * Changes everything but the code.
     *
     * @throws ParcelNotFoundException if the hub does not exist
     */
    @Transactional
    public Hub update(UUID hubId, HubDetails details, boolean active) {
        Objects.requireNonNull(details, "details");
        Hub hub = hubs.findById(hubId).orElseThrow(ParcelNotFoundException::new);
        Instant now = clock.instant();
        checkZoneAndCentral(hubId, details);
        hub.update(details.name(), details.city(), details.zoneId(), details.central(), now);
        if (hub.isActive() != active) {
            hub.setActive(active, now);
        }
        return save(hub);
    }

    private void checkZoneAndCentral(UUID hubId, HubDetails details) {
        if (details.zoneId() != null) {
            hubs.findByZoneIdAndActiveTrue(details.zoneId())
                    .filter(other -> !other.getId().equals(hubId))
                    .ifPresent(other -> {
                        throw new ParcelConflictException("This zone is already served by hub " + other.getCode());
                    });
        }
        if (details.central()) {
            hubs.findFirstByCentralTrueAndActiveTrue()
                    .filter(other -> !other.getId().equals(hubId))
                    .ifPresent(other -> {
                        throw new ParcelConflictException("Hub " + other.getCode() + " is already the central hub");
                    });
        }
    }

    private Hub save(Hub hub) {
        try {
            return hubs.saveAndFlush(hub);
        } catch (DataIntegrityViolationException e) {
            // A concurrent change took the code, name, zone or central flag first
            throw new ParcelConflictException("The hub conflicts with an existing hub");
        }
    }

    /** What an admin may set on a hub. */
    public record HubDetails(String name, String city, UUID zoneId, boolean central) {
    }
}
