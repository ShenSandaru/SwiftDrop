package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.domain.Hub;
import com.swiftdrop.delivery.domain.HubRepository;

class HubNetworkTest {

    private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");
    private static final UUID COLOMBO_ZONE = UUID.randomUUID();
    private static final UUID KANDY_ZONE = UUID.randomUUID();
    private static final UUID JAFFNA_ZONE = UUID.randomUUID();

    private final HubRepository hubs = mock(HubRepository.class);
    private final HubNetwork network = new HubNetwork(hubs);
    private final Hub colombo = new Hub("CMB", "Colombo hub", "Colombo", COLOMBO_ZONE, true, NOW);
    private final Hub kandy = new Hub("KDY", "Kandy branch", "Kandy", KANDY_ZONE, false, NOW);
    private final Hub jaffna = new Hub("JAF", "Jaffna branch", "Jaffna", JAFFNA_ZONE, false, NOW);

    @BeforeEach
    void setUp() {
        when(hubs.findByZoneIdAndActiveTrue(COLOMBO_ZONE)).thenReturn(Optional.of(colombo));
        when(hubs.findByZoneIdAndActiveTrue(KANDY_ZONE)).thenReturn(Optional.of(kandy));
        when(hubs.findByZoneIdAndActiveTrue(JAFFNA_ZONE)).thenReturn(Optional.of(jaffna));
        when(hubs.findFirstByCentralTrueAndActiveTrue()).thenReturn(Optional.of(colombo));
    }

    @Test
    void branchesAreConnectedThroughTheCentralHub() {
        assertThat(network.routeFor(KANDY_ZONE, JAFFNA_ZONE).hubs())
                .containsExactly(kandy.getId(), colombo.getId(), jaffna.getId());
    }

    @Test
    void zoneWithoutAnActiveHubIsNotServed() {
        UUID galle = UUID.randomUUID();
        when(hubs.findByZoneIdAndActiveTrue(galle)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> network.requireServed(galle, KANDY_ZONE))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("The pickup zone is not served by a hub");
        assertThatThrownBy(() -> network.requireServed(KANDY_ZONE, galle))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("The drop-off zone is not served by a hub");
    }

    @Test
    void branchesWithoutACentralHubCannotBeConnected() {
        when(hubs.findFirstByCentralTrueAndActiveTrue()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> network.routeFor(KANDY_ZONE, JAFFNA_ZONE))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("No central hub is configured");
        // Within one branch no central hub is needed
        assertThat(network.routeFor(KANDY_ZONE, KANDY_ZONE).hubs()).containsExactly(kandy.getId());
    }
}
