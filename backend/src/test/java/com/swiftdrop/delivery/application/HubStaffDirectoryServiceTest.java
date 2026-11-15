package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.swiftdrop.delivery.domain.HubScope;
import com.swiftdrop.delivery.domain.HubStaff;
import com.swiftdrop.delivery.domain.HubStaffRepository;

/** Who shares a hub with whom (ADR 0012 §4). */
class HubStaffDirectoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-14T08:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID DISPATCHER = UUID.randomUUID();
    private static final UUID COURIER = UUID.randomUUID();
    private static final UUID BADULLA = UUID.randomUUID();
    private static final UUID KANDY = UUID.randomUUID();

    private final StaffScopes scopes = mock(StaffScopes.class);
    private final HubStaffRepository staff = mock(HubStaffRepository.class);
    private final com.swiftdrop.delivery.domain.HubRepository hubs = mock(com.swiftdrop.delivery.domain.HubRepository.class);
    private final HubStaffDirectoryService directory = new HubStaffDirectoryService(scopes, staff, hubs);

    @Test
    void colleaguesAreEveryoneWhoWorksForOneOfTheUsersHubs() {
        when(scopes.scopeOf(DISPATCHER)).thenReturn(HubScope.of(Set.of(BADULLA, KANDY)));
        when(staff.findByHubIdIn(Set.of(BADULLA, KANDY))).thenReturn(List.of(
                HubStaff.atHub(DISPATCHER, BADULLA, ADMIN, NOW), HubStaff.atHub(COURIER, KANDY, ADMIN, NOW),
                HubStaff.atHub(COURIER, BADULLA, ADMIN, NOW)));

        assertThat(directory.colleaguesOf(DISPATCHER)).containsExactlyInAnyOrder(DISPATCHER, COURIER);
    }

    @Test
    void someoneWithNoHubHasNoColleagues() {
        when(scopes.scopeOf(DISPATCHER)).thenReturn(HubScope.NONE);

        assertThat(directory.colleaguesOf(DISPATCHER)).isEmpty();
    }

    @Test
    void staffWhoWorkEverywhereAreReportedAsSuchNotByColleagues() {
        when(scopes.scopeOf(DISPATCHER)).thenReturn(HubScope.EVERYWHERE);

        assertThat(directory.worksEverywhere(DISPATCHER)).isTrue();
        assertThat(directory.colleaguesOf(DISPATCHER)).isEmpty();
        when(scopes.scopeOf(COURIER)).thenReturn(HubScope.of(Set.of(BADULLA)));
        assertThat(directory.worksEverywhere(COURIER)).isFalse();
    }

    @Test
    void theScopeNamesTheHubsAlphabetically() {
        com.swiftdrop.delivery.domain.Hub kandy = new com.swiftdrop.delivery.domain.Hub("KDY", "Kandy branch", "Kandy",
                UUID.randomUUID(), false, NOW);
        com.swiftdrop.delivery.domain.Hub badulla = new com.swiftdrop.delivery.domain.Hub("BAD", "Badulla branch",
                "Badulla", UUID.randomUUID(), false, NOW);
        when(scopes.scopeOf(DISPATCHER)).thenReturn(HubScope.of(Set.of(kandy.getId(), badulla.getId())));
        when(hubs.findAllById(Set.of(kandy.getId(), badulla.getId()))).thenReturn(List.of(kandy, badulla));

        com.swiftdrop.delivery.api.StaffScopeView view = directory.scopeOf(DISPATCHER);

        assertThat(view.everywhere()).isFalse();
        assertThat(view.hubs()).extracting(com.swiftdrop.delivery.api.StaffScopeView.HubRef::code)
                .containsExactly("BAD", "KDY");
    }

    @Test
    void anEverywhereScopeListsNoHubs() {
        when(scopes.scopeOf(DISPATCHER)).thenReturn(HubScope.EVERYWHERE);

        assertThat(directory.scopeOf(DISPATCHER)).isEqualTo(new com.swiftdrop.delivery.api.StaffScopeView(true, List.of()));
    }

    @Test
    void someoneWithNoHubHasAnEmptyScope() {
        when(scopes.scopeOf(DISPATCHER)).thenReturn(HubScope.NONE);

        assertThat(directory.scopeOf(DISPATCHER)).isEqualTo(new com.swiftdrop.delivery.api.StaffScopeView(false, List.of()));
    }
}
