package com.swiftdrop.delivery;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

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

    private static void insert(JdbcTemplate jdbc, String code, String name, String city, UUID zoneId, boolean central) {
        jdbc.update("INSERT INTO hubs (id, code, name, city, zone_id, central, active, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, TRUE, now(), now()) ON CONFLICT (code) DO NOTHING",
                UUID.randomUUID(), code, name, city, zoneId, central);
    }
}
