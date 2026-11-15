package com.swiftdrop.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** The route rule of ADR 0009 §1 and ADR 0011 §2. */
class HubRouteTest {

    private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");

    private final Hub colombo = new Hub("CMB", "Colombo hub", "Colombo", UUID.randomUUID(), true, NOW);
    private final Hub kandy = new Hub("KDY", "Kandy branch", "Kandy", UUID.randomUUID(), false, NOW);
    private final Hub jaffna = new Hub("JAF", "Jaffna branch", "Jaffna", UUID.randomUUID(), false, NOW);

    @Test
    void twoBranchesTravelThroughTheCentralHub() {
        HubRoute route = HubRoute.between(kandy, jaffna, colombo, false);

        assertThat(route.hubs()).containsExactly(kandy.getId(), colombo.getId(), jaffna.getId());
        assertThat(route.nextAfter(kandy.getId())).isEqualTo(colombo.getId());
        assertThat(route.nextAfter(colombo.getId())).isEqualTo(jaffna.getId());
        assertThat(route.nextAfter(jaffna.getId())).isNull();
    }

    @Test
    void fromOrToTheCentralHubThereIsNoDetour() {
        assertThat(HubRoute.between(colombo, jaffna, colombo, false).hubs()).containsExactly(colombo.getId(), jaffna.getId());
        assertThat(HubRoute.between(kandy, colombo, colombo, false).hubs()).containsExactly(kandy.getId(), colombo.getId());
    }

    @Test
    void withinOneBranchTheRouteIsThatHubOnly() {
        HubRoute route = HubRoute.between(kandy, kandy, colombo, false);

        assertThat(route.hubs()).containsExactly(kandy.getId());
        assertThat(route.nextAfter(kandy.getId())).isNull();
    }

    @Test
    void betweenBranchesWithoutACentralHubThereIsNoRoute() {
        assertThatThrownBy(() -> HubRoute.between(kandy, jaffna, null, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No central hub is configured");
    }

    @Test
    void returnTripVisitsTheSameHubsBackwards() {
        HubRoute back = HubRoute.between(kandy, jaffna, colombo, false).reversed();

        assertThat(back.hubs()).containsExactly(jaffna.getId(), colombo.getId(), kandy.getId());
    }

    @Test
    void hubOffTheRouteHasNoNextHub() {
        HubRoute route = HubRoute.between(colombo, jaffna, colombo, false);

        assertThatThrownBy(() -> route.nextAfter(kandy.getId())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void linkedBranchesSkipTheCentralHub() {
        HubRoute route = HubRoute.between(kandy, jaffna, colombo, true);

        assertThat(route.via()).isNull();
        assertThat(route.hubs()).containsExactly(kandy.getId(), jaffna.getId());
        assertThat(route.nextAfter(kandy.getId())).isEqualTo(jaffna.getId());
        assertThat(route.reversed().hubs()).containsExactly(jaffna.getId(), kandy.getId());
    }

    @Test
    void linkedBranchesNeedNoCentralHub() {
        assertThat(HubRoute.between(kandy, jaffna, null, true).hubs())
                .containsExactly(kandy.getId(), jaffna.getId());
    }
}
