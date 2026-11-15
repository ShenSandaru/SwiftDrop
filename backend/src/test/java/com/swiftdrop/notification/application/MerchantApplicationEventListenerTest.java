package com.swiftdrop.notification.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;
import com.swiftdrop.merchant.api.MerchantApplicationSubmitted;
import com.swiftdrop.notification.domain.NotificationChannel;
import com.swiftdrop.notification.domain.NotificationDeliveryStatus;
import com.swiftdrop.notification.domain.NotificationRecordRepository;

@DisplayName("MerchantApplicationEventListener unit tests")
class MerchantApplicationEventListenerTest {

    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID APPLICANT_USER_ID = UUID.randomUUID();
    private static final Instant SUBMITTED_AT = Instant.parse("2026-11-14T11:30:00Z");

    private NotificationRecordRepository recordRepository;
    private UserDirectory userDirectory;
    private MerchantApplicationEventListener listener;

    @BeforeEach
    void setUp() {
        recordRepository = mock(NotificationRecordRepository.class);
        userDirectory = mock(UserDirectory.class);
        listener = new MerchantApplicationEventListener(recordRepository, userDirectory);
    }

    @Test
    @DisplayName("Creates account-level notification when one enabled administrator exists")
    void createsNotificationForSingleAdmin() {
        UUID eventId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        MerchantApplicationSubmitted event = new MerchantApplicationSubmitted(
                eventId, MERCHANT_ID, APPLICANT_USER_ID, "Acme Shop", "acme@example.com", SUBMITTED_AT);

        when(userDirectory.findEnabledAdmins()).thenReturn(List.of(
                new UserSummary(adminId, "admin@swiftdrop.com", "Admin One", Set.of("ADMIN"), true)));
        when(recordRepository.insertIfAbsent(
                any(), eq(eventId), eq(adminId), isNull(), isNull(),
                eq("New Merchant Application"),
                eq("A new merchant application has been submitted by Acme Shop."),
                eq(NotificationChannel.IN_APP.name()),
                eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()),
                eq(SUBMITTED_AT)))
                .thenReturn(1);

        listener.onMerchantApplicationSubmitted(event);

        verify(recordRepository).insertIfAbsent(
                any(), eq(eventId), eq(adminId), isNull(), isNull(),
                eq("New Merchant Application"),
                eq("A new merchant application has been submitted by Acme Shop."),
                eq(NotificationChannel.IN_APP.name()),
                eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()),
                eq(SUBMITTED_AT));
    }

    @Test
    @DisplayName("Creates notification for each enabled administrator when multiple exist")
    void createsNotificationsForMultipleAdmins() {
        UUID eventId = UUID.randomUUID();
        UUID admin1 = UUID.randomUUID();
        UUID admin2 = UUID.randomUUID();
        MerchantApplicationSubmitted event = new MerchantApplicationSubmitted(
                eventId, MERCHANT_ID, APPLICANT_USER_ID, "Beta Logistics", "beta@example.com", SUBMITTED_AT);

        when(userDirectory.findEnabledAdmins()).thenReturn(List.of(
                new UserSummary(admin1, "admin1@swiftdrop.com", "Admin One", Set.of("ADMIN"), true),
                new UserSummary(admin2, "admin2@swiftdrop.com", "Admin Two", Set.of("ADMIN"), true)));
        when(recordRepository.insertIfAbsent(any(), eq(eventId), any(), isNull(), isNull(),
                any(), any(), any(), any(), any())).thenReturn(1);

        listener.onMerchantApplicationSubmitted(event);

        verify(recordRepository).insertIfAbsent(
                any(), eq(eventId), eq(admin1), isNull(), isNull(),
                eq("New Merchant Application"),
                eq("A new merchant application has been submitted by Beta Logistics."),
                eq(NotificationChannel.IN_APP.name()),
                eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()),
                eq(SUBMITTED_AT));
        verify(recordRepository).insertIfAbsent(
                any(), eq(eventId), eq(admin2), isNull(), isNull(),
                eq("New Merchant Application"),
                eq("A new merchant application has been submitted by Beta Logistics."),
                eq(NotificationChannel.IN_APP.name()),
                eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()),
                eq(SUBMITTED_AT));
    }

    @Test
    @DisplayName("Skips notification creation when no enabled administrators exist")
    void skipsWhenNoEnabledAdmins() {
        UUID eventId = UUID.randomUUID();
        MerchantApplicationSubmitted event = new MerchantApplicationSubmitted(
                eventId, MERCHANT_ID, APPLICANT_USER_ID, "Acme Shop", "acme@example.com", SUBMITTED_AT);

        when(userDirectory.findEnabledAdmins()).thenReturn(List.of());

        listener.onMerchantApplicationSubmitted(event);

        verify(recordRepository, never()).insertIfAbsent(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Duplicate event delivery relies on insertIfAbsent idempotency without error")
    void duplicateEventHandledIdempotently() {
        UUID eventId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        MerchantApplicationSubmitted event = new MerchantApplicationSubmitted(
                eventId, MERCHANT_ID, APPLICANT_USER_ID, "Acme Shop", "acme@example.com", SUBMITTED_AT);

        when(userDirectory.findEnabledAdmins()).thenReturn(List.of(
                new UserSummary(adminId, "admin@swiftdrop.com", "Admin One", Set.of("ADMIN"), true)));
        when(recordRepository.insertIfAbsent(any(), eq(eventId), eq(adminId), isNull(), isNull(),
                any(), any(), any(), any(), any()))
                .thenReturn(1)
                .thenReturn(0);

        listener.onMerchantApplicationSubmitted(event);
        listener.onMerchantApplicationSubmitted(event);

        verify(recordRepository, times(2)).insertIfAbsent(
                any(), eq(eventId), eq(adminId), isNull(), isNull(),
                eq("New Merchant Application"),
                eq("A new merchant application has been submitted by Acme Shop."),
                eq(NotificationChannel.IN_APP.name()),
                eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()),
                eq(SUBMITTED_AT));
    }
}

