package com.swiftdrop.notification.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for managing persisted {@link NotificationRecord} rows.
 */
public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, UUID> {

    /**
     * Checks if a notification record already exists for the given event, channel, and recipient.
     * Enforces idempotency matching the database constraint {@code uq_notification_records_event_channel_user}.
     */
    boolean existsByEventIdAndChannelAndRecipientUserId(UUID eventId, NotificationChannel channel, UUID recipientUserId);

    /**
     * Finds a single notification record for an event, channel, and recipient.
     */
    Optional<NotificationRecord> findByEventIdAndChannelAndRecipientUserId(
            UUID eventId,
            NotificationChannel channel,
            UUID recipientUserId);

    /**
     * Queries notification feed for a user ordered by created_at DESC.
     * Backed by index {@code ix_notification_records_user_feed}.
     */
    Page<NotificationRecord> findByRecipientUserId(UUID recipientUserId, Pageable pageable);

    /**
     * Queries unread in-app notifications for a user.
     * Backed by partial index {@code ix_notification_records_user_unread}.
     */
    Page<NotificationRecord> findByRecipientUserIdAndChannelAndIsReadFalse(
            UUID recipientUserId,
            NotificationChannel channel,
            Pageable pageable);

    /**
     * Counts unread in-app notifications for badges.
     * Backed by partial index {@code ix_notification_records_user_unread}.
     */
    long countByRecipientUserIdAndChannelAndIsReadFalse(UUID recipientUserId, NotificationChannel channel);

    /**
     * Queries notifications related to a specific parcel for audit and support.
     * Backed by index {@code ix_notification_records_parcel_id}.
     */
    Page<NotificationRecord> findByParcelId(UUID parcelId, Pageable pageable);
}

