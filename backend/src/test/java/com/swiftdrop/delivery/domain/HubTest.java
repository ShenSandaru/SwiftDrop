package com.swiftdrop.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class HubTest {

    private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");
    private static final UUID ZONE = UUID.randomUUID();

    @Test
    void newHubIsActiveWithANormalizedCode() {
        Hub hub = new Hub(" kdy-1 ", " Kandy branch ", "Kandy", ZONE, false, NOW);

        assertThat(hub.getCode()).isEqualTo("KDY-1");
        assertThat(hub.getName()).isEqualTo("Kandy branch");
        assertThat(hub.isActive()).isTrue();
        assertThat(hub.getId()).isNotNull();
    }

    @Test
    void onlyTheCentralHubMayServeNoZone() {
        assertThat(new Hub("HUB", "Central hub", "Colombo", null, true, NOW).getZoneId()).isNull();
        assertThatThrownBy(() -> new Hub("GAL", "Galle branch", "Galle", null, false, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must serve a zone");
    }

    @Test
    void invalidValuesAreRejected() {
        assertThatThrownBy(() -> new Hub("K", "Kandy", "Kandy", ZONE, false, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("code");
        assertThatThrownBy(() -> new Hub("KDY!", "Kandy", "Kandy", ZONE, false, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("code");
        assertThatThrownBy(() -> new Hub("KDY", " ", "Kandy", ZONE, false, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name");
        assertThatThrownBy(() -> new Hub("KDY", "Kandy", "x".repeat(101), ZONE, false, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("city");
    }

    @Test
    void updateChangesEverythingButTheCode() {
        Hub hub = new Hub("KDY", "Kandy branch", "Kandy", ZONE, false, NOW);
        UUID otherZone = UUID.randomUUID();

        hub.update("Kandy city branch", "Kandy", otherZone, true, NOW.plusSeconds(60));
        hub.setActive(false, NOW.plusSeconds(60));

        assertThat(hub.getCode()).isEqualTo("KDY");
        assertThat(hub.getName()).isEqualTo("Kandy city branch");
        assertThat(hub.getZoneId()).isEqualTo(otherZone);
        assertThat(hub.isCentral()).isTrue();
        assertThat(hub.isActive()).isFalse();
        assertThat(hub.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
    }
}
