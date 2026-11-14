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

import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelCourierAssigned;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;
import com.swiftdrop.notification.domain.NotificationChannel;
import com.swiftdrop.notification.domain.NotificationDeliveryStatus;
import com.swiftdrop.notification.domain.NotificationRecordRepository;

/**
 * Listens for {@link ParcelCourierAssigned} in-process application events published by the
 * delivery module after transaction commit (ADR 0003, ADR 0004 §3).
 * Resolves the recipient courier user, validates the account is enabled, and creates
 * an idempotent in-app notification record.
 *
 * <p>Known limitation: Because this uses in-process Spring application events rather than
 * a transactional outbox queue, an unexpected application termination between the delivery
 * commit and this listener execution could lose the notification.
 */
@Component
public class CourierAssignmentEventListener {

    private static final Logger log = LoggerFactory.getLogger(CourierAssignmentEventListener.class);

    private final NotificationRecordRepository recordRepository;
    private final UserDirectory userDirectory;

    public CourierAssignmentEventListener(NotificationRecordRepository recordRepository, UserDirectory userDirectory) {
        this.recordRepository = Objects.requireNonNull(recordRepository, "recordRepository must not be null");
        this.userDirectory = Objects.requireNonNull(userDirectory, "userDirectory must not be null");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onParcelCourierAssigned(ParcelCourierAssigned event) {
        Objects.requireNonNull(event, "event must not be null");

        UUID eventId = event.eventId();
        UUID courierId = event.courierId();
        UUID parcelId = event.parcelId();
        String trackingNumber = event.trackingNumber();
        ParcelAssignmentType assignmentType = event.assignmentType();

        CourierAssignmentNotificationContent content = resolveContent(assignmentType, trackingNumber);

        // Validate recipient exists and is enabled
        Optional<UserSummary> userOpt = userDirectory.findUser(courierId);
        if (userOpt.isEmpty() || !userOpt.get().enabled()) {
            log.warn("Courier user {} not found or disabled. Skipping notification for event {}.", courierId, eventId);
            return;
        }

        // Idempotency pre-check
        if (recordRepository.existsByEventIdAndChannelAndRecipientUserId(eventId, NotificationChannel.IN_APP, courierId)) {
            log.debug("Notification record already exists for event {} and recipient {}. Skipping duplicate.", eventId, courierId);
            return;
        }

        int inserted = recordRepository.insertIfAbsent(
                UUID.randomUUID(),
                eventId,
                courierId,
                parcelId,
                trackingNumber,
                content.title(),
                content.message(),
                NotificationChannel.IN_APP.name(),
                NotificationDeliveryStatus.NOT_APPLICABLE.name(),
                event.occurredAt());

        if (inserted > 0) {
            log.info("Created in-app notification for courier user {} on {} assignment for parcel {}",
                    courierId, assignmentType, parcelId);
        } else {
            log.debug("Notification record already existed for event {} and recipient {}. Skipped insert.", eventId, courierId);
        }
    }

    private static CourierAssignmentNotificationContent resolveContent(ParcelAssignmentType type, String trackingNumber) {
        return switch (type) {
            case PICKUP -> new CourierAssignmentNotificationContent(
                    "New Pickup Task",
                    "You have been assigned to pick up parcel " + trackingNumber + ".");
            case DELIVERY -> new CourierAssignmentNotificationContent(
                    "New Delivery Task",
                    "You have been assigned to deliver parcel " + trackingNumber + ".");
            case RETURN -> new CourierAssignmentNotificationContent(
                    "New Return Task",
                    "You have been assigned to return parcel " + trackingNumber + " to sender.");
        };
    }

    private record CourierAssignmentNotificationContent(String title, String message) {}
}

