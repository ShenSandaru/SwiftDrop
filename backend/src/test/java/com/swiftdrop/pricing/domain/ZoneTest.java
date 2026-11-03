package com.swiftdrop.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ZoneTest {

    @Test
    void newZoneHasExpectedDefaultsAndNormalizedFields() {
        Zone zone = new Zone("  colombo_01  ", "  Colombo District  ");

        assertThat(zone.getCode()).isEqualTo("COLOMBO_01");
        assertThat(zone.getName()).isEqualTo("Colombo District");
        assertThat(zone.isActive()).isTrue();
        assertThat(zone.getCreatedAt()).isNotNull();
        assertThat(zone.getVersion()).isZero();
        assertThat(zone.getId()).as("generated when persisted").isNull();
    }

    @Test
    void newZoneCanBeCreatedInactive() {
        Zone zone = new Zone("KANDY", "Kandy Central", false);

        assertThat(zone.isActive()).isFalse();
    }

    @Test
    void zoneCanBeDeactivatedAndReactivated() {
        Zone zone = new Zone("GALLE", "Galle Port");
        assertThat(zone.isActive()).isTrue();

        zone.deactivate();
        assertThat(zone.isActive()).isFalse();

        zone.activate();
        assertThat(zone.isActive()).isTrue();
    }

    @Test
    void codeCannotBeNullOrBlank() {
        assertThatThrownBy(() -> new Zone(null, "Colombo"))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new Zone("", "Colombo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");

        assertThatThrownBy(() -> new Zone("   ", "Colombo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void nameCannotBeNullOrBlank() {
        assertThatThrownBy(() -> new Zone("CMB", null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new Zone("CMB", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");

        assertThatThrownBy(() -> new Zone("CMB", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }
}

