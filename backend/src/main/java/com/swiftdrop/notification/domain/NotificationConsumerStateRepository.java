package com.swiftdrop.notification.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for managing {@link NotificationConsumerState} per-parcel sequence watermarks.
 */
public interface NotificationConsumerStateRepository extends JpaRepository<NotificationConsumerState, UUID> {
}

