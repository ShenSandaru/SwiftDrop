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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.application.HubStaffAdministration.StaffMember;
import com.swiftdrop.delivery.domain.Hub;
import com.swiftdrop.delivery.domain.HubRepository;
import com.swiftdrop.delivery.domain.HubStaff;
import com.swiftdrop.delivery.domain.HubStaffRepository;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;

/** Giving dispatchers and couriers their home hubs (ADR 0012 §1, §2). */
class HubStaffAdministrationTest {

    private static final Instant NOW = Instant.parse("2026-11-15T08:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();

    private final HubStaffRepository staff = mock(HubStaffRepository.class);
    private final HubRepository hubs = mock(HubRepository.class);
    private final UserDirectory users = mock(UserDirectory.class);
    private final HubStaffAdministration administration =
            new HubStaffAdministration(staff, hubs, users, Clock.fixed(NOW, ZoneOffset.UTC));

    private Hub hub(String code) {
        Hub hub = new Hub(code, code + " branch", code, UUID.randomUUID(), false, NOW);
        when(hubs.findById(hub.getId())).thenReturn(Optional.of(hub));
        return hub;
    }

    private UserSummary user(String name, String... roles) {
        UserSummary user = new UserSummary(UUID.randomUUID(), name, name.toLowerCase() + "@example.com",
                Set.of(roles), true);
        when(users.findUser(user.id())).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void givesADispatcherOrCourierAHomeHub() {
        Hub badulla = hub("BAD");
        UserSummary courier = user("Ajith", "COURIER");

        administration.addToHub(badulla.getId(), courier.id(), ADMIN);

        ArgumentCaptor<HubStaff> saved = ArgumentCaptor.forClass(HubStaff.class);
        verify(staff).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(courier.id());
        assertThat(saved.getValue().getHubId()).isEqualTo(badulla.getId());
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(ADMIN);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void askingAgainForTheSameHubChangesNothing() {
        Hub badulla = hub("BAD");
        UserSummary courier = user("Ajith", "COURIER");
        when(staff.findByUserIdAndHubId(courier.id(), badulla.getId()))
                .thenReturn(Optional.of(HubStaff.atHub(courier.id(), badulla.getId(), ADMIN, NOW)));

        administration.addToHub(badulla.getId(), courier.id(), ADMIN);

        verify(staff, never()).saveAndFlush(any());
    }

    @Test
    void aHubDispatcherReplacesTheHeadOfficeRow() {
        Hub badulla = hub("BAD");
        UserSummary dispatcher = user("Saman", "DISPATCHER");
        HubStaff headOffice = HubStaff.headOffice(dispatcher.id(), ADMIN, NOW);
        when(staff.findByUserIdAndHubIdIsNull(dispatcher.id())).thenReturn(Optional.of(headOffice));

        administration.addToHub(badulla.getId(), dispatcher.id(), ADMIN);

        verify(staff).delete(headOffice);
        verify(staff).saveAndFlush(any());
    }

    @Test
    void onlyDispatchersAndCouriersWorkForAHub() {
        Hub badulla = hub("BAD");
        UserSummary customer = user("Nimal", "CUSTOMER");

        assertThatThrownBy(() -> administration.addToHub(badulla.getId(), customer.id(), ADMIN))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("Only dispatchers and couriers can work for a hub");
        verify(staff, never()).saveAndFlush(any());
    }

    @Test
    void unknownHubOrUserIsNotFound() {
        Hub badulla = hub("BAD");
        UUID unknown = UUID.randomUUID();
        when(hubs.findById(unknown)).thenReturn(Optional.empty());
        when(users.findUser(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> administration.addToHub(unknown, UUID.randomUUID(), ADMIN))
                .isInstanceOf(ParcelNotFoundException.class);
        assertThatThrownBy(() -> administration.addToHub(badulla.getId(), unknown, ADMIN))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void anInactiveHubTakesNoNewStaff() {
        Hub badulla = hub("BAD");
        badulla.setActive(false, NOW);
        UserSummary courier = user("Ajith", "COURIER");

        assertThatThrownBy(() -> administration.addToHub(badulla.getId(), courier.id(), ADMIN))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("Hub BAD is not active");
    }

    @Test
    void aConcurrentDuplicateIsAConflict() {
        Hub badulla = hub("BAD");
        UserSummary courier = user("Ajith", "COURIER");
        when(staff.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_hub_staff_user_hub"));

        assertThatThrownBy(() -> administration.addToHub(badulla.getId(), courier.id(), ADMIN))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("This person already works there");
    }

    @Test
    void removesAPersonFromAHub() {
        UUID hubId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        HubStaff row = HubStaff.atHub(userId, hubId, ADMIN, NOW);
        when(staff.findByUserIdAndHubId(userId, hubId)).thenReturn(Optional.of(row));

        administration.removeFromHub(hubId, userId);

        verify(staff).delete(row);
        assertThatThrownBy(() -> administration.removeFromHub(hubId, UUID.randomUUID()))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void aDispatcherBecomesHeadOfficeAndLosesTheirHubs() {
        UserSummary dispatcher = user("Saman", "DISPATCHER");
        List<HubStaff> hubRows = List.of(HubStaff.atHub(dispatcher.id(), UUID.randomUUID(), ADMIN, NOW));
        when(staff.findByUserId(dispatcher.id())).thenReturn(hubRows);

        administration.makeHeadOffice(dispatcher.id(), ADMIN);

        verify(staff).deleteAll(hubRows);
        ArgumentCaptor<HubStaff> saved = ArgumentCaptor.forClass(HubStaff.class);
        verify(staff).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isHeadOffice()).isTrue();
    }

    @Test
    void headOfficeIsForDispatchersOnly() {
        UserSummary courier = user("Ajith", "COURIER");

        assertThatThrownBy(() -> administration.makeHeadOffice(courier.id(), ADMIN))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("Only a dispatcher can work for the head office");
    }

    @Test
    void makingSomeoneHeadOfficeTwiceChangesNothing() {
        UserSummary dispatcher = user("Saman", "DISPATCHER");
        when(staff.findByUserIdAndHubIdIsNull(dispatcher.id()))
                .thenReturn(Optional.of(HubStaff.headOffice(dispatcher.id(), ADMIN, NOW)));

        administration.makeHeadOffice(dispatcher.id(), ADMIN);

        verify(staff, never()).saveAndFlush(any());
    }

    @Test
    void removesHeadOffice() {
        UUID userId = UUID.randomUUID();
        HubStaff row = HubStaff.headOffice(userId, ADMIN, NOW);
        when(staff.findByUserIdAndHubIdIsNull(userId)).thenReturn(Optional.of(row));

        administration.removeFromHeadOffice(userId);

        verify(staff).delete(row);
        assertThatThrownBy(() -> administration.removeFromHeadOffice(UUID.randomUUID()))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void listsEveryDispatcherAndCourierWithTheirPlaceByName() {
        UserSummary saman = user("saman", "DISPATCHER");
        UserSummary ajith = user("Ajith", "COURIER");
        UserSummary nobody = user("Kamal", "DISPATCHER");
        UUID badulla = UUID.randomUUID();
        when(users.findEnabledDispatchers()).thenReturn(List.of(saman, nobody));
        when(users.findEnabledCouriers()).thenReturn(List.of(ajith));
        when(staff.findAll()).thenReturn(List.of(
                HubStaff.headOffice(saman.id(), ADMIN, NOW), HubStaff.atHub(ajith.id(), badulla, ADMIN, NOW)));

        List<StaffMember> members = administration.list();

        assertThat(members).extracting(StaffMember::fullName).containsExactly("Ajith", "Kamal", "saman");
        assertThat(members.get(0).hubIds()).containsExactly(badulla);
        assertThat(members.get(0).headOffice()).isFalse();
        assertThat(members.get(1).hubIds()).isEmpty();
        assertThat(members.get(1).headOffice()).isFalse();
        assertThat(members.get(2).headOffice()).isTrue();
    }
}
