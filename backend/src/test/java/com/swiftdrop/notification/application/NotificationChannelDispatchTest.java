package com.swiftdrop.notification.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

import com.swiftdrop.delivery.api.ParcelEvents;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;
import com.swiftdrop.merchant.api.MerchantLookup;
import com.swiftdrop.notification.application.NotificationTemplateResolver.NotificationContent;
import com.swiftdrop.notification.domain.NotificationChannel;
import com.swiftdrop.notification.domain.NotificationConsumerState;
import com.swiftdrop.notification.domain.NotificationConsumerStateRepository;
import com.swiftdrop.notification.domain.NotificationDeliveryStatus;
import com.swiftdrop.notification.domain.NotificationPendingEventRepository;
import com.swiftdrop.notification.domain.NotificationRecordRepository;

class NotificationChannelDispatchTest {

    private NotificationConsumerStateRepository consumerStateRepository;
    private NotificationPendingEventRepository pendingEventRepository;
    private NotificationRecordRepository recordRepository;
    private NotificationTemplateResolver templateResolver;
    private ParcelStatusEventParser eventParser;
    private ParcelEvents parcelEvents;
    private UserDirectory userDirectory;
    private MerchantLookup merchantLookup;
    private EmailNotificationSender emailSender;
    private SmsNotificationSender smsSender;
    private NotificationEventProcessor processor;

    private final UUID parcelId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final String trackingNumber = "SD-CHAN-001";
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        consumerStateRepository = mock(NotificationConsumerStateRepository.class);
        pendingEventRepository = mock(NotificationPendingEventRepository.class);
        recordRepository = mock(NotificationRecordRepository.class);
        templateResolver = mock(NotificationTemplateResolver.class);
        eventParser = mock(ParcelStatusEventParser.class);
        parcelEvents = mock(ParcelEvents.class);
        userDirectory = mock(UserDirectory.class);
        merchantLookup = mock(MerchantLookup.class);
        emailSender = mock(EmailNotificationSender.class);
        smsSender = mock(SmsNotificationSender.class);

        processor = new NotificationEventProcessor(
                consumerStateRepository,
                pendingEventRepository,
                recordRepository,
                templateResolver,
                eventParser,
                parcelEvents,
                userDirectory,
                merchantLookup,
                emailSender,
                smsSender);
    }

    @Test
    @DisplayName("Dispatches IN_APP, EMAIL, and SMS for customer parcel event when contact info is present")
    void dispatchesEmailAndSmsWithInApp() {
        when(consumerStateRepository.save(any(NotificationConsumerState.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(consumerStateRepository.findById(parcelId)).thenReturn(Optional.empty());
        when(userDirectory.findUser(customerId)).thenReturn(Optional.of(
                new UserSummary(customerId, "Alice", "alice@example.com", "+94771234567", Set.of("CUSTOMER"), true, true)));

        ParcelStatusChanged event = ParcelStatusChanged.of(
                UUID.randomUUID(), now,
                new ParcelStatusChangedData(parcelId, trackingNumber, null, ParcelStatus.CREATED, 1,
                        ParcelSource.CUSTOMER, customerId, null, null, null));

        when(templateResolver.resolve(event)).thenReturn(Optional.of(new NotificationContent("Parcel Booked", "Booked msg")));
        when(recordRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        processor.process(event, "{\"sequence\":1}");

        verify(recordRepository).insertIfAbsent(any(), eq(event.eventId()), eq(customerId), eq(parcelId), eq(trackingNumber),
                eq("Parcel Booked"), eq("Booked msg"), eq(NotificationChannel.IN_APP.name()),
                eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()), eq(now));
        verify(emailSender).send("alice@example.com", "Parcel Booked", "Booked msg");
        verify(smsSender).send("+94771234567", "Booked msg");
    }

    @Test
    @DisplayName("Skips SMS safely when phone number is missing while still sending EMAIL")
    void skipsSmsWhenPhoneNumberMissing() {
        when(consumerStateRepository.save(any(NotificationConsumerState.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(consumerStateRepository.findById(parcelId)).thenReturn(Optional.empty());
        when(userDirectory.findUser(customerId)).thenReturn(Optional.of(
                new UserSummary(customerId, "Alice", "alice@example.com", null, Set.of("CUSTOMER"), true, true)));

        ParcelStatusChanged event = ParcelStatusChanged.of(
                UUID.randomUUID(), now,
                new ParcelStatusChangedData(parcelId, trackingNumber, null, ParcelStatus.CREATED, 1,
                        ParcelSource.CUSTOMER, customerId, null, null, null));

        when(templateResolver.resolve(event)).thenReturn(Optional.of(new NotificationContent("Parcel Booked", "Booked msg")));
        when(recordRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        processor.process(event, "{\"sequence\":1}");

        verify(emailSender).send("alice@example.com", "Parcel Booked", "Booked msg");
        verify(smsSender, never()).send(any(), any());
    }

    @Test
    @DisplayName("Does not send duplicate EMAIL or SMS when insertIfAbsent returns 0")
    void preventsDuplicateChannelSends() {
        when(consumerStateRepository.save(any(NotificationConsumerState.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(consumerStateRepository.findById(parcelId)).thenReturn(Optional.empty());
        when(userDirectory.findUser(customerId)).thenReturn(Optional.of(
                new UserSummary(customerId, "Alice", "alice@example.com", "+94771234567", Set.of("CUSTOMER"), true, true)));

        ParcelStatusChanged event = ParcelStatusChanged.of(
                UUID.randomUUID(), now,
                new ParcelStatusChangedData(parcelId, trackingNumber, null, ParcelStatus.CREATED, 1,
                        ParcelSource.CUSTOMER, customerId, null, null, null));

        when(templateResolver.resolve(event)).thenReturn(Optional.of(new NotificationContent("Parcel Booked", "Booked msg")));
        when(recordRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        processor.process(event, "{\"sequence\":1}");

        verify(emailSender, never()).send(any(), any(), any());
        verify(smsSender, never()).send(any(), any());
    }

    @Test
    @DisplayName("Failure in EmailNotificationSender does not suppress SMS notification")
    void emailFailureDoesNotSuppressSms() {
        when(consumerStateRepository.save(any(NotificationConsumerState.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(consumerStateRepository.findById(parcelId)).thenReturn(Optional.empty());
        when(userDirectory.findUser(customerId)).thenReturn(Optional.of(
                new UserSummary(customerId, "Alice", "alice@example.com", "+94771234567", Set.of("CUSTOMER"), true, true)));

        ParcelStatusChanged event = ParcelStatusChanged.of(
                UUID.randomUUID(), now,
                new ParcelStatusChangedData(parcelId, trackingNumber, null, ParcelStatus.CREATED, 1,
                        ParcelSource.CUSTOMER, customerId, null, null, null));

        when(templateResolver.resolve(event)).thenReturn(Optional.of(new NotificationContent("Parcel Booked", "Booked msg")));
        when(recordRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        doThrow(new RuntimeException("SMTP unavailable")).when(emailSender).send(any(), any(), any());

        processor.process(event, "{\"sequence\":1}");

        verify(recordRepository).updateDeliveryStatus(any(), eq(NotificationDeliveryStatus.FAILED));
        verify(smsSender).send("+94771234567", "Booked msg");
        verify(recordRepository).updateDeliveryStatus(any(), eq(NotificationDeliveryStatus.SENT));
    }
}

