package com.swiftdrop.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
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
import com.swiftdrop.notification.application.PushNotificationSender.PushSendResult;
import com.swiftdrop.notification.domain.NotificationChannel;
import com.swiftdrop.notification.domain.NotificationConsumerState;
import com.swiftdrop.notification.domain.NotificationConsumerStateRepository;
import com.swiftdrop.notification.domain.NotificationDeliveryStatus;
import com.swiftdrop.notification.domain.NotificationPendingEventRepository;
import com.swiftdrop.notification.domain.NotificationPushToken;
import com.swiftdrop.notification.domain.NotificationPushTokenRepository;
import com.swiftdrop.notification.domain.NotificationRecordRepository;

class NotificationPushDispatchTest {

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
    private PushNotificationSender pushSender;
    private NotificationPushTokenRepository pushTokenRepository;
    private NotificationEventProcessor processor;

    private final UUID parcelId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final String trackingNumber = "SD-PUSH-001";
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
        pushSender = mock(PushNotificationSender.class);
        pushTokenRepository = mock(NotificationPushTokenRepository.class);

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
                smsSender,
                pushSender,
                pushTokenRepository);

        when(consumerStateRepository.save(any(NotificationConsumerState.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(consumerStateRepository.findById(parcelId)).thenReturn(Optional.empty());
        when(userDirectory.findUser(customerId)).thenReturn(Optional.of(
                new UserSummary(customerId, "Alice", "alice@example.com", "+94771234567",
                        Set.of("CUSTOMER"), true, true)));
        when(recordRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
    }

    private ParcelStatusChanged createEvent() {
        ParcelStatusChanged event = ParcelStatusChanged.of(
                UUID.randomUUID(), now,
                new ParcelStatusChangedData(parcelId, trackingNumber, null, ParcelStatus.CREATED, 1,
                        ParcelSource.CUSTOMER, customerId, null, null, null));
        when(templateResolver.resolve(event))
                .thenReturn(Optional.of(new NotificationContent("Parcel Booked", "Booked msg")));
        return event;
    }

    @Test
    @DisplayName("Dispatches PUSH to multiple active tokens and marks PUSH record SENT")
    void dispatchesToMultipleActiveTokens() {
        NotificationPushToken t1 = new NotificationPushToken(customerId, "tok-1", "WEB", now);
        NotificationPushToken t2 = new NotificationPushToken(customerId, "tok-2", "WEB", now);
        when(pushTokenRepository.findByUserIdAndIsActiveTrue(customerId)).thenReturn(List.of(t1, t2));
        when(pushSender.send(any(), any(), any(), anyMap())).thenReturn(PushSendResult.SENT);

        processor.process(createEvent(), "{\"sequence\":1}");

        verify(pushSender).send(eq("tok-1"), eq("Parcel Booked"), eq("Booked msg"), anyMap());
        verify(pushSender).send(eq("tok-2"), eq("Parcel Booked"), eq("Booked msg"), anyMap());
        verify(recordRepository, org.mockito.Mockito.times(3))
                .updateDeliveryStatus(any(), eq(NotificationDeliveryStatus.SENT));
    }

    @Test
    @DisplayName("Skips PUSH record and send when user has no active tokens")
    void skipsWhenNoActiveTokens() {
        when(pushTokenRepository.findByUserIdAndIsActiveTrue(customerId)).thenReturn(List.of());

        processor.process(createEvent(), "{\"sequence\":1}");

        verify(pushSender, never()).send(any(), any(), any(), anyMap());
        verify(recordRepository, never()).insertIfAbsent(
                any(), any(), any(), any(), any(), any(), any(),
                eq(NotificationChannel.PUSH.name()), any(), any());
    }

    @Test
    @DisplayName("Marks PUSH record SENT when one token succeeds and another fails")
    void partialSuccessMarksSent() {
        NotificationPushToken t1 = new NotificationPushToken(customerId, "tok-ok", "WEB", now);
        NotificationPushToken t2 = new NotificationPushToken(customerId, "tok-fail", "WEB", now);
        when(pushTokenRepository.findByUserIdAndIsActiveTrue(customerId)).thenReturn(List.of(t1, t2));
        when(pushSender.send(eq("tok-ok"), any(), any(), anyMap())).thenReturn(PushSendResult.SENT);
        when(pushSender.send(eq("tok-fail"), any(), any(), anyMap()))
                .thenThrow(new IllegalStateException("FCM transient error"));

        processor.process(createEvent(), "{\"sequence\":1}");

        verify(recordRepository, org.mockito.Mockito.times(3))
                .updateDeliveryStatus(any(), eq(NotificationDeliveryStatus.SENT));
        assertThat(t2.isActive()).isTrue();
    }

    @Test
    @DisplayName("Marks PUSH record FAILED when all tokens fail without affecting IN_APP, EMAIL, or SMS")
    void allTokensFailMarksFailedWithoutBreakingOtherChannels() {
        NotificationPushToken t1 = new NotificationPushToken(customerId, "tok-err", "WEB", now);
        when(pushTokenRepository.findByUserIdAndIsActiveTrue(customerId)).thenReturn(List.of(t1));
        when(pushSender.send(eq("tok-err"), any(), any(), anyMap()))
                .thenThrow(new IllegalStateException("FCM down"));

        processor.process(createEvent(), "{\"sequence\":1}");

        verify(recordRepository).insertIfAbsent(
                any(), any(), eq(customerId), eq(parcelId), eq(trackingNumber),
                eq("Parcel Booked"), eq("Booked msg"),
                eq(NotificationChannel.IN_APP.name()), eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()), eq(now));
        verify(emailSender).send("alice@example.com", "Parcel Booked", "Booked msg");
        verify(smsSender).send("+94771234567", "Booked msg");
        verify(recordRepository).updateDeliveryStatus(any(), eq(NotificationDeliveryStatus.FAILED));
    }

    @Test
    @DisplayName("Deactivates UNREGISTERED token and keeps INVALID_ARGUMENT token active")
    void deactivatesOnlyUnregisteredToken() {
        NotificationPushToken unregistered = new NotificationPushToken(customerId, "tok-unreg", "WEB", now);
        NotificationPushToken invalidArg = new NotificationPushToken(customerId, "tok-bad-arg", "WEB", now);
        when(pushTokenRepository.findByUserIdAndIsActiveTrue(customerId))
                .thenReturn(List.of(unregistered, invalidArg));
        when(pushSender.send(eq("tok-unreg"), any(), any(), anyMap()))
                .thenReturn(PushSendResult.INVALID_TOKEN);
        when(pushSender.send(eq("tok-bad-arg"), any(), any(), anyMap()))
                .thenThrow(new IllegalStateException("FCM push dispatch failed (errorCode=INVALID_ARGUMENT)"));

        processor.process(createEvent(), "{\"sequence\":1}");

        assertThat(unregistered.isActive()).isFalse();
        verify(pushTokenRepository).save(unregistered);
        assertThat(invalidArg.isActive()).isTrue();
    }

    @Test
    @DisplayName("Does not dispatch PUSH when insertIfAbsent returns 0 for duplicate event")
    void preventsDuplicatePushSends() {
        NotificationPushToken t1 = new NotificationPushToken(customerId, "tok-1", "WEB", now);
        when(pushTokenRepository.findByUserIdAndIsActiveTrue(customerId)).thenReturn(List.of(t1));
        when(recordRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        processor.process(createEvent(), "{\"sequence\":1}");

        verify(pushSender, never()).send(any(), any(), any(), anyMap());
    }
}
