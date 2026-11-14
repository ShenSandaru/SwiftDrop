package com.swiftdrop.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
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

import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;
import com.swiftdrop.notification.domain.NotificationChannel;
import com.swiftdrop.notification.domain.NotificationDeliveryStatus;
import com.swiftdrop.notification.domain.NotificationRecord;
import com.swiftdrop.notification.domain.NotificationRecordRepository;

class NotificationRetryServiceTest {

    private NotificationRecordRepository recordRepository;
    private UserDirectory userDirectory;
    private EmailNotificationSender emailSender;
    private SmsNotificationSender smsSender;
    private NotificationRetryService retryService;

    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-14T12:00:00Z");

    @BeforeEach
    void setUp() {
        recordRepository = mock(NotificationRecordRepository.class);
        userDirectory = mock(UserDirectory.class);
        emailSender = mock(EmailNotificationSender.class);
        smsSender = mock(SmsNotificationSender.class);
        retryService = new NotificationRetryService(
                recordRepository, userDirectory, emailSender, smsSender, 3, 60L, 50);
    }

    @Test
    @DisplayName("Successful EMAIL retry marks record as SENT and clears nextRetryAt")
    void successfulEmailRetryMarksSent() {
        NotificationRecord record = NotificationRecord.forParcel(
                UUID.randomUUID(), userId, UUID.randomUUID(), "SD-1",
                "Title", "Body", NotificationChannel.EMAIL, NotificationDeliveryStatus.FAILED, now);

        when(recordRepository.findRetryEligibleRecords(any(), anyInt(), anyInt())).thenReturn(List.of(record));
        when(userDirectory.findUser(userId)).thenReturn(Optional.of(
                new UserSummary(userId, "Alice", "alice@example.com", "+94771234567", Set.of("CUSTOMER"), true, true)));

        retryService.retryFailedDeliveries();

        verify(emailSender).send("alice@example.com", "Title", "Body");
        assertThat(record.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(record.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("Repeated failure increments retryCount and schedules exponential backoff")
    void repeatedFailureSchedulesExponentialBackoff() {
        NotificationRecord record = NotificationRecord.forParcel(
                UUID.randomUUID(), userId, UUID.randomUUID(), "SD-2",
                "Title", "Body", NotificationChannel.SMS, NotificationDeliveryStatus.FAILED, now);

        when(userDirectory.findUser(userId)).thenReturn(Optional.of(
                new UserSummary(userId, "Alice", "alice@example.com", "+94771234567", Set.of("CUSTOMER"), true, true)));
        doThrow(new RuntimeException("Gateway timeout")).when(smsSender).send(any(), any());

        retryService.retryRecord(record, now);
        assertThat(record.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(record.getRetryCount()).isEqualTo(1);
        assertThat(record.getNextRetryAt()).isEqualTo(now.plusSeconds(60));

        retryService.retryRecord(record, now.plusSeconds(60));
        assertThat(record.getRetryCount()).isEqualTo(2);
        assertThat(record.getNextRetryAt()).isEqualTo(now.plusSeconds(180));
    }

    @Test
    @DisplayName("Exceeding maxAttempts leaves record permanently FAILED with null nextRetryAt")
    void maxAttemptsMarksPermanentlyFailed() {
        NotificationRecord record = NotificationRecord.forParcel(
                UUID.randomUUID(), userId, UUID.randomUUID(), "SD-3",
                "Title", "Body", NotificationChannel.EMAIL, NotificationDeliveryStatus.FAILED, now);
        record.scheduleRetry(2, now, "Previous error");

        when(userDirectory.findUser(userId)).thenReturn(Optional.of(
                new UserSummary(userId, "Alice", "alice@example.com", null, Set.of("CUSTOMER"), true, true)));
        doThrow(new RuntimeException("SMTP error")).when(emailSender).send(any(), any(), any());

        retryService.retryRecord(record, now);

        assertThat(record.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(record.getRetryCount()).isEqualTo(3);
        assertThat(record.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("IN_APP records are ignored by retryRecord")
    void inAppRecordsIgnored() {
        NotificationRecord record = NotificationRecord.forParcel(
                UUID.randomUUID(), userId, UUID.randomUUID(), "SD-4",
                "Title", "Body", NotificationChannel.IN_APP, NotificationDeliveryStatus.NOT_APPLICABLE, now);

        retryService.retryRecord(record, now);

        verify(userDirectory, never()).findUser(any());
        verify(emailSender, never()).send(any(), any(), any());
        verify(smsSender, never()).send(any(), any());
    }
}

