package com.swiftdrop.notification.application;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.notification.domain.NotificationChannel;
import com.swiftdrop.notification.domain.NotificationRecord;
import com.swiftdrop.notification.domain.NotificationRecordRepository;
import com.swiftdrop.shared.web.PageLimit;

/**
 * Application service for querying and updating user notifications.
 * Restricts all operations strictly to IN_APP notifications belonging to the authenticated caller.
 */
@Service
public class NotificationQueryService {

    private final NotificationRecordRepository recordRepository;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationQueryService(NotificationRecordRepository recordRepository) {
        this(recordRepository, Clock.systemUTC());
    }

    public NotificationQueryService(NotificationRecordRepository recordRepository, Clock clock) {
        this.recordRepository = Objects.requireNonNull(recordRepository, "recordRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Retrieves a paginated feed of in-app notifications for the recipient.
     * Orders newest first by createdAt DESC, with a stable secondary sort of id DESC.
     *
     * @param recipientUserId the authenticated caller UUID
     * @param unreadOnly whether to filter only unread notifications
     * @param page requested page number (clamped)
     * @param size requested page size (clamped)
     * @return page of notification records
     */
    @Transactional(readOnly = true)
    public Page<NotificationRecord> getFeed(UUID recipientUserId, boolean unreadOnly, int page, int size) {
        int pageNumber = PageLimit.clampPage(page);
        int pageSize = PageLimit.clampSize(size);
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, sort);

        if (unreadOnly) {
            return recordRepository.findByRecipientUserIdAndChannelAndIsReadFalse(
                    recipientUserId, NotificationChannel.IN_APP, pageRequest);
        } else {
            return recordRepository.findByRecipientUserIdAndChannel(
                    recipientUserId, NotificationChannel.IN_APP, pageRequest);
        }
    }

    /**
     * Returns the count of unread in-app notifications for the recipient.
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID recipientUserId) {
        return recordRepository.countByRecipientUserIdAndChannelAndIsReadFalse(
                recipientUserId, NotificationChannel.IN_APP);
    }

    /**
     * Marks a single in-app notification as read.
     * Idempotent: repeated calls on an already-read notification succeed and preserve readAt.
     * Returns 404 if the notification does not exist or does not belong to the recipient.
     */
    @Transactional
    public NotificationRecord markAsRead(UUID id, UUID recipientUserId) {
        NotificationRecord record = recordRepository.findByIdAndRecipientUserIdAndChannel(
                id, recipientUserId, NotificationChannel.IN_APP)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        if (!record.isRead()) {
            record.markAsRead(clock.instant());
            record = recordRepository.save(record);
        }
        return record;
    }

    /**
     * Marks all unread in-app notifications as read for the recipient.
     * Only updates records where is_read = false, preserving existing readAt timestamps.
     *
     * @return the number of updated notifications
     */
    @Transactional
    public int markAllAsRead(UUID recipientUserId) {
        return recordRepository.markAllAsReadForRecipient(recipientUserId, clock.instant());
    }
}
