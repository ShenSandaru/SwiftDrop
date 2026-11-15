package com.swiftdrop.dispatch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.HubStaffDirectory;
import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelAssignmentStatus;
import com.swiftdrop.delivery.api.ParcelAssignmentSummary;
import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelHubOperations;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.Staff;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;

class DispatchServiceTest {

    private static final UUID PARCEL_ID = UUID.randomUUID();
    private static final UUID COURIER_ID = UUID.randomUUID();
    private static final UUID DISPATCHER_ID = UUID.randomUUID();

    private ParcelQueries parcels;
    private ParcelAssignment assignments;
    private ParcelHubOperations hubOperations;
    private UserDirectory users;
    private final HubStaffDirectory hubStaff = mock(HubStaffDirectory.class);
    private DispatchService service;

    @BeforeEach
    void setUp() {
        parcels = mock(ParcelQueries.class);
        assignments = mock(ParcelAssignment.class);
        hubOperations = mock(ParcelHubOperations.class);
        users = mock(UserDirectory.class);
        service = new DispatchService(parcels, assignments, hubOperations, users, hubStaff,
                java.time.Duration.ofHours(24),
                java.time.Clock.fixed(Instant.parse("2026-11-14T08:00:00Z"), java.time.ZoneOffset.UTC));
    }

    @Test
    void validatesAnEnabledCourierAndDelegatesTypedAssignment() {
        UserSummary courier = new UserSummary(COURIER_ID, "Courier", "courier@example.com",
                Set.of("COURIER"), true);
        ParcelAssignmentSummary expected = new ParcelAssignmentSummary(UUID.randomUUID(), PARCEL_ID, COURIER_ID,
                ParcelAssignmentType.DELIVERY, ParcelAssignmentStatus.ACTIVE, Instant.now(), null, DISPATCHER_ID);
        when(users.findUser(COURIER_ID)).thenReturn(Optional.of(courier));
        when(assignments.assignCourier(PARCEL_ID, COURIER_ID, DISPATCHER_ID, ParcelAssignmentType.DELIVERY))
                .thenReturn(expected);

        assertThat(service.assign(PARCEL_ID, COURIER_ID, DISPATCHER_ID, ParcelAssignmentType.DELIVERY))
                .isEqualTo(expected);
    }

