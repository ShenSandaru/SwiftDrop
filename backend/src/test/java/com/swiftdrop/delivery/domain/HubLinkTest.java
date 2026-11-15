package com.swiftdrop.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** The pair order of ADR 0011 §1. */
class HubLinkTest {

    private static final Instant NOW = Instant.parse("2026-11-14T08:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();
    // The first is smaller byte by byte (PostgreSQL), but larger as signed longs (UUID.compareTo)
    private static final UUID LOW = UUID.fromString("10000000-0000-0000-0000-000000000000");
    private static final UUID HIGH = UUID.fromString("f0000000-0000-0000-0000-000000000000");

    @Test
    void storesThePairOnceWhicheverOrderItIsGivenIn() {
        HubLink link = new HubLink(HIGH, LOW, ADMIN, NOW);
        HubLink same = new HubLink(LOW, HIGH, ADMIN, NOW);

        assertThat(link.getFirstHubId()).isEqualTo(LOW).isEqualTo(same.getFirstHubId());
        assertThat(link.getSecondHubId()).isEqualTo(HIGH).isEqualTo(same.getSecondHubId());
        assertThat(link.getCreatedBy()).isEqualTo(ADMIN);
        assertThat(link.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void ordersIdsTheWayTheDatabaseDoes() {
        assertThat(HIGH.compareTo(LOW)).isNegative();

        assertThat(HubLink.first(HIGH, LOW)).isEqualTo(LOW);
        assertThat(HubLink.second(HIGH, LOW)).isEqualTo(HIGH);
    }

    @Test
    void aHubCannotBeLinkedToItself() {
        assertThatThrownBy(() -> new HubLink(LOW, LOW, ADMIN, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A hub cannot be linked to itself");
    }
}
