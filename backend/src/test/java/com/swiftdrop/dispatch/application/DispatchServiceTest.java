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
import com.swiftdrop.delivery.api.DispatchActivity;
import com.swiftdrop.delivery.api.DispatchActivityAction;
import com.swiftdrop.delivery.api.DispatchActivityDetails;
import com.swiftdrop.delivery.api.DispatchActivityFilter;
import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelAssignmentStatus;
import com.swiftdrop.delivery.api.ParcelAssignmentSummary;
import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelHubOperations;
import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.Staff;
import com.swiftdrop.dispatch.api.DispatchActivityItem;
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
    void enrichesHistoryActorNamesAndHandlesLegacyMissingDispatcherIds() {
        UUID legacyActivityId = UUID.randomUUID();
        DispatchActivity activity = new DispatchActivity(
                "ASSIGNMENT:" + UUID.randomUUID(),
                "SD7K3M9Q2XWB",
                Instant.now(),
                DispatchActivityAction.PICKUP_ASSIGNED,
                DISPATCHER_ID,
                new DispatchActivityDetails(ParcelAssignmentType.PICKUP, COURIER_ID, null, null, null, null, null));
        DispatchActivity legacyActivity = new DispatchActivity(
                "ASSIGNMENT:" + legacyActivityId,
                "SD9K3M9Q2XWB",
                Instant.now().minusSeconds(1),
                DispatchActivityAction.RETURN_ASSIGNED,
                null,
                new DispatchActivityDetails(ParcelAssignmentType.RETURN, COURIER_ID, null, null, null, null, null));
        DispatchActivityFilter filter = new DispatchActivityFilter(null, null, null, null, null);
        PageRequest page = new PageRequest(0, 20);
        when(parcels.findDispatchActivityHistory(filter, page))
                .thenReturn(PageResult.of(List.of(activity, legacyActivity), page, 2));
        when(users.findUsers(Set.of(DISPATCHER_ID))).thenReturn(List.of(new UserSummary(
                DISPATCHER_ID, "Dispatcher One", "dispatcher@example.com", Set.of("DISPATCHER"), true)));

        PageResult<com.swiftdrop.dispatch.api.DispatchActivityItem> result = service.activityHistory(filter, page);

        assertThat(result.items()).extracting(DispatchActivityItem::actorName)
                .containsExactly("Dispatcher One", "Unknown dispatcher");
        assertThat(result.items().get(1).actorId()).isNull();
        verify(users).findUsers(Set.of(DISPATCHER_ID));
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
}
