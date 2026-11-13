package com.swiftdrop.notification.domain;

/**
 * Lifecycle status for out-of-order pending events awaiting sequence reconciliation.
 * Corresponds to database check constraint {@code ck_notification_pending_events_status}.
 */
public enum NotificationPendingEventStatus {
    PENDING,
    UNRESOLVED
}

