package com.swiftdrop.notification.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for managing {@link NotificationPendingEvent} rows buffered during out-of-order delivery.
 */
public interface NotificationPendingEventRepository extends JpaRepository<NotificationPendingEvent, UUID> {

    /**
     * Finds a pending event for a parcel at a specific sequence number.
     * Backed by constraint {@code uq_notification_pending_events_parcel_sequence}.
     */
    Optional<NotificationPendingEvent> findByParcelIdAndSequence(UUID parcelId, int sequence);

    /**
     * Finds all pending events for a parcel ordered by sequence ascending.
     */
    List<NotificationPendingEvent> findByParcelIdOrderBySequenceAsc(UUID parcelId);

    /**
     * Discovers pending events awaiting reconciliation received prior to the cutoff threshold.
     * Backed by partial index {@code ix_notification_pending_events_reconciliation}.
     */
    List<NotificationPendingEvent> findByStatusAndReceivedAtBeforeOrderByReceivedAtAsc(
            NotificationPendingEventStatus status,
            Instant receivedBeforeCutoff,
            Limit limit);

    /**
     * Counts currently pending events.
     */
    long countByStatus(NotificationPendingEventStatus status);
}

