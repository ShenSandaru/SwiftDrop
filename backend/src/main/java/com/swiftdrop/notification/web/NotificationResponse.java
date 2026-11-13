package com.swiftdrop.notification.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.notification.domain.NotificationChannel;
import com.swiftdrop.notification.domain.NotificationRecord;

/**
 * Public response DTO for a notification item in REST endpoints.
 */
public record NotificationResponse(
        UUID id,
        UUID parcelId,
        String trackingNumber,
        String title,
        String message,
        NotificationChannel channel,
        boolean isRead,
        Instant readAt,
        Instant createdAt) {

    public static NotificationResponse from(NotificationRecord record) {
        return new NotificationResponse(
                record.getId(),
                record.getParcelId(),
                record.getTrackingNumber(),
                record.getTitle(),
                record.getMessage(),
                record.getChannel(),
                record.isRead(),
                record.getReadAt(),
                record.getCreatedAt());
    }
}

