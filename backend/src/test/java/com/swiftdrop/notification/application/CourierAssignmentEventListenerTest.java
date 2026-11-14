package com.swiftdrop.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
}

