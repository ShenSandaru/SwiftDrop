package com.swiftdrop.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Home hubs of ADR 0012 §1. */
class HubStaffTest {

    private static final Instant NOW = Instant.parse("2026-11-14T08:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    @Test
    void aHubRowNamesItsHub() {
        UUID hub = UUID.randomUUID();
        HubStaff row = HubStaff.atHub(USER, hub, ADMIN, NOW);

        assertThat(row.getUserId()).isEqualTo(USER);
        assertThat(row.getHubId()).isEqualTo(hub);
        assertThat(row.getCreatedBy()).isEqualTo(ADMIN);
        assertThat(row.getCreatedAt()).isEqualTo(NOW);
    }


    @Test
    void aHubRowNeedsAHub() {
        assertThatThrownBy(() -> HubStaff.atHub(USER, null, ADMIN, NOW)).isInstanceOf(NullPointerException.class);
    }
}
