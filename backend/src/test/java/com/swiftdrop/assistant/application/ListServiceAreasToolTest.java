package com.swiftdrop.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.swiftdrop.pricing.api.ServiceZone;
import com.swiftdrop.pricing.api.ZoneDirectory;

class ListServiceAreasToolTest {

    @Test
    void listsTheNamesOfTheActiveZonesOnly() {
        ZoneDirectory zones = mock(ZoneDirectory.class);
        when(zones.findActiveZones()).thenReturn(List.of(
                new ServiceZone(UUID.randomUUID(), "CMB", "Colombo"),
                new ServiceZone(UUID.randomUUID(), "KDY", "Kandy")));

        Map<String, Object> result = new ListServiceAreasTool(zones).execute(Map.of());

        assertThat(result).containsOnlyKeys("areas").containsEntry("areas", List.of("Colombo", "Kandy"));
    }
}
