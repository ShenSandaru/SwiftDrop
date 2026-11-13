package com.swiftdrop.delivery.infrastructure.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.support.TransactionOperations;

import com.swiftdrop.delivery.domain.OutboxEvent;
import com.swiftdrop.delivery.domain.OutboxEventRepository;

/**
 * The outbox relay (ADR 0004 §3, §4.2): a scheduled poller that publishes unpublished outbox rows
 * to RabbitMQ and marks them published once the broker confirmed them.
 *
 * <ul>
 *   <li>Each poll runs in one transaction behind a transaction-scoped advisory lock, so only one
 *       relay publishes at a time; a second instance skips its poll.</li>
 *   <li>Rows are handled in outbox-ID order. A failed row, or one still waiting for its retry
 *       time, blocks only its own parcel's later rows for the rest of the poll; other parcels
 *       continue. This keeps each parcel's events in order without blocking everyone.</li>
 *   <li>A broker-wide failure (connection, confirm timeout) ends the poll at once.</li>
 *   <li>Rows are never dropped. If the commit fails after a confirm, the next poll publishes the
 *       same rows again with the same event IDs; consumers treat them as duplicates.</li>
 * </ul>
 */
public class OutboxRelay {

    /** Advisory lock key of the relay; an arbitrary constant only this relay uses. */
    static final long LOCK_KEY = 0x5D_0B0C_0001L;

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outbox;
    private final OutboxPublisher publisher;
    private final TransactionOperations transactions;
    private final OutboxRelayProperties properties;
    private final Clock clock;
    private final AtomicReference<Instant> lastCompletedPoll = new AtomicReference<>();

    OutboxRelay(OutboxEventRepository outbox, OutboxPublisher publisher, TransactionOperations transactions,
            OutboxRelayProperties properties, Clock clock) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.transactions = transactions;
        this.properties = properties;
        this.clock = clock;
    }

    /** One poll. Called by the scheduler; failures are logged and the next poll starts afresh. */
    public void poll() {
        try {
            PollResult result = transactions.execute(status -> pollInTransaction());
            if (result != null) {
                lastCompletedPoll.set(clock.instant());
                if (result.published() > 0 || result.failed() > 0) {
                    log.debug("Outbox poll: {} published, {} failed", result.published(), result.failed());
                }
            }
        } catch (RuntimeException e) {
            // For example the database is unavailable; nothing was marked, so nothing is lost
            log.warn("Outbox poll failed: {}", e.toString());
        }
    }

    /** When the last poll that held the lock completed; {@code null} before the first one. */
    public Instant lastCompletedPoll() {
        return lastCompletedPoll.get();
    }

    /** {@code null} when another relay holds the lock. */
    private PollResult pollInTransaction() {
        if (!outbox.tryRelayLock(LOCK_KEY)) {
            return null;
        }
        Instant now = clock.instant();
        Set<UUID> blockedParcels = new HashSet<>();
        int published = 0;
        int failed = 0;
        for (OutboxEvent row : outbox.findByPublishedAtIsNullOrderByIdAsc(Limit.of(properties.batchSize()))) {
            if (blockedParcels.contains(row.getParcelId())) {
                continue;
            }
            if (!row.isDue(now)) {
                // Still waiting for its retry time: its parcel's later rows must wait behind it
                blockedParcels.add(row.getParcelId());
                continue;
            }
            PublishOutcome outcome = publisher.publish(row);
            if (outcome == PublishOutcome.CONFIRMED) {
                row.markPublished(clock.instant());
                published++;
                continue;
            }
            int attempt = row.getAttempts() + 1;
            row.recordFailure(outcome.failureCategory(), clock.instant().plus(properties.backoff(attempt)));
            blockedParcels.add(row.getParcelId());
            failed++;
            if (outcome.endsPoll()) {
                // Broker-wide; the monitor alerts if it lasts. IDs only, never the payload.
                log.debug("Outbox poll stopped: {} at row {} (attempt {})", outcome.failureCategory(), row.getId(),
                        attempt);
                break;
            }
            if (outcome == PublishOutcome.NACKED) {
                log.warn("Outbox row {} of parcel {} refused by the broker (attempt {})", row.getId(),
                        row.getParcelId(), attempt);
            } else {
                // Unroutable is expected until a consumer has bound its queue; the monitor alerts if it lasts
                log.debug("Outbox row {} of parcel {} not published: {} (attempt {})", row.getId(),
                        row.getParcelId(), outcome.failureCategory(), attempt);
            }
        }
        return new PollResult(published, failed);
    }

    private record PollResult(int published, int failed) {
    }
}
