package com.swiftdrop.notification.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;
import com.swiftdrop.merchant.api.MerchantStatus;
import com.swiftdrop.merchant.api.MerchantStatusChanged;
import com.swiftdrop.notification.domain.NotificationChannel;
import com.swiftdrop.notification.domain.NotificationDeliveryStatus;
import com.swiftdrop.notification.domain.NotificationRecord;
import com.swiftdrop.notification.domain.NotificationRecordRepository;

/**
 * Listens for {@link MerchantStatusChanged} in-process application events published by the
 * merchant module after transaction commit (ADR 0003, ADR 0004 §3).
 * Resolves the recipient merchant user, validates the account is enabled, and creates
 * an idempotent in-app notification record.
 */
@Component("notificationMerchantStatusEventListener")
public class MerchantStatusEventListener {

    private static final Logger log = LoggerFactory.getLogger(MerchantStatusEventListener.class);

    private final NotificationRecordRepository recordRepository;
    private final UserDirectory userDirectory;

    public MerchantStatusEventListener(NotificationRecordRepository recordRepository, UserDirectory userDirectory) {
        this.recordRepository = Objects.requireNonNull(recordRepository, "recordRepository must not be null");
        this.userDirectory = Objects.requireNonNull(userDirectory, "userDirectory must not be null");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMerchantStatusChanged(MerchantStatusChanged event) {
        Objects.requireNonNull(event, "event must not be null");

        UUID eventId = event.eventId();
        UUID userId = event.userId();
        MerchantStatus toStatus = event.toStatus();

        MerchantNotificationContent content = resolveContent(toStatus);
        if (content == null) {
            log.debug("Merchant status {} does not require in-app notification. Skipping.", toStatus);
            return;
        }

        // Validate recipient exists and is enabled
        Optional<UserSummary> userOpt = userDirectory.findUser(userId);
        if (userOpt.isEmpty() || !userOpt.get().enabled()) {
            log.warn("Merchant user {} not found or disabled. Skipping notification for event {}.", userId, eventId);
            return;
        }

        // Idempotency pre-check
        if (recordRepository.existsByEventIdAndChannelAndRecipientUserId(eventId, NotificationChannel.IN_APP, userId)) {
            log.debug("Notification record already exists for event {} and recipient {}. Skipping duplicate.", eventId, userId);
            return;
        }

        int inserted = recordRepository.insertIfAbsent(
                UUID.randomUUID(),
                eventId,
                userId,
                null,
                null,
                content.title(),
                content.message(),
                NotificationChannel.IN_APP.name(),
                NotificationDeliveryStatus.NOT_APPLICABLE.name(),
                event.occurredAt());

        if (inserted > 0) {
            log.info("Created in-app notification for merchant user {} on status transition to {}", userId, toStatus);
        } else {
            log.debug("Notification record already existed for event {} and recipient {}. Skipped insert.", eventId, userId);
        }
    }

    private static MerchantNotificationContent resolveContent(MerchantStatus status) {
        return switch (status) {
            case APPROVED -> new MerchantNotificationContent(
                    "Merchant Application Approved",
                    "Congratulations! Your merchant account application has been approved.");
            case REJECTED -> new MerchantNotificationContent(
                    "Merchant Application Update",
                    "Your merchant account application was not approved. Please contact support for more information.");
            case SUSPENDED -> new MerchantNotificationContent(
                    "Merchant Account Suspended",
                    "Your merchant account has been suspended. Please contact support.");
            case PENDING -> null;
        };
    }

    private record MerchantNotificationContent(String title, String message) {}
}

