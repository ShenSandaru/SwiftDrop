package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.application.HubAdministration.HubDetails;
import com.swiftdrop.delivery.domain.Hub;
import com.swiftdrop.delivery.domain.HubRepository;

class HubAdministrationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");
    private static final UUID KANDY_ZONE = UUID.randomUUID();

    private final HubRepository hubs = mock(HubRepository.class);
    private final HubAdministration administration = new HubAdministration(hubs, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(hubs.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createsAnActiveHub() {
        Hub hub = administration.create(new HubDetails("Kandy branch", "Kandy", KANDY_ZONE, false), "kdy");

        assertThat(hub.getCode()).isEqualTo("KDY");
        assertThat(hub.isActive()).isTrue();
        verify(hubs).saveAndFlush(hub);
    }

    @Test
    void takenCodeNameOrZoneIsAConflict() {
        when(hubs.existsByCode("KDY")).thenReturn(true);
        assertThatThrownBy(() -> administration.create(new HubDetails("Kandy", "Kandy", KANDY_ZONE, false), "KDY"))
                .isInstanceOf(ParcelConflictException.class).hasMessageContaining("code");

        when(hubs.existsByCode("KDY")).thenReturn(false);
        when(hubs.existsByNameIgnoreCase("Kandy")).thenReturn(true);
        assertThatThrownBy(() -> administration.create(new HubDetails("Kandy", "Kandy", KANDY_ZONE, false), "KDY"))
                .isInstanceOf(ParcelConflictException.class).hasMessageContaining("name");

        when(hubs.existsByNameIgnoreCase("Kandy")).thenReturn(false);
        Hub existing = new Hub("KAN", "Old Kandy branch", "Kandy", KANDY_ZONE, false, NOW);
        when(hubs.findByZoneIdAndActiveTrue(KANDY_ZONE)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> administration.create(new HubDetails("Kandy", "Kandy", KANDY_ZONE, false), "KDY"))
                .isInstanceOf(ParcelConflictException.class).hasMessageContaining("KAN");
        verify(hubs, never()).saveAndFlush(any());
    }

    @Test
    void thereIsOnlyOneCentralHub() {
        Hub colombo = new Hub("CMB", "Colombo hub", "Colombo", UUID.randomUUID(), true, NOW);
        when(hubs.findFirstByCentralTrueAndActiveTrue()).thenReturn(Optional.of(colombo));

        assertThatThrownBy(() -> administration.create(new HubDetails("Kandy", "Kandy", KANDY_ZONE, true), "KDY"))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("Hub CMB is already the central hub");
    }

    @Test
    void updateKeepsTheCodeAndCanDeactivate() {
        Hub hub = new Hub("KDY", "Kandy branch", "Kandy", KANDY_ZONE, false, NOW);
        when(hubs.findById(hub.getId())).thenReturn(Optional.of(hub));
        when(hubs.findByZoneIdAndActiveTrue(KANDY_ZONE)).thenReturn(Optional.of(hub));

        Hub updated = administration.update(hub.getId(), new HubDetails("Kandy city", "Kandy", KANDY_ZONE, false),
                false);

        assertThat(updated.getCode()).isEqualTo("KDY");
        assertThat(updated.getName()).isEqualTo("Kandy city");
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    void unknownHubIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(hubs.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> administration.update(unknown, new HubDetails("X", "X", KANDY_ZONE, false), true))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void concurrentDuplicateIsAConflict() {
        when(hubs.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_hubs_code"));

        assertThatThrownBy(() -> administration.create(new HubDetails("Kandy", "Kandy", KANDY_ZONE, false), "KDY"))
                .isInstanceOf(ParcelConflictException.class);
    }
}
