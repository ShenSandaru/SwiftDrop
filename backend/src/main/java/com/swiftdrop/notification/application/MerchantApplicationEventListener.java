package com.swiftdrop.notification.application;

import java.util.List;
import java.util.Objects;
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
import com.swiftdrop.merchant.api.MerchantApplicationSubmitted;
import com.swiftdrop.notification.domain.NotificationChannel;
import com.swiftdrop.notification.domain.NotificationDeliveryStatus;
import com.swiftdrop.notification.domain.NotificationRecordRepository;

/**
 * Listens for {@link MerchantApplicationSubmitted} in-process application events published by the
 * merchant module after registration transaction commit (ADR 0003, ADR 0004 §3).
 * Resolves all enabled administrators via {@link UserDirectory#findEnabledAdmins()} and creates
 * an idempotent account-level in-app notification record for each administrator.
 *
 * <p>Known limitation: Because this uses in-process Spring application events rather than
 * a transactional outbox queue, an unexpected application termination between the registration
 * commit and this listener execution could lose the admin notification.
 */
@Component
public class MerchantApplicationEventListener {

    private static final Logger log = LoggerFactory.getLogger(MerchantApplicationEventListener.class);

    private final NotificationRecordRepository recordRepository;
    private final UserDirectory userDirectory;

    public MerchantApplicationEventListener(NotificationRecordRepository recordRepository, UserDirectory userDirectory) {
        this.recordRepository = Objects.requireNonNull(recordRepository, "recordRepository must not be null");
        this.userDirectory = Objects.requireNonNull(userDirectory, "userDirectory must not be null");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMerchantApplicationSubmitted(MerchantApplicationSubmitted event) {
        Objects.requireNonNull(event, "event must not be null");

        List<UserSummary> admins = userDirectory.findEnabledAdmins();
        if (admins.isEmpty()) {
            log.debug("No enabled administrators found for merchant application event {}. Skipping.", event.eventId());
            return;
        }

        String title = "New Merchant Application";
        String message = "A new merchant application has been submitted by " + event.businessName() + ".";

        int createdCount = 0;
        for (UserSummary admin : admins) {
            int inserted = recordRepository.insertIfAbsent(
                    UUID.randomUUID(),
                    event.eventId(),
                    admin.id(),
                    null,
                    null,
                    title,
                    message,
                    NotificationChannel.IN_APP.name(),
                    NotificationDeliveryStatus.NOT_APPLICABLE.name(),
                    event.submittedAt());
            if (inserted > 0) {
                createdCount++;
            }
        }

        log.info("Processed merchant application {} for {} enabled admin(s) (inserted={})",
                event.merchantId(), admins.size(), createdCount);
    }
}

