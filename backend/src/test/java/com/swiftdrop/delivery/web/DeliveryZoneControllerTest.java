package com.swiftdrop.delivery.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.swiftdrop.delivery.application.HubNetwork;
import com.swiftdrop.pricing.api.ServiceZone;
import com.swiftdrop.pricing.api.ZoneDirectory;

class DeliveryZoneControllerTest {

    private final ZoneDirectory zones = mock(ZoneDirectory.class);
    private final HubNetwork hubs = mock(HubNetwork.class);
    private final DeliveryZoneController controller = new DeliveryZoneController(zones, hubs);

    @Test
    void returnsOnlyActiveZonesServedByActiveHubs() {
        UUID colomboId = UUID.randomUUID();
        UUID kandyId = UUID.randomUUID();
        UUID unservedId = UUID.randomUUID();

        ServiceZone colombo = new ServiceZone(colomboId, "CMB", "Colombo");
        ServiceZone kandy = new ServiceZone(kandyId, "KDY", "Kandy");
        ServiceZone unserved = new ServiceZone(unservedId, "GAL", "Galle");

        when(hubs.servedZoneIds()).thenReturn(Set.of(colomboId, kandyId));
        when(zones.findActiveZones()).thenReturn(List.of(colombo, kandy, unserved));

        List<ServiceZone> result = controller.availableZones();

        assertThat(result).containsExactly(colombo, kandy);
        assertThat(result).allSatisfy(zone -> {
            assertThat(zone.id()).isNotNull();
            assertThat(zone.code()).isNotBlank();
            assertThat(zone.name()).isNotBlank();
        });
    }

    @Test
    void returnsEmptyListWhenNoActiveZonesAreServed() {
        when(hubs.servedZoneIds()).thenReturn(Set.of());
        when(zones.findActiveZones()).thenReturn(List.of(
                new ServiceZone(UUID.randomUUID(), "GAL", "Galle")));

        assertThat(controller.availableZones()).isEmpty();
    }
}
