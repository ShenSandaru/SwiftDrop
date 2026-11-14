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
import com.swiftdrop.delivery.domain.HubLink;
import com.swiftdrop.delivery.domain.HubLinkRepository;
import com.swiftdrop.delivery.domain.HubRepository;

/**
 * Admins create, change and deactivate hubs (ADR 0009 §1) and link branches that exchange parcels
 * directly (ADR 0011 §1). Uniqueness (code, name, zone, the single central hub, one link per pair)
 * is checked here for a clear message and enforced by the database for races.
 */
@Service
public class HubAdministration {

    private final HubRepository hubs;
    private final HubLinkRepository links;
    private final Clock clock;

    @Autowired
    public HubAdministration(HubRepository hubs, HubLinkRepository links) {
        this(hubs, links, Clock.systemUTC());
    }

    HubAdministration(HubRepository hubs, HubLinkRepository links, Clock clock) {
        this.hubs = hubs;
        this.links = links;
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

    /**
     * Deletes a hub from the delivery network.
     *
     * @throws ParcelNotFoundException if the hub does not exist
     * @throws ParcelConflictException if the hub is referenced by existing parcels
     */
    @Transactional
    public void delete(UUID hubId) {
        Hub hub = hubs.findById(hubId).orElseThrow(ParcelNotFoundException::new);
        try {
            hubs.delete(hub);
            hubs.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ParcelConflictException("Cannot delete hub '" + hub.getName()
                    + "': it is referenced by existing parcels or deliveries. Deactivate the hub instead.");
        }
    }

    /** All direct links, oldest first. */
    @Transactional(readOnly = true)
    public List<HubLink> links() {
        return links.findAllByOrderByCreatedAtAsc();
    }

    /**
     * Links two branches so that parcels between them skip the central hub (ADR 0011 §1). The order
     * of the two hubs does not matter.
     *
     * @throws IllegalArgumentException if both IDs name the same hub
     * @throws ParcelNotFoundException if a hub does not exist
     * @throws ParcelConflictException if a hub is central or inactive, or the hubs are already linked
     */
    @Transactional
    public HubLink link(UUID hubId, UUID otherHubId, UUID adminId) {
        Objects.requireNonNull(hubId, "hubId");
        Objects.requireNonNull(otherHubId, "otherHubId");
        Objects.requireNonNull(adminId, "adminId");
        if (hubId.equals(otherHubId)) {
            throw new IllegalArgumentException("A hub cannot be linked to itself");
        }
        requireLinkable(hubId);
        requireLinkable(otherHubId);
        if (links.areLinked(hubId, otherHubId)) {
            throw new ParcelConflictException("These hubs are already linked");
        }
        try {
            return links.saveAndFlush(new HubLink(hubId, otherHubId, adminId, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // A concurrent request linked the same pair first
            throw new ParcelConflictException("These hubs are already linked");
        }
    }

    /**
     * Removes a link. Parcels already picked up keep their stored route (ADR 0011 §2).
     *
     * @throws IllegalArgumentException if both IDs name the same hub
     * @throws ParcelNotFoundException if the hubs are not linked
     */
    @Transactional
    public void unlink(UUID hubId, UUID otherHubId) {
        Objects.requireNonNull(hubId, "hubId");
        Objects.requireNonNull(otherHubId, "otherHubId");
        links.delete(links.findLink(hubId, otherHubId).orElseThrow(ParcelNotFoundException::new));
    }

    private void requireLinkable(UUID hubId) {
        Hub hub = hubs.findById(hubId).orElseThrow(ParcelNotFoundException::new);
        if (hub.isCentral()) {
            throw new ParcelConflictException("The central hub is already connected to every branch");
        }
        if (!hub.isActive()) {
            throw new ParcelConflictException("Hub " + hub.getCode() + " is not active");
        }
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
