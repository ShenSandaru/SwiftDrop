package com.swiftdrop.notification.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;
import com.swiftdrop.notification.domain.NotificationChannel;
import com.swiftdrop.notification.domain.NotificationRecord;
import com.swiftdrop.notification.domain.NotificationRecordRepository;

/**
 * Periodically retries failed EMAIL and SMS notification deliveries with exponential backoff.
 * Uses {@code FOR UPDATE SKIP LOCKED} via {@link NotificationRecordRepository#findRetryEligibleRecords}
 * so concurrent workers do not process the same record simultaneously.
 */
@Service
@ConditionalOnProperty(name = "swiftdrop.notification.retry.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationRetryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryService.class);

    private final NotificationRecordRepository recordRepository;
    private final UserDirectory userDirectory;
    private final EmailNotificationSender emailSender;
    private final SmsNotificationSender smsSender;
    private final int maxAttempts;
    private final long baseDelaySeconds;
    private final int batchSize;

    public NotificationRetryService(
            NotificationRecordRepository recordRepository,
            UserDirectory userDirectory,
            EmailNotificationSender emailSender,
            SmsNotificationSender smsSender,
            @Value("${swiftdrop.notification.retry.max-attempts:3}") int maxAttempts,
            @Value("${swiftdrop.notification.retry.base-delay-seconds:60}") long baseDelaySeconds,
            @Value("${swiftdrop.notification.retry.batch-size:50}") int batchSize) {
        this.recordRepository = Objects.requireNonNull(recordRepository, "recordRepository");
        this.userDirectory = Objects.requireNonNull(userDirectory, "userDirectory");
        this.emailSender = Objects.requireNonNull(emailSender, "emailSender");
        this.smsSender = Objects.requireNonNull(smsSender, "smsSender");
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseDelaySeconds = Math.max(1L, baseDelaySeconds);
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${swiftdrop.notification.retry.poll-interval-ms:60000}")
    @Transactional
    public void retryFailedDeliveries() {
        Instant now = Instant.now();
        List<NotificationRecord> eligible = recordRepository.findRetryEligibleRecords(now, maxAttempts, batchSize);
        for (NotificationRecord record : eligible) {
            retryRecord(record, now);
        }
    }

    void retryRecord(NotificationRecord record, Instant now) {
        if (record.getChannel() != NotificationChannel.EMAIL && record.getChannel() != NotificationChannel.SMS) {
            return;
        }

        Optional<UserSummary> userOpt = userDirectory.findUser(record.getRecipientUserId());
        if (userOpt.isEmpty() || !userOpt.get().enabled()) {
            record.scheduleRetry(maxAttempts, null, "Recipient missing or disabled");
            recordRepository.save(record);
            return;
        }

        UserSummary user = userOpt.get();
        try {
            if (record.getChannel() == NotificationChannel.EMAIL) {
                if (user.email() == null || user.email().isBlank()) {
                    record.scheduleRetry(maxAttempts, null, "Recipient email missing");
                    recordRepository.save(record);
                    return;
                }
                emailSender.send(user.email(), record.getTitle(), record.getMessage());
            } else {
                if (user.phoneNumber() == null || user.phoneNumber().isBlank()) {
                    record.scheduleRetry(maxAttempts, null, "Recipient phone number missing");
                    recordRepository.save(record);
                    return;
                }
                smsSender.send(user.phoneNumber(), record.getMessage());
            }
            record.markAsSent(now);
            recordRepository.save(record);
        } catch (RuntimeException ex) {
            int nextAttempt = record.getRetryCount() + 1;
            if (nextAttempt >= maxAttempts) {
                log.warn("Notification {} ({}) permanently failed after {} attempts: {}",
                        record.getId(), record.getChannel(), nextAttempt, ex.getMessage());
                record.scheduleRetry(nextAttempt, null, ex.getMessage());
            } else {
                long delaySeconds = baseDelaySeconds * (1L << Math.min(nextAttempt - 1, 20));
                Instant nextRetryAt = now.plusSeconds(delaySeconds);
                log.warn("Notification {} ({}) retry {} failed; next retry at {}: {}",
                        record.getId(), record.getChannel(), nextAttempt, nextRetryAt, ex.getMessage());
                record.scheduleRetry(nextAttempt, nextRetryAt, ex.getMessage());
            }
            recordRepository.save(record);
        }
    }
}

