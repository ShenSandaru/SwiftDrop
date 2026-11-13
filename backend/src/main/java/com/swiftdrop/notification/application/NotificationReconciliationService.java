package com.swiftdrop.notification.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;

import com.swiftdrop.notification.domain.NotificationPendingEvent;
import com.swiftdrop.notification.domain.NotificationPendingEventRepository;
import com.swiftdrop.notification.domain.NotificationPendingEventStatus;
import com.swiftdrop.notification.infrastructure.NotificationReconciliationProperties;

/**
 * Scheduled service for reconciling out-of-order pending events in the Notification module (ADR 0004 §4.3).
 * <p>
 * Guarantees:
 * <ul>
 *   <li>Discovers {@code PENDING} events buffered for longer than the grace period (e.g. 15s).</li>
 *   <li>Processes each unique parcel in an isolated transaction with a non-blocking advisory lock
 *       ({@code tryAcquireParcelLock}), preventing lock contention with the listener or concurrent runs.</li>
 *   <li>Enforces max attempts (e.g. 5) before quarantining persistently missing events as {@code UNRESOLVED}.</li>
 * </ul>
 */
public class NotificationReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationReconciliationService.class);

    private final NotificationPendingEventRepository pendingEventRepository;
    private final NotificationEventProcessor processor;
    private final NotificationReconciliationProperties properties;
    private final Clock clock;

    public NotificationReconciliationService(
            NotificationPendingEventRepository pendingEventRepository,
            NotificationEventProcessor processor,
            NotificationReconciliationProperties properties,
            Clock clock) {
        this.pendingEventRepository = Objects.requireNonNull(pendingEventRepository, "pendingEventRepository");
        this.processor = Objects.requireNonNull(processor, "processor");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Executes one reconciliation cycle across pending events older than the grace period.
     *
     * @return number of parcels reconciled
     */
    public int reconcile() {
        if (!properties.enabled()) {
            return 0;
        }

        Instant now = clock.instant();
        Instant cutoff = now.minus(properties.gracePeriod());

        // Discover eligible pending events older than grace period
        List<NotificationPendingEvent> eligible = pendingEventRepository
                .findByStatusAndReceivedAtBeforeOrderByReceivedAtAsc(
                        NotificationPendingEventStatus.PENDING,
                        cutoff,
                        Limit.of(properties.batchSize()));

        if (eligible.isEmpty()) {
            return 0;
        }

        // Deduplicate parcel IDs while preserving insertion order
        Set<UUID> parcelIds = new LinkedHashSet<>();
        for (NotificationPendingEvent event : eligible) {
            parcelIds.add(event.getParcelId());
        }

        int processedCount = 0;
        for (UUID parcelId : parcelIds) {
            try {
                boolean executed = processor.reconcileParcel(parcelId, now, properties.maxAttempts());
                if (executed) {
                    processedCount++;
                }
            } catch (Exception e) {
                log.warn("Reconciliation failed for parcel {}: {}", parcelId, e.getMessage());
            }
        }

        if (processedCount > 0) {
            log.debug("Notification reconciliation cycle completed: {} parcels processed", processedCount);
        }

        return processedCount;
    }
}

