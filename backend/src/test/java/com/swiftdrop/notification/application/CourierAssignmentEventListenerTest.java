package com.swiftdrop.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelCourierAssigned;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;
import com.swiftdrop.notification.domain.NotificationChannel;
import com.swiftdrop.notification.domain.NotificationDeliveryStatus;
import com.swiftdrop.notification.domain.NotificationRecordRepository;

@DisplayName("CourierAssignmentEventListener unit tests")
class CourierAssignmentEventListenerTest {

    private static final UUID COURIER_ID = UUID.randomUUID();
    private static final UUID DISPATCHER_ID = UUID.randomUUID();
    private static final UUID PARCEL_ID = UUID.randomUUID();
    private static final String TRACKING_NUMBER = "SD7K3M9Q2XWB";
    private static final Instant OCCURRED_AT = Instant.parse("2026-11-14T06:00:00Z");

    private NotificationRecordRepository recordRepository;
    private UserDirectory userDirectory;
    private CourierAssignmentEventListener listener;

    @BeforeEach
    void setUp() {
        recordRepository = mock(NotificationRecordRepository.class);
        userDirectory = mock(UserDirectory.class);
        listener = new CourierAssignmentEventListener(recordRepository, userDirectory);
    }

    @Test
    @DisplayName("Creates notification for pickup assignment")
    void createsNotificationForPickup() {
        UUID eventId = UUID.randomUUID();
        ParcelCourierAssigned event = new ParcelCourierAssigned(
                eventId, PARCEL_ID, TRACKING_NUMBER, COURIER_ID, DISPATCHER_ID, ParcelAssignmentType.PICKUP, OCCURRED_AT);

        when(userDirectory.findUser(COURIER_ID)).thenReturn(Optional.of(
                new UserSummary(COURIER_ID, "courier@swiftdrop.com", "Test Courier", Set.of("COURIER"), true)));
        when(recordRepository.existsByEventIdAndChannelAndRecipientUserId(eventId, NotificationChannel.IN_APP, COURIER_ID))
                .thenReturn(false);
        when(recordRepository.insertIfAbsent(any(), eq(eventId), eq(COURIER_ID), eq(PARCEL_ID), eq(TRACKING_NUMBER),
                any(), any(), eq(NotificationChannel.IN_APP.name()), eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()), eq(OCCURRED_AT)))
                .thenReturn(1);

