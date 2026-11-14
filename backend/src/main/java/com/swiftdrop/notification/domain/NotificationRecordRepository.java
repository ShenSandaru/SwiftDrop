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
     * Finds a single notification record by its ID, recipient, and channel.
     * Guarantees that callers can only access their own in-app notifications.
     */
    Optional<NotificationRecord> findByIdAndRecipientUserIdAndChannel(
            UUID id,
            UUID recipientUserId,
            NotificationChannel channel);

    /**
     * Queries notification feed for a user filtered by channel (e.g. IN_APP).
     * Backed by index {@code ix_notification_records_user_feed}.
     */
    Page<NotificationRecord> findByRecipientUserIdAndChannel(
            UUID recipientUserId,
            NotificationChannel channel,
            Pageable pageable);

    /**
     * Bulk marks all unread in-app notifications as read for a recipient.
     * Preserves existing read_at timestamps by updating only records where is_read is false.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE NotificationRecord r SET r.isRead = true, r.readAt = :readAt "
            + "WHERE r.recipientUserId = :recipientUserId AND r.isRead = false AND r.channel = 'IN_APP'")
    int markAllAsReadForRecipient(
            @org.springframework.data.repository.query.Param("recipientUserId") UUID recipientUserId,
            @org.springframework.data.repository.query.Param("readAt") java.time.Instant readAt);

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

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "UPDATE NotificationRecord r SET r.deliveryStatus = :status WHERE r.id = :id")
    int updateDeliveryStatus(
            @org.springframework.data.repository.query.Param("id") UUID id,
            @org.springframework.data.repository.query.Param("status") NotificationDeliveryStatus status);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT * FROM notification_records
            WHERE delivery_status = 'FAILED'
              AND channel IN ('EMAIL', 'SMS')
              AND retry_count < :maxAttempts
              AND (next_retry_at IS NULL OR next_retry_at <= :now)
            ORDER BY next_retry_at ASC NULLS FIRST
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    java.util.List<NotificationRecord> findRetryEligibleRecords(
            @org.springframework.data.repository.query.Param("now") java.time.Instant now,
            @org.springframework.data.repository.query.Param("maxAttempts") int maxAttempts,
            @org.springframework.data.repository.query.Param("limit") int limit);

    /**
     * Atomically inserts an in-app notification record if one does not already exist for the event and recipient.
     * Uses PostgreSQL's ON CONFLICT DO NOTHING against constraint uq_notification_records_event_channel_user.
     * Returns 1 if inserted, 0 if a conflicting record already existed.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            INSERT INTO notification_records (
                id, event_id, recipient_user_id, parcel_id, tracking_number,
                title, message, channel, is_read, read_at,
                delivery_status, sent_at, failure_reason, created_at
            ) VALUES (
                :id, :eventId, :recipientUserId, :parcelId, :trackingNumber,
                :title, :message, :channel, false, null,
                :deliveryStatus, null, null, :createdAt
            ) ON CONFLICT (event_id, channel, recipient_user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @org.springframework.data.repository.query.Param("id") UUID id,
            @org.springframework.data.repository.query.Param("eventId") UUID eventId,
            @org.springframework.data.repository.query.Param("recipientUserId") UUID recipientUserId,
            @org.springframework.data.repository.query.Param("parcelId") UUID parcelId,
            @org.springframework.data.repository.query.Param("trackingNumber") String trackingNumber,
            @org.springframework.data.repository.query.Param("title") String title,
            @org.springframework.data.repository.query.Param("message") String message,
            @org.springframework.data.repository.query.Param("channel") String channel,
            @org.springframework.data.repository.query.Param("deliveryStatus") String deliveryStatus,
            @org.springframework.data.repository.query.Param("createdAt") java.time.Instant createdAt);

    /**
     * Queries notifications related to a specific parcel for audit and support.
     * Backed by index {@code ix_notification_records_parcel_id}.
     */
    Page<NotificationRecord> findByParcelId(UUID parcelId, Pageable pageable);
}

