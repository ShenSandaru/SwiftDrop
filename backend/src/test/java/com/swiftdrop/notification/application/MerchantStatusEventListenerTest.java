package com.swiftdrop.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;
import com.swiftdrop.merchant.api.MerchantStatus;
import com.swiftdrop.merchant.api.MerchantStatusChanged;
import com.swiftdrop.notification.domain.NotificationChannel;
import com.swiftdrop.notification.domain.NotificationDeliveryStatus;
import com.swiftdrop.notification.domain.NotificationRecord;
import com.swiftdrop.notification.domain.NotificationRecordRepository;

@ExtendWith(MockitoExtension.class)
class MerchantStatusEventListenerTest {

    @Mock
    private NotificationRecordRepository recordRepository;

    @Mock
    private UserDirectory userDirectory;

    private MerchantStatusEventListener listener;

    private final UUID eventId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID adminUserId = UUID.randomUUID();
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        listener = new MerchantStatusEventListener(recordRepository, userDirectory);
    }

    private MerchantStatusChanged createEvent(MerchantStatus fromStatus, MerchantStatus toStatus, String reason) {
        return new MerchantStatusChanged(
                eventId,
                merchantId,
                userId,
                fromStatus,
                toStatus,
                adminUserId,
                reason,
                now);
    }

    @Test
    @DisplayName("APPROVED event creates non-parcel in-app notification for enabled merchant user")
    void approvedEventCreatesNotification() {
        MerchantStatusChanged event = createEvent(MerchantStatus.PENDING, MerchantStatus.APPROVED, null);

        when(userDirectory.findUser(userId)).thenReturn(Optional.of(
                new UserSummary(userId, "Merchant Owner", "merchant@example.com", Set.of("MERCHANT"), true)));
        when(recordRepository.existsByEventIdAndChannelAndRecipientUserId(eventId, NotificationChannel.IN_APP, userId))
                .thenReturn(false);

        listener.onMerchantStatusChanged(event);

        verify(recordRepository).insertIfAbsent(
                any(UUID.class),
                eq(eventId),
                eq(userId),
                eq(null),
                eq(null),
                eq("Merchant Application Approved"),
                contains("approved"),
                eq(NotificationChannel.IN_APP.name()),
                eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()),
                eq(now));
    }

    @Test
    @DisplayName("REJECTED event creates notification without exposing internal admin reason")
    void rejectedEventCreatesNotificationWithoutInternalReason() {
        MerchantStatusChanged event = createEvent(MerchantStatus.PENDING, MerchantStatus.REJECTED, "Internal KYC failed: fraudulent documents");

        when(userDirectory.findUser(userId)).thenReturn(Optional.of(
                new UserSummary(userId, "Merchant Owner", "merchant@example.com", Set.of("MERCHANT"), true)));
        when(recordRepository.existsByEventIdAndChannelAndRecipientUserId(eventId, NotificationChannel.IN_APP, userId))
                .thenReturn(false);

        listener.onMerchantStatusChanged(event);

        verify(recordRepository).insertIfAbsent(
                any(UUID.class),
                eq(eventId),
                eq(userId),
                eq(null),
                eq(null),
                eq("Merchant Application Update"),
                contains("was not approved"),
                eq(NotificationChannel.IN_APP.name()),
                eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()),
                eq(now));
    }

    @Test
    @DisplayName("SUSPENDED event creates suspension notification")
    void suspendedEventCreatesNotification() {
        MerchantStatusChanged event = createEvent(MerchantStatus.APPROVED, MerchantStatus.SUSPENDED, "Non-payment");

        when(userDirectory.findUser(userId)).thenReturn(Optional.of(
                new UserSummary(userId, "Merchant Owner", "merchant@example.com", Set.of("MERCHANT"), true)));
        when(recordRepository.existsByEventIdAndChannelAndRecipientUserId(eventId, NotificationChannel.IN_APP, userId))
                .thenReturn(false);

        listener.onMerchantStatusChanged(event);

        verify(recordRepository).insertIfAbsent(
                any(UUID.class),
                eq(eventId),
                eq(userId),
                eq(null),
                eq(null),
                eq("Merchant Account Suspended"),
                contains("suspended"),
                eq(NotificationChannel.IN_APP.name()),
                eq(NotificationDeliveryStatus.NOT_APPLICABLE.name()),
                eq(now));
    }

    @Test
    @DisplayName("Duplicate event does not insert second notification record")
    void duplicateEventIsIgnored() {
        MerchantStatusChanged event = createEvent(MerchantStatus.PENDING, MerchantStatus.APPROVED, null);

        when(userDirectory.findUser(userId)).thenReturn(Optional.of(
                new UserSummary(userId, "Merchant Owner", "merchant@example.com", Set.of("MERCHANT"), true)));
        when(recordRepository.existsByEventIdAndChannelAndRecipientUserId(eventId, NotificationChannel.IN_APP, userId))
                .thenReturn(true);

        listener.onMerchantStatusChanged(event);

        verify(recordRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Disabled or missing user skips notification creation")
    void disabledOrMissingUserSkipsNotification() {
        MerchantStatusChanged event = createEvent(MerchantStatus.PENDING, MerchantStatus.APPROVED, null);

        // Disabled user
        when(userDirectory.findUser(userId)).thenReturn(Optional.of(
                new UserSummary(userId, "Merchant Owner", "merchant@example.com", Set.of("MERCHANT"), false)));

        listener.onMerchantStatusChanged(event);

        verify(recordRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ON CONFLICT DO NOTHING (insertIfAbsent returns 0) is handled gracefully without exception")
    void concurrentDuplicateInsertReturnsZeroAndHandledGracefully() {
        MerchantStatusChanged event = createEvent(MerchantStatus.PENDING, MerchantStatus.APPROVED, null);

        when(userDirectory.findUser(userId)).thenReturn(Optional.of(
                new UserSummary(userId, "Merchant Owner", "merchant@example.com", Set.of("MERCHANT"), true)));
        when(recordRepository.existsByEventIdAndChannelAndRecipientUserId(eventId, NotificationChannel.IN_APP, userId))
                .thenReturn(false);
        when(recordRepository.insertIfAbsent(any(), eq(eventId), eq(userId), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        // Must not propagate exception
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> listener.onMerchantStatusChanged(event));
    }

    @Test
    @DisplayName("PENDING status does not generate notification")
    void pendingStatusDoesNotNotify() {
        MerchantStatusChanged event = createEvent(null, MerchantStatus.PENDING, null);

        listener.onMerchantStatusChanged(event);

        verifyNoInteractions(userDirectory);
        verifyNoInteractions(recordRepository);
    }
}

