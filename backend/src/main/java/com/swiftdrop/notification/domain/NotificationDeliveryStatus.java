package com.swiftdrop.notification.domain;

/**
 * Delivery status for notification dispatches.
 * Corresponds to database check constraint {@code ck_notification_records_delivery_status}.
 */
public enum NotificationDeliveryStatus {
    PENDING,
    SENT,
    FAILED,
    NOT_APPLICABLE
}