        listener.onParcelCourierAssigned(event);

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        verify(recordRepository).insertIfAbsent(
                any(), eq(eventId), eq(COURIER_ID), eq(PARCEL_ID), eq(TRACKING_NUMBER),
                titleCaptor.capture(), messageCaptor.capture(),
                eq(NotificationChannel.IN_APP.name()), eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()), eq(OCCURRED_AT));

        assertThat(titleCaptor.getValue()).isEqualTo("New Pickup Task");
        assertThat(messageCaptor.getValue()).isEqualTo("You have been assigned to pick up parcel " + TRACKING_NUMBER + ".");
    }

    @Test
    @DisplayName("Creates notification for delivery assignment")
    void createsNotificationForDelivery() {
        UUID eventId = UUID.randomUUID();
        ParcelCourierAssigned event = new ParcelCourierAssigned(
                eventId, PARCEL_ID, TRACKING_NUMBER, COURIER_ID, DISPATCHER_ID, ParcelAssignmentType.DELIVERY, OCCURRED_AT);

        when(userDirectory.findUser(COURIER_ID)).thenReturn(Optional.of(
                new UserSummary(COURIER_ID, "courier@swiftdrop.com", "Test Courier", Set.of("COURIER"), true)));
        when(recordRepository.existsByEventIdAndChannelAndRecipientUserId(eventId, NotificationChannel.IN_APP, COURIER_ID))
                .thenReturn(false);
        when(recordRepository.insertIfAbsent(any(), eq(eventId), eq(COURIER_ID), eq(PARCEL_ID), eq(TRACKING_NUMBER),
                any(), any(), eq(NotificationChannel.IN_APP.name()), eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()), eq(OCCURRED_AT)))
                .thenReturn(1);

        listener.onParcelCourierAssigned(event);

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        verify(recordRepository).insertIfAbsent(
                any(), eq(eventId), eq(COURIER_ID), eq(PARCEL_ID), eq(TRACKING_NUMBER),
                titleCaptor.capture(), messageCaptor.capture(),
                eq(NotificationChannel.IN_APP.name()), eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()), eq(OCCURRED_AT));

        assertThat(titleCaptor.getValue()).isEqualTo("New Delivery Task");
        assertThat(messageCaptor.getValue()).isEqualTo("You have been assigned to deliver parcel " + TRACKING_NUMBER + ".");
    }

    @Test
    @DisplayName("Creates notification for return assignment")
    void createsNotificationForReturn() {
        UUID eventId = UUID.randomUUID();
        ParcelCourierAssigned event = new ParcelCourierAssigned(
                eventId, PARCEL_ID, TRACKING_NUMBER, COURIER_ID, DISPATCHER_ID, ParcelAssignmentType.RETURN, OCCURRED_AT);

        when(userDirectory.findUser(COURIER_ID)).thenReturn(Optional.of(
                new UserSummary(COURIER_ID, "courier@swiftdrop.com", "Test Courier", Set.of("COURIER"), true)));
        when(recordRepository.existsByEventIdAndChannelAndRecipientUserId(eventId, NotificationChannel.IN_APP, COURIER_ID))
                .thenReturn(false);
        when(recordRepository.insertIfAbsent(any(), eq(eventId), eq(COURIER_ID), eq(PARCEL_ID), eq(TRACKING_NUMBER),
                any(), any(), eq(NotificationChannel.IN_APP.name()), eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()), eq(OCCURRED_AT)))
                .thenReturn(1);

        listener.onParcelCourierAssigned(event);

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        verify(recordRepository).insertIfAbsent(
                any(), eq(eventId), eq(COURIER_ID), eq(PARCEL_ID), eq(TRACKING_NUMBER),
                titleCaptor.capture(), messageCaptor.capture(),
                eq(NotificationChannel.IN_APP.name()), eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()), eq(OCCURRED_AT));

        assertThat(titleCaptor.getValue()).isEqualTo("New Return Task");
        assertThat(messageCaptor.getValue()).isEqualTo("You have been assigned to return parcel " + TRACKING_NUMBER + " to sender.");
    }

    @Test
    @DisplayName("Skips when courier is not found")
    void skipsWhenCourierNotFound() {
        UUID eventId = UUID.randomUUID();
        ParcelCourierAssigned event = new ParcelCourierAssigned(
                eventId, PARCEL_ID, TRACKING_NUMBER, COURIER_ID, DISPATCHER_ID, ParcelAssignmentType.PICKUP, OCCURRED_AT);

        when(userDirectory.findUser(COURIER_ID)).thenReturn(Optional.empty());

        listener.onParcelCourierAssigned(event);

        verify(recordRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Skips when courier account is disabled")
    void skipsWhenCourierDisabled() {
        UUID eventId = UUID.randomUUID();
        ParcelCourierAssigned event = new ParcelCourierAssigned(
                eventId, PARCEL_ID, TRACKING_NUMBER, COURIER_ID, DISPATCHER_ID, ParcelAssignmentType.PICKUP, OCCURRED_AT);

        when(userDirectory.findUser(COURIER_ID)).thenReturn(Optional.of(
                new UserSummary(COURIER_ID, "courier@swiftdrop.com", "Disabled Courier", Set.of("COURIER"), false)));

        listener.onParcelCourierAssigned(event);

        verify(recordRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Skips duplicate event when already exists in repository pre-check")
    void skipsDuplicateEventInPreCheck() {
        UUID eventId = UUID.randomUUID();
        ParcelCourierAssigned event = new ParcelCourierAssigned(
                eventId, PARCEL_ID, TRACKING_NUMBER, COURIER_ID, DISPATCHER_ID, ParcelAssignmentType.PICKUP, OCCURRED_AT);

        when(userDirectory.findUser(COURIER_ID)).thenReturn(Optional.of(
                new UserSummary(COURIER_ID, "courier@swiftdrop.com", "Test Courier", Set.of("COURIER"), true)));
        when(recordRepository.existsByEventIdAndChannelAndRecipientUserId(eventId, NotificationChannel.IN_APP, COURIER_ID))
                .thenReturn(true);

        listener.onParcelCourierAssigned(event);

        verify(recordRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Notifies both previous and new courier on reassignment")
    void notifiesBothCouriersOnReassignment() {
        UUID eventId = UUID.randomUUID();
        UUID previousCourierId = UUID.randomUUID();
        ParcelCourierAssigned event = new ParcelCourierAssigned(
                eventId, PARCEL_ID, TRACKING_NUMBER, COURIER_ID, DISPATCHER_ID,
                ParcelAssignmentType.DELIVERY, OCCURRED_AT, previousCourierId);

        when(userDirectory.findUser(COURIER_ID)).thenReturn(Optional.of(
                new UserSummary(COURIER_ID, "new@swiftdrop.com", "New Courier", Set.of("COURIER"), true)));
        when(userDirectory.findUser(previousCourierId)).thenReturn(Optional.of(
                new UserSummary(previousCourierId, "old@swiftdrop.com", "Old Courier", Set.of("COURIER"), true)));
        when(recordRepository.existsByEventIdAndChannelAndRecipientUserId(any(), any(), any())).thenReturn(false);
        when(recordRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        listener.onParcelCourierAssigned(event);

        verify(recordRepository).insertIfAbsent(
                any(), eq(eventId), eq(COURIER_ID), eq(PARCEL_ID), eq(TRACKING_NUMBER),
                eq("New Delivery Task"), eq("You have been assigned to deliver parcel " + TRACKING_NUMBER + "."),
                eq(NotificationChannel.IN_APP.name()), eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()), eq(OCCURRED_AT));
        verify(recordRepository).insertIfAbsent(
                any(), eq(eventId), eq(previousCourierId), eq(PARCEL_ID), eq(TRACKING_NUMBER),
                eq("Parcel Assignment Transferred"),
                eq("Parcel " + TRACKING_NUMBER + " has been reassigned to another courier."),
                eq(NotificationChannel.IN_APP.name()), eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()), eq(OCCURRED_AT));
    }

    @Test
    @DisplayName("Skips previous courier notification when missing or disabled without affecting new courier")
    void skipsMissingOrDisabledPreviousCourier() {
        UUID eventId = UUID.randomUUID();
        UUID previousCourierId = UUID.randomUUID();
        ParcelCourierAssigned event = new ParcelCourierAssigned(
                eventId, PARCEL_ID, TRACKING_NUMBER, COURIER_ID, DISPATCHER_ID,
                ParcelAssignmentType.RETURN, OCCURRED_AT, previousCourierId);

        when(userDirectory.findUser(COURIER_ID)).thenReturn(Optional.of(
                new UserSummary(COURIER_ID, "new@swiftdrop.com", "New Courier", Set.of("COURIER"), true)));
        when(userDirectory.findUser(previousCourierId)).thenReturn(Optional.of(
                new UserSummary(previousCourierId, "old@swiftdrop.com", "Old Courier", Set.of("COURIER"), false)));
        when(recordRepository.existsByEventIdAndChannelAndRecipientUserId(any(), any(), any())).thenReturn(false);
        when(recordRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        listener.onParcelCourierAssigned(event);

        verify(recordRepository, times(1)).insertIfAbsent(
                any(), eq(eventId), eq(COURIER_ID), eq(PARCEL_ID), eq(TRACKING_NUMBER),
                any(), any(), any(), any(), any());
        verify(recordRepository, never()).insertIfAbsent(
                any(), any(), eq(previousCourierId), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Failure in previous-courier processing does not suppress new-courier notification")
    void failureInPreviousCourierDoesNotSuppressNewCourier() {
        UUID eventId = UUID.randomUUID();
        UUID previousCourierId = UUID.randomUUID();
        ParcelCourierAssigned event = new ParcelCourierAssigned(
                eventId, PARCEL_ID, TRACKING_NUMBER, COURIER_ID, DISPATCHER_ID,
                ParcelAssignmentType.PICKUP, OCCURRED_AT, previousCourierId);

        when(userDirectory.findUser(COURIER_ID)).thenReturn(Optional.of(
                new UserSummary(COURIER_ID, "new@swiftdrop.com", "New Courier", Set.of("COURIER"), true)));
        when(userDirectory.findUser(previousCourierId)).thenThrow(new RuntimeException("Transient lookup error"));
        when(recordRepository.existsByEventIdAndChannelAndRecipientUserId(any(), any(), any())).thenReturn(false);
        when(recordRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        listener.onParcelCourierAssigned(event);

        verify(recordRepository, times(1)).insertIfAbsent(
                any(), eq(eventId), eq(COURIER_ID), eq(PARCEL_ID), eq(TRACKING_NUMBER),
                any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Notifies courier when active PICKUP, DELIVERY, or RETURN assignment is cancelled")
    void notifiesCourierOnAssignmentCancellation() {
        when(userDirectory.findUser(COURIER_ID)).thenReturn(Optional.of(
                new UserSummary(COURIER_ID, "courier@swiftdrop.com", "Test Courier", Set.of("COURIER"), true)));
        when(recordRepository.existsByEventIdAndChannelAndRecipientUserId(any(), any(), any())).thenReturn(false);
        when(recordRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        for (ParcelAssignmentType type : ParcelAssignmentType.values()) {
            UUID eventId = UUID.randomUUID();
            com.swiftdrop.delivery.api.ParcelCourierAssignmentCancelled event =
                    new com.swiftdrop.delivery.api.ParcelCourierAssignmentCancelled(
                            eventId, PARCEL_ID, TRACKING_NUMBER, COURIER_ID, type, OCCURRED_AT);

            listener.onParcelCourierAssignmentCancelled(event);

            verify(recordRepository).insertIfAbsent(
                    any(), eq(eventId), eq(COURIER_ID), eq(PARCEL_ID), eq(TRACKING_NUMBER),
                    eq("Parcel Assignment Cancelled"),
                    eq("The courier assignment for parcel " + TRACKING_NUMBER + " has been cancelled by the dispatcher."),
                    eq(NotificationChannel.IN_APP.name()), eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()), eq(OCCURRED_AT));
        }
    }

    @Test
    @DisplayName("Skips cancellation notification when courier is missing or disabled")
    void skipsCancellationWhenCourierMissingOrDisabled() {
        UUID eventId = UUID.randomUUID();
        com.swiftdrop.delivery.api.ParcelCourierAssignmentCancelled event =
                new com.swiftdrop.delivery.api.ParcelCourierAssignmentCancelled(
                        eventId, PARCEL_ID, TRACKING_NUMBER, COURIER_ID, ParcelAssignmentType.PICKUP, OCCURRED_AT);

        when(userDirectory.findUser(COURIER_ID)).thenReturn(Optional.empty());
        listener.onParcelCourierAssignmentCancelled(event);

        when(userDirectory.findUser(COURIER_ID)).thenReturn(Optional.of(
                new UserSummary(COURIER_ID, "courier@swiftdrop.com", "Disabled Courier", Set.of("COURIER"), false)));
        listener.onParcelCourierAssignmentCancelled(event);

        verify(recordRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Skips duplicate cancellation event when already exists in repository pre-check")
    void skipsDuplicateCancellationEvent() {
        UUID eventId = UUID.randomUUID();
        com.swiftdrop.delivery.api.ParcelCourierAssignmentCancelled event =
                new com.swiftdrop.delivery.api.ParcelCourierAssignmentCancelled(
                        eventId, PARCEL_ID, TRACKING_NUMBER, COURIER_ID, ParcelAssignmentType.DELIVERY, OCCURRED_AT);

        when(userDirectory.findUser(COURIER_ID)).thenReturn(Optional.of(
                new UserSummary(COURIER_ID, "courier@swiftdrop.com", "Test Courier", Set.of("COURIER"), true)));
        when(recordRepository.existsByEventIdAndChannelAndRecipientUserId(eventId, NotificationChannel.IN_APP, COURIER_ID))
                .thenReturn(true);

        listener.onParcelCourierAssignmentCancelled(event);

        verify(recordRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}

