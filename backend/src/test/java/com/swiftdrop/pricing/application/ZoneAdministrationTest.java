package com.swiftdrop.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.pricing.api.PricingConflictException;
import com.swiftdrop.pricing.api.PricingNotFoundException;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;

class ZoneAdministrationTest {

    private ZoneRepository repository;
    private ZoneAdministration administration;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        repository = mock(ZoneRepository.class);
        administration = new ZoneAdministration(repository, clock);
    }

    @Test
    void listReturnsAllZonesFromRepository() {
        Zone z1 = createZoneWithId(UUID.randomUUID(), "CMB", "Colombo", true, 0L);
        Zone z2 = createZoneWithId(UUID.randomUUID(), "KDY", "Kandy", false, 1L);
        when(repository.findAllByOrderByNameAsc()).thenReturn(List.of(z1, z2));

        List<Zone> result = administration.list();

        assertThat(result).containsExactly(z1, z2);
    }

    @Test
    void getReturnsZoneWhenFound() {
        UUID id = UUID.randomUUID();
        Zone zone = createZoneWithId(id, "CMB", "Colombo", true, 0L);
        when(repository.findById(id)).thenReturn(Optional.of(zone));

        Zone result = administration.get(id);

        assertThat(result).isSameAs(zone);
    }

    @Test
    void getThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> administration.get(id))
                .isInstanceOf(PricingNotFoundException.class)
                .hasMessageContaining("Zone not found");
    }

    @Test
    void createNormalizesAndSavesZone() {
        when(repository.existsByCodeIgnoreCase("GAL")).thenReturn(false);
        when(repository.existsByNameIgnoreCase("Galle")).thenReturn(false);
        when(repository.saveAndFlush(any(Zone.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Zone created = administration.create("  gal  ", "  Galle  ");

        assertThat(created.getCode()).isEqualTo("GAL");
        assertThat(created.getName()).isEqualTo("Galle");
        assertThat(created.isActive()).isTrue();
        verify(repository).saveAndFlush(any(Zone.class));
    }

    @Test
    void createRejectsDuplicateCodeWith409() {
        when(repository.existsByCodeIgnoreCase("CMB")).thenReturn(true);

        assertThatThrownBy(() -> administration.create("CMB", "New Colombo"))
                .isInstanceOf(PricingConflictException.class)
                .hasMessageContaining("code 'CMB' already exists");
    }

    @Test
    void createRejectsDuplicateNameWith409() {
        when(repository.existsByCodeIgnoreCase("COL")).thenReturn(false);
        when(repository.existsByNameIgnoreCase("Colombo")).thenReturn(true);

        assertThatThrownBy(() -> administration.create("COL", "Colombo"))
                .isInstanceOf(PricingConflictException.class)
                .hasMessageContaining("name 'Colombo' already exists");
    }

    @Test
    void updateModifiesFieldsWhenValid() {
        UUID id = UUID.randomUUID();
        Zone existing = createZoneWithId(id, "CMB", "Colombo", true, 2L);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.existsByNameIgnoreCase("Greater Colombo")).thenReturn(false);
        when(repository.saveAndFlush(any(Zone.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Zone updated = administration.update(id, "CMB", "Greater Colombo", false, 2L);

        assertThat(updated.getName()).isEqualTo("Greater Colombo");
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    void updateRejectsVersionConflictWith409() {
        UUID id = UUID.randomUUID();
        Zone existing = createZoneWithId(id, "CMB", "Colombo", true, 3L);
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> administration.update(id, "CMB", "Greater Colombo", true, 2L))
                .isInstanceOf(PricingConflictException.class)
                .hasMessageContaining("modified by another transaction");
    }

    @Test
    void updateRejectsDuplicateNameWith409() {
        UUID id = UUID.randomUUID();
        Zone existing = createZoneWithId(id, "CMB", "Colombo", true, 0L);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.existsByNameIgnoreCase("Kandy")).thenReturn(true);

        assertThatThrownBy(() -> administration.update(id, "CMB", "Kandy", true, 0L))
                .isInstanceOf(PricingConflictException.class)
                .hasMessageContaining("name 'Kandy' already exists");
    }

    private static Zone createZoneWithId(UUID id, String code, String name, boolean active, long version) {
        Zone zone = new Zone(code, name, active);
        ReflectionTestUtils.setField(zone, "id", id);
        ReflectionTestUtils.setField(zone, "version", version);
        return zone;
    }
}

