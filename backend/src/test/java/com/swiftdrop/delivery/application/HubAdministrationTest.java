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
import com.swiftdrop.delivery.domain.HubLink;
import com.swiftdrop.delivery.domain.HubLinkRepository;
import com.swiftdrop.delivery.domain.HubRepository;

class HubAdministrationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");
    private static final UUID KANDY_ZONE = UUID.randomUUID();

    private final HubRepository hubs = mock(HubRepository.class);
    private final HubLinkRepository links = mock(HubLinkRepository.class);
    private final HubAdministration administration =
            new HubAdministration(hubs, links, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(hubs.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(links.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
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

    @Test
    void deletesAnExistingHub() {
        UUID id = UUID.randomUUID();
        Hub hub = new Hub("KDY", "Kandy branch", "Kandy", KANDY_ZONE, false, NOW);
        when(hubs.findById(id)).thenReturn(Optional.of(hub));

        administration.delete(id);

        verify(hubs).delete(hub);
        verify(hubs).flush();
    }

    @Test
    void conflictWhenHubReferencedByParcels() {
        UUID id = UUID.randomUUID();
        Hub hub = new Hub("KDY", "Kandy branch", "Kandy", KANDY_ZONE, false, NOW);
        when(hubs.findById(id)).thenReturn(Optional.of(hub));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk_parcels_origin_hub")).when(hubs).flush();

        assertThatThrownBy(() -> administration.delete(id))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessageContaining("referenced by existing parcels");
    }

    // ---- Direct links (ADR 0011 §1) ----

    private static final UUID ADMIN = UUID.randomUUID();

    private Hub branch(String code, String city) {
        Hub hub = new Hub(code, city + " branch", city, UUID.randomUUID(), false, NOW);
        when(hubs.findById(hub.getId())).thenReturn(Optional.of(hub));
        return hub;
    }

    @Test
    void linksTwoBranches() {
        Hub badulla = branch("BAD", "Badulla");
        Hub monaragala = branch("MON", "Monaragala");

        HubLink link = administration.link(badulla.getId(), monaragala.getId(), ADMIN);

        assertThat(java.util.List.of(link.getFirstHubId(), link.getSecondHubId()))
                .containsExactlyInAnyOrder(badulla.getId(), monaragala.getId());
        assertThat(link.getCreatedBy()).isEqualTo(ADMIN);
        assertThat(link.getCreatedAt()).isEqualTo(NOW);
        verify(links).saveAndFlush(link);
    }

    @Test
    void aHubIsNotLinkedToItself() {
        Hub badulla = branch("BAD", "Badulla");

        assertThatThrownBy(() -> administration.link(badulla.getId(), badulla.getId(), ADMIN))
                .isInstanceOf(IllegalArgumentException.class);
        verify(links, never()).saveAndFlush(any());
    }

    @Test
    void unknownHubCannotBeLinked() {
        Hub badulla = branch("BAD", "Badulla");
        UUID unknown = UUID.randomUUID();
        when(hubs.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> administration.link(badulla.getId(), unknown, ADMIN))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void theCentralHubIsNotLinked() {
        Hub badulla = branch("BAD", "Badulla");
        Hub colombo = new Hub("CMB", "Colombo hub", "Colombo", UUID.randomUUID(), true, NOW);
        when(hubs.findById(colombo.getId())).thenReturn(Optional.of(colombo));

        assertThatThrownBy(() -> administration.link(badulla.getId(), colombo.getId(), ADMIN))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("The central hub is already connected to every branch");
    }

    @Test
    void anInactiveHubIsNotLinked() {
        Hub badulla = branch("BAD", "Badulla");
        Hub monaragala = branch("MON", "Monaragala");
        monaragala.setActive(false, NOW);

        assertThatThrownBy(() -> administration.link(badulla.getId(), monaragala.getId(), ADMIN))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("Hub MON is not active");
    }

    @Test
    void aPairIsLinkedOnlyOnce() {
        Hub badulla = branch("BAD", "Badulla");
        Hub monaragala = branch("MON", "Monaragala");
        when(links.areLinked(monaragala.getId(), badulla.getId())).thenReturn(true);

        assertThatThrownBy(() -> administration.link(monaragala.getId(), badulla.getId(), ADMIN))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("These hubs are already linked");
        verify(links, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateLinkIsAConflict() {
        Hub badulla = branch("BAD", "Badulla");
        Hub monaragala = branch("MON", "Monaragala");
        when(links.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_hub_links_pair"));

        assertThatThrownBy(() -> administration.link(badulla.getId(), monaragala.getId(), ADMIN))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("These hubs are already linked");
    }

    @Test
    void removesALink() {
        UUID badulla = UUID.randomUUID();
        UUID monaragala = UUID.randomUUID();
        HubLink link = new HubLink(badulla, monaragala, ADMIN, NOW);
        when(links.findLink(monaragala, badulla)).thenReturn(Optional.of(link));

        administration.unlink(monaragala, badulla);

        verify(links).delete(link);
    }

    @Test
    void removingAMissingLinkIsNotFound() {
        UUID badulla = UUID.randomUUID();
        UUID monaragala = UUID.randomUUID();
        when(links.findLink(badulla, monaragala)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> administration.unlink(badulla, monaragala))
                .isInstanceOf(ParcelNotFoundException.class);
    }
}
