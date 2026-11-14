package com.swiftdrop.notification.domain;

/**
 * Transport channels supported for delivery of notifications.
 * Corresponds to database check constraint {@code ck_notification_records_channel}.
 */
public enum NotificationChannel {
    IN_APP,
    EMAIL,
    SMS,
    PUSH
}

