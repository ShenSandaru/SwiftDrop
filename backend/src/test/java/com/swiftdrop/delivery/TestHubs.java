package com.swiftdrop.delivery;

import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import com.swiftdrop.delivery.api.ParcelHubOperations;
import com.swiftdrop.delivery.api.Staff;

/**
 * A minimal hub network for integration tests that book parcels (ADR 0009 §1): Colombo is the
 * central hub and the branch of {@link #COLOMBO_ZONE}, Kandy the branch of {@link #KANDY_ZONE}.
 * Book with these zone IDs; random zones are not served and booking them answers 409.
 */
public final class TestHubs {

    public static final UUID COLOMBO_ZONE = UUID.fromString("00000000-0000-0000-0000-00000000c0b0");
    public static final UUID KANDY_ZONE = UUID.fromString("00000000-0000-0000-0000-00000000ca4d");

    private TestHubs() {
    }

    /** Creates the two hubs unless they exist; safe to call before every test. */
    public static void install(JdbcTemplate jdbc) {
        insert(jdbc, "CMB", "Colombo hub", "Colombo", COLOMBO_ZONE, true);
        insert(jdbc, "KDY", "Kandy branch", "Kandy", KANDY_ZONE, false);
    }

    /** The served zone for a city name used in tests: Kandy, otherwise Colombo. */
    public static UUID zoneFor(String city) {
        return "Kandy".equalsIgnoreCase(city) ? KANDY_ZONE : COLOMBO_ZONE;
    }

    /**
     * Moves a picked-up parcel along its route to its destination hub, as a dispatcher does
     * (ADR 0009 §3): scan at the origin hub, then a line-haul leg and a scan for every further hub.
     */
    public static void toDestinationHub(ParcelHubOperations hubOperations, JdbcTemplate jdbc, UUID parcelId,
            Staff staff) {
        Map<String, Object> route = jdbc.queryForMap(
                "SELECT origin_hub_id, via_hub_id, destination_hub_id FROM parcels WHERE id = ?", parcelId);
        UUID origin = (UUID) route.get("origin_hub_id");
        UUID via = (UUID) route.get("via_hub_id");
        UUID destination = (UUID) route.get("destination_hub_id");
        hubOperations.scanAtHub(parcelId, origin, staff);
        if (via != null) {
            hubOperations.sendToNextHub(parcelId, staff);
            hubOperations.scanAtHub(parcelId, via, staff);
        }
        if (!destination.equals(origin)) {
            hubOperations.sendToNextHub(parcelId, staff);
            hubOperations.scanAtHub(parcelId, destination, staff);
        }
    }

    /**
     * The one admin account (created if there is none yet). Admins work everywhere (ADR 0012 §2), so tests
     * that need a dispatcher who can act on any parcel, including parcels of zones without a hub, use it.
     */
    public static UUID adminUser(JdbcTemplate jdbc) {
        java.util.List<UUID> existing = jdbc.queryForList(
                "SELECT user_id FROM user_roles WHERE role_name = 'ADMIN'", UUID.class);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, 'Test admin', TRUE, now())",
                id, "admin-" + id + "@example.com");
        jdbc.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'ADMIN')", id);
        return id;
    }

    /**
     * Makes the user work for every hub that exists now (ADR 0012 §1), so a test dispatcher can act on any
     * parcel. Call it after the hubs are created.
     */
    public static void worksAtAllHubs(JdbcTemplate jdbc, UUID userId) {
        jdbc.update("INSERT INTO hub_staff (id, user_id, hub_id, created_by, created_at) "
                + "SELECT gen_random_uuid(), ?, id, ?, now() FROM hubs ON CONFLICT DO NOTHING", userId, UUID.randomUUID());
    }

    /** Makes the user work for one hub, by code (ADR 0012 §1). */
    public static void worksAt(JdbcTemplate jdbc, UUID userId, String hubCode) {
        jdbc.update("INSERT INTO hub_staff (id, user_id, hub_id, created_by, created_at) "
                + "SELECT gen_random_uuid(), ?, id, ?, now() FROM hubs WHERE code = ? ON CONFLICT DO NOTHING",
                userId, UUID.randomUUID(), hubCode);
    }

    private static void insert(JdbcTemplate jdbc, String code, String name, String city, UUID zoneId, boolean central) {
        jdbc.update("INSERT INTO hubs (id, code, name, city, zone_id, central, active, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, TRUE, now(), now()) ON CONFLICT (code) DO NOTHING",
                UUID.randomUUID(), code, name, city, zoneId, central);
    }
}
