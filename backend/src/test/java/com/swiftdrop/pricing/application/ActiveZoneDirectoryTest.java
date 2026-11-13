package com.swiftdrop.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.pricing.api.ServiceZone;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;

class ActiveZoneDirectoryTest {

    @Test
    void listsActiveZonesOrderedByNameWithoutPricingDetails() {
        ZoneRepository zones = mock(ZoneRepository.class);
        Zone kandy = zone("KDY", "Kandy");
        Zone colombo = zone("CMB", "Colombo");
        when(zones.findByActiveTrue()).thenReturn(List.of(kandy, colombo));

        List<ServiceZone> result = new ActiveZoneDirectory(zones).findActiveZones();

        assertThat(result).extracting(ServiceZone::name).containsExactly("Colombo", "Kandy");
        assertThat(result).extracting(ServiceZone::code).containsExactly("CMB", "KDY");
        assertThat(result.get(0).id()).isEqualTo(colombo.getId());
    }

    @Test
    void returnsNothingWhenNoZoneIsActive() {
        ZoneRepository zones = mock(ZoneRepository.class);
        when(zones.findByActiveTrue()).thenReturn(List.of());

        assertThat(new ActiveZoneDirectory(zones).findActiveZones()).isEmpty();
    }

    private static Zone zone(String code, String name) {
        Zone zone = new Zone(code, name);
        ReflectionTestUtils.setField(zone, "id", java.util.UUID.randomUUID());
        return zone;
    }
}
