package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.swiftdrop.delivery.domain.HubScope;
import com.swiftdrop.delivery.domain.HubStaff;
import com.swiftdrop.delivery.domain.HubStaffRepository;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;

/** Who works on which hubs (ADR 0012 §2). */
class StaffScopesTest {

    private static final Instant NOW = Instant.parse("2026-11-14T08:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ADMIN_USER = UUID.randomUUID();
    private static final UUID BADULLA = UUID.randomUUID();
    private static final UUID KANDY = UUID.randomUUID();

    private final HubStaffRepository staff = mock(HubStaffRepository.class);
    private final UserDirectory users = mock(UserDirectory.class);
    private final StaffScopes scopes = new StaffScopes(staff, users);

    private void givenRole(UUID id, String role) {
        when(users.findUser(id)).thenReturn(Optional.of(new UserSummary(id, "Staff", "staff@example.com",
                Set.of(role), true)));
    }

    @Test
    void anAdminWorksEverywhereWithoutAnyRow() {
        givenRole(ADMIN_USER, "ADMIN");

        assertThat(scopes.scopeOf(ADMIN_USER)).isEqualTo(HubScope.EVERYWHERE);
    }

    @Test
    void aHeadOfficeRowMeansEverywhere() {
        givenRole(USER, "DISPATCHER");
        when(staff.findByUserId(USER)).thenReturn(List.of(HubStaff.headOffice(USER, ADMIN_USER, NOW)));

        assertThat(scopes.scopeOf(USER)).isEqualTo(HubScope.EVERYWHERE);
    }

    @Test
    void theHubsOfAPersonAddUp() {
        givenRole(USER, "COURIER");
        when(staff.findByUserId(USER)).thenReturn(List.of(
                HubStaff.atHub(USER, BADULLA, ADMIN_USER, NOW), HubStaff.atHub(USER, KANDY, ADMIN_USER, NOW)));

        HubScope scope = scopes.scopeOf(USER);

        assertThat(scope.everywhere()).isFalse();
        assertThat(scope.hubIds()).containsExactlyInAnyOrder(BADULLA, KANDY);
    }

    @Test
    void aPersonWithNoRowSeesNothing() {
        givenRole(USER, "DISPATCHER");
        when(staff.findByUserId(USER)).thenReturn(List.of());

        assertThat(scopes.scopeOf(USER)).isEqualTo(HubScope.NONE);
    }

    @Test
    void anUnknownUserSeesNothing() {
        when(users.findUser(USER)).thenReturn(Optional.empty());
        when(staff.findByUserId(USER)).thenReturn(List.of());

        assertThat(scopes.scopeOf(USER)).isEqualTo(HubScope.NONE);
    }
}
