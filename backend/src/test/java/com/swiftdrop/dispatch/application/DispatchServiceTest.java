package com.swiftdrop.dispatch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private DispatchService service;

    @BeforeEach
    void setUp() {
        parcels = mock(ParcelQueries.class);
        assignments = mock(ParcelAssignment.class);
        hubOperations = mock(ParcelHubOperations.class);
        users = mock(UserDirectory.class);
        service = new DispatchService(parcels, assignments, hubOperations, users);
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

        List<UserSummary> active = service.activeCouriers();

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
    void hubAssignedDispatcherOnlySeesCouriersAndWorkloadsInTheirHub() {
        UUID dispatcherHub = UUID.randomUUID();
        UUID otherHub = UUID.randomUUID();
        UUID otherCourierId = UUID.randomUUID();
        UserSummary dispatcher = new UserSummary(DISPATCHER_ID, "Colombo Disp", "disp@example.com",
                null, Set.of("DISPATCHER"), true, true, dispatcherHub);
        UserSummary hubCourier = new UserSummary(COURIER_ID, "Colombo Courier", "c1@example.com",
                null, Set.of("COURIER"), true, true, dispatcherHub);
        when(users.findUser(DISPATCHER_ID)).thenReturn(Optional.of(dispatcher));
        when(users.findEnabledCouriersByHub(dispatcherHub)).thenReturn(List.of(hubCourier));
        when(parcels.countActiveParcelsByCourier()).thenReturn(java.util.Map.of(COURIER_ID, 3L, otherCourierId, 5L));

        Staff caller = Staff.dispatcher(DISPATCHER_ID);
        assertThat(service.activeCouriers(otherHub, caller)).containsExactly(hubCourier);
        assertThat(service.courierWorkloads(otherHub, caller)).containsExactlyEntriesOf(java.util.Map.of(COURIER_ID, 3L));
        verify(users, never()).findEnabledCouriersByHub(otherHub);
    }

    @Test
    void hubAssignedDispatcherCannotAssignCourierOrParcelFromAnotherHub() {
        UUID dispatcherHub = UUID.randomUUID();
        UUID otherHub = UUID.randomUUID();
        UserSummary dispatcher = new UserSummary(DISPATCHER_ID, "Colombo Disp", "disp@example.com",
                null, Set.of("DISPATCHER"), true, true, dispatcherHub);
        UserSummary otherCourier = new UserSummary(COURIER_ID, "Kandy Courier", "kandy@example.com",
                null, Set.of("COURIER"), true, true, otherHub);
        when(users.findUser(DISPATCHER_ID)).thenReturn(Optional.of(dispatcher));
        when(users.findUser(COURIER_ID)).thenReturn(Optional.of(otherCourier));

        Staff caller = Staff.dispatcher(DISPATCHER_ID);
        assertThatThrownBy(() -> service.assign(PARCEL_ID, COURIER_ID, caller, ParcelAssignmentType.PICKUP))
                .isInstanceOf(DispatchCourierUnavailableException.class)
                .hasMessageContaining("not assigned to your hub");

        UserSummary sameHubCourier = new UserSummary(COURIER_ID, "Colombo Courier", "cmb@example.com",
                null, Set.of("COURIER"), true, true, dispatcherHub);
        when(users.findUser(COURIER_ID)).thenReturn(Optional.of(sameHubCourier));
        when(parcels.isParcelInHub(PARCEL_ID, dispatcherHub)).thenReturn(false);

        assertThatThrownBy(() -> service.assign(PARCEL_ID, COURIER_ID, caller, ParcelAssignmentType.PICKUP))
                .isInstanceOf(com.swiftdrop.delivery.api.ParcelConflictException.class)
                .hasMessageContaining("does not belong to your hub");

        verify(assignments, never()).assignCourier(PARCEL_ID, COURIER_ID, DISPATCHER_ID, ParcelAssignmentType.PICKUP);
    }

    @Test
    void hubAssignedDispatcherCannotScanAtAnotherHub() {
        UUID dispatcherHub = UUID.randomUUID();
        UUID otherHub = UUID.randomUUID();
        UserSummary dispatcher = new UserSummary(DISPATCHER_ID, "Colombo Disp", "disp@example.com",
                null, Set.of("DISPATCHER"), true, true, dispatcherHub);
        when(users.findUser(DISPATCHER_ID)).thenReturn(Optional.of(dispatcher));

        Staff caller = Staff.dispatcher(DISPATCHER_ID);
        assertThatThrownBy(() -> service.scanAtHub(PARCEL_ID, otherHub, caller))
                .isInstanceOf(com.swiftdrop.delivery.api.ParcelConflictException.class)
                .hasMessageContaining("assigned hub");
        verify(hubOperations, never()).scanAtHub(PARCEL_ID, otherHub, caller);
    }

    @Test
    void adminCanQueryAcrossAllHubsOrFilterByRequestedHub() {
        UUID adminId = UUID.randomUUID();
        UUID requestedHub = UUID.randomUUID();
        Staff admin = Staff.admin(adminId);

        service.activeCouriers(null, admin);
        verify(users).findEnabledCouriers();

        service.activeCouriers(requestedHub, admin);
        verify(users).findEnabledCouriersByHub(requestedHub);
    }
}