    @Test
    void missingOrNonCourierAccountCannotBeAssigned() {
        when(users.findUser(COURIER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(PARCEL_ID, COURIER_ID, DISPATCHER_ID, ParcelAssignmentType.PICKUP))
                .isInstanceOf(DispatchCourierNotFoundException.class);

        when(users.findUser(COURIER_ID)).thenReturn(Optional.of(new UserSummary(
                COURIER_ID, "Customer", "customer@example.com", Set.of("CUSTOMER"), true)));
        assertThatThrownBy(() -> service.assign(PARCEL_ID, COURIER_ID, DISPATCHER_ID, ParcelAssignmentType.PICKUP))
                .isInstanceOf(DispatchCourierNotFoundException.class);

        verify(assignments, never()).assignCourier(PARCEL_ID, COURIER_ID, DISPATCHER_ID, ParcelAssignmentType.PICKUP);
    }

    @Test
    void disabledCourierCannotBeAssigned() {
        when(users.findUser(COURIER_ID)).thenReturn(Optional.of(new UserSummary(
                COURIER_ID, "Courier", "courier@example.com", Set.of("COURIER"), false)));

        assertThatThrownBy(() -> service.assign(PARCEL_ID, COURIER_ID, DISPATCHER_ID, ParcelAssignmentType.PICKUP))
                .isInstanceOf(DispatchCourierUnavailableException.class);

        verify(assignments, never()).assignCourier(PARCEL_ID, COURIER_ID, DISPATCHER_ID, ParcelAssignmentType.PICKUP);
    }

    @Test
    void delegatesReassignmentToDifferentCourier() {
        UUID replacementCourierId = UUID.randomUUID();
        UserSummary courier = new UserSummary(replacementCourierId, "Replacement Courier", "courier2@example.com",
                Set.of("COURIER"), true);
        ParcelAssignmentSummary expected = new ParcelAssignmentSummary(UUID.randomUUID(), PARCEL_ID, replacementCourierId,
                ParcelAssignmentType.PICKUP, ParcelAssignmentStatus.ACTIVE, Instant.now(), null, DISPATCHER_ID);
        when(users.findUser(replacementCourierId)).thenReturn(Optional.of(courier));
        when(assignments.assignCourier(PARCEL_ID, replacementCourierId, DISPATCHER_ID, ParcelAssignmentType.PICKUP))
                .thenReturn(expected);

        assertThat(service.assign(PARCEL_ID, replacementCourierId, DISPATCHER_ID, ParcelAssignmentType.PICKUP))
                .isEqualTo(expected);
        verify(assignments).assignCourier(PARCEL_ID, replacementCourierId, DISPATCHER_ID, ParcelAssignmentType.PICKUP);
    }

    @Test
    void retrievesActiveCouriersFromUserDirectory() {
        UserSummary courier = new UserSummary(COURIER_ID, "Courier", "courier@example.com", Set.of("COURIER"), true);
        when(users.findEnabledCouriers()).thenReturn(List.of(courier));

        List<UserSummary> active = service.activeCouriers(Staff.admin(DISPATCHER_ID));

        assertThat(active).containsExactly(courier);
        verify(users).findEnabledCouriers();
    }

    @Test
    void hubStepsAreDelegatedWithTheCaller() {
        UUID parcel = UUID.randomUUID();
        UUID hub = UUID.randomUUID();
        Staff staff = Staff.dispatcher(UUID.randomUUID());

        service.scanAtHub(parcel, hub, staff);
        service.sendToNextHub(parcel, staff);
        service.startReturn(parcel, staff);
        service.markFailed(parcel, FailureReason.DAMAGED, "Crushed", staff);
        service.cancel(parcel, staff);

        verify(hubOperations).scanAtHub(parcel, hub, staff);
        verify(hubOperations).sendToNextHub(parcel, staff);
        verify(hubOperations).startReturn(parcel, staff);
        verify(hubOperations).markFailed(parcel, FailureReason.DAMAGED, "Crushed", staff);
        verify(hubOperations).cancel(parcel, staff);
    }

    @Test
    void staleParcelsAreThoseUntouchedForTheConfiguredTime() {
        com.swiftdrop.delivery.api.PageRequest page = new com.swiftdrop.delivery.api.PageRequest(0, 20);
        com.swiftdrop.delivery.api.PageResult<com.swiftdrop.delivery.api.DispatchParcelSummary> expected =
                com.swiftdrop.delivery.api.PageResult.of(List.of(), page, 0);
        // 24 hours before the fixed clock
        when(parcels.findStaleParcels(DISPATCHER_ID, Instant.parse("2026-11-13T08:00:00Z"), page)).thenReturn(expected);

        assertThat(service.staleParcels(Staff.dispatcher(DISPATCHER_ID), page)).isSameAs(expected);
    }

    @Test
    void theStaleTimeMustBePositive() {
        assertThatThrownBy(() -> new DispatchService(parcels, assignments, hubOperations, users, hubStaff, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- Hub scope of the courier lists (ADR 0012 §4) ----

    private static UserSummary courier(UUID id) {
        return new UserSummary(id, "Courier", "courier-" + id + "@example.com", Set.of("COURIER"), true);
    }

    @Test
    void aHubDispatcherSeesOnlyTheCouriersOfTheirHubs() {
        UUID mine = UUID.randomUUID();
        UUID someoneElses = UUID.randomUUID();
        when(users.findEnabledCouriers()).thenReturn(List.of(courier(mine), courier(someoneElses)));
        when(hubStaff.worksEverywhere(DISPATCHER_ID)).thenReturn(false);
        when(hubStaff.colleaguesOf(DISPATCHER_ID)).thenReturn(Set.of(DISPATCHER_ID, mine));

        assertThat(service.activeCouriers(Staff.dispatcher(DISPATCHER_ID))).extracting(UserSummary::id)
                .containsExactly(mine);
    }

    @Test
    void aDispatcherWithNoHubSeesNoCouriers() {
        when(users.findEnabledCouriers()).thenReturn(List.of(courier(UUID.randomUUID())));
        when(hubStaff.colleaguesOf(DISPATCHER_ID)).thenReturn(Set.of());

        assertThat(service.activeCouriers(Staff.dispatcher(DISPATCHER_ID))).isEmpty();
    }

    @Test
    void adminsSeeEveryCourier() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(users.findEnabledCouriers()).thenReturn(List.of(courier(first), courier(second)));

        assertThat(service.activeCouriers(Staff.admin(UUID.randomUUID()))).hasSize(2);
        verify(hubStaff, never()).colleaguesOf(any());
    }

    @Test
    void workloadsAreShownOnlyForTheCouriersTheCallerMayAssign() {
        UUID mine = UUID.randomUUID();
        UUID someoneElses = UUID.randomUUID();
        when(parcels.countActiveParcelsByCourier()).thenReturn(java.util.Map.of(mine, 3L, someoneElses, 5L));
        when(hubStaff.colleaguesOf(DISPATCHER_ID)).thenReturn(Set.of(mine));

        assertThat(service.courierWorkloads(Staff.dispatcher(DISPATCHER_ID))).containsOnlyKeys(mine);

        assertThat(service.courierWorkloads(Staff.admin(DISPATCHER_ID))).containsOnlyKeys(mine, someoneElses);
    }

    @Test
    void anAdminWorksEverywhereWithoutALookup() {
        assertThat(service.scope(Staff.admin(DISPATCHER_ID)).everywhere()).isTrue();
        verify(hubStaff, never()).scopeOf(DISPATCHER_ID);
    }

    @Test
    void aDispatchersScopeComesFromTheHubDirectory() {
        com.swiftdrop.delivery.api.StaffScopeView scope = new com.swiftdrop.delivery.api.StaffScopeView(false,
                List.of(new com.swiftdrop.delivery.api.StaffScopeView.HubRef(UUID.randomUUID(), "BAD", "Badulla branch")));
        when(hubStaff.scopeOf(DISPATCHER_ID)).thenReturn(scope);

        assertThat(service.scope(Staff.dispatcher(DISPATCHER_ID))).isSameAs(scope);
    }
}
