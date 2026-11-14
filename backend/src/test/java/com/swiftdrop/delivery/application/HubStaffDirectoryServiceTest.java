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
    private final HubStaffDirectoryService directory = new HubStaffDirectoryService(scopes, staff);

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
}
