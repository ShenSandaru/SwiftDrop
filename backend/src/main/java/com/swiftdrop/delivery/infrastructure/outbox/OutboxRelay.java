package com.swiftdrop.delivery.infrastructure.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.support.TransactionOperations;

import com.swiftdrop.delivery.domain.OutboxEvent;
import com.swiftdrop.delivery.domain.OutboxEventRepository;

/**
 * Publishes the transactional outbox to RabbitMQ (SD-502, ADR 0004 §3 and §4.2).
 * <ul>
 * <li>One relay at a time: each poll is one transaction that first takes a PostgreSQL advisory lock;
 * if another instance holds it, the poll is skipped.</li>
 * <li>Rows are handled in outbox order. A row that fails, or waits for its retry time, blocks only its
 * own parcel for the rest of the poll; other parcels continue.</li>
 * <li>A broker failure or a confirm timeout ends the poll, since every later row would fail the same way.</li>
 * <li>Rows are marked published only after a positive confirm and never dropped; marks commit with the
 * poll, so a crash before the commit publishes those rows again with the same event IDs.</li>
 * </ul>
 * Logs carry outbox, parcel and event IDs, never payloads.
 */
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    /** The advisory lock key of the outbox relay; any fixed number unique within the database. */
    static final long LOCK_KEY = 5_020_250_301L;

    static final Duration BASE_BACKOFF = Duration.ofSeconds(1);
    private static final Duration ALERT_INTERVAL = Duration.ofMinutes(1);

    /** Result of one poll, for tests and logging. */
    record PollResult(boolean lockAcquired, int published, int failed, boolean stopped) {
        static final PollResult SKIPPED = new PollResult(false, 0, 0, false);
    }

    private final OutboxEventRepository outbox;
    private final OutboxPublisher publisher;
    private final TransactionOperations transactions;
    private final OutboxRelayMetrics metrics;
    private final OutboxRelayProperties properties;
    private final Clock clock;
    private final Instant startedAt;

    private volatile Instant lastBrokerWarning;
    private volatile Instant lastBacklogAlert;
    private volatile Instant lastStallAlert;

    OutboxRelay(OutboxEventRepository outbox, OutboxPublisher publisher, TransactionOperations transactions,
            OutboxRelayMetrics metrics, OutboxRelayProperties properties, Clock clock) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.transactions = transactions;
        this.metrics = metrics;
        this.properties = properties;
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    /** Scheduled entry point: never throws, so the schedule keeps running. */
    void poll() {
        try {
            PollResult result = pollOnce();
            if (result.published() > 0 || result.failed() > 0) {
                log.debug("Outbox poll: published={}, failed={}, stopped={}",
                        result.published(), result.failed(), result.stopped());
            }
        } catch (RuntimeException e) {
            // Database unavailable or the transaction failed: rows stay unpublished for the next poll.
            log.warn("Outbox relay poll failed: {}", e.getClass().getSimpleName());
        }
    }

    PollResult pollOnce() {
        return transactions.execute(status -> {
            if (!outbox.tryAdvisoryTransactionLock(LOCK_KEY)) {
                return PollResult.SKIPPED;
            }
            PollResult result = publishDueRows();
            refreshBacklog();
            metrics.pollCompleted(clock.instant());
            return result;
        });
    }

    private PollResult publishDueRows() {
        List<OutboxEvent> rows = outbox.findByPublishedAtIsNullOrderByIdAsc(Limit.of(properties.batchSize()));
        Set<UUID> blockedParcels = new HashSet<>();
        int published = 0;
        int failed = 0;
        for (OutboxEvent row : rows) {
            if (blockedParcels.contains(row.getParcelId())) {
                continue;
            }
            Instant now = clock.instant();
            if (row.getNextAttemptAt().isAfter(now)) {
                blockedParcels.add(row.getParcelId());
                continue;
            }
            OutboxPublisher.Result result;
            try {
                result = publisher.publish(row);
            } catch (AmqpException e) {
                fail(row, "BROKER_UNAVAILABLE", now);
                warnBrokerUnavailable(e, now);
                return new PollResult(true, published, failed + 1, true);
            }
            switch (result) {
                case CONFIRMED -> {
                    row.markPublished(clock.instant());
                    published++;
                }
                case NACKED, RETURNED -> {
                    fail(row, result.name(), now);
                    blockedParcels.add(row.getParcelId());
                    failed++;
                }
                case CONFIRM_TIMEOUT -> {
                    fail(row, result.name(), now);
                    return new PollResult(true, published, failed + 1, true);
                }
            }
        }
        return new PollResult(true, published, failed, false);
    }

    private void fail(OutboxEvent row, String category, Instant now) {
        row.recordFailure(category, now, backoff(row.getAttempts() + 1));
        if (row.getAttempts() >= properties.attemptAlertThreshold()) {
            log.error("Outbox row keeps failing: outboxId={}, parcelId={}, eventId={}, attempts={}, category={}",
                    row.getId(), row.getParcelId(), row.getEventId(), row.getAttempts(), category);
        }
    }

    /** 1 s, 2 s, 4 s ... up to the configured maximum (ADR 0004 §9). */
    Duration backoff(int attempt) {
        int doublings = Math.min(Math.max(attempt - 1, 0), 30);
        Duration delay = BASE_BACKOFF.multipliedBy(1L << doublings);
        return delay.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : delay;
    }

    private void refreshBacklog() {
        Instant now = clock.instant();
        long unpublished = outbox.countByPublishedAtIsNull();
        var oldest = outbox.findFirstByPublishedAtIsNullOrderByIdAsc();
        long oldestAge = oldest.map(row -> Duration.between(row.getCreatedAt(), now).toSeconds()).orElse(0L);
        long aboveThreshold = outbox.countByPublishedAtIsNullAndAttemptsGreaterThanEqual(
                properties.attemptAlertThreshold());
        metrics.backlog(unpublished, oldestAge, aboveThreshold);

        if (oldest.isPresent() && oldestAge >= properties.oldestAlertAge().toSeconds() && due(lastBacklogAlert, now)) {
            lastBacklogAlert = now;
            log.error("Outbox backlog: oldest unpublished row is {} s old (outboxId={}, parcelId={}), {} unpublished",
                    oldestAge, oldest.get().getId(), oldest.get().getParcelId(), unpublished);
        }
    }

    /** Scheduled watchdog: rows wait but no poll has completed for too long (ADR 0004 §9). */
    void checkStalled() {
        Instant now = clock.instant();
        Instant last = metrics.lastCompletedPoll() != null ? metrics.lastCompletedPoll() : startedAt;
        if (metrics.unpublished() > 0 && Duration.between(last, now).compareTo(properties.stalledPollAlert()) > 0
                && due(lastStallAlert, now)) {
            lastStallAlert = now;
            log.error("Outbox relay stalled: no completed poll since {} while {} rows are unpublished",
                    last, metrics.unpublished());
        }
    }

    private void warnBrokerUnavailable(AmqpException e, Instant now) {
        if (due(lastBrokerWarning, now)) {
            lastBrokerWarning = now;
            log.warn("Outbox relay cannot publish, broker unavailable: {}", e.getClass().getSimpleName());
        }
    }

    private static boolean due(Instant lastAlert, Instant now) {
        return lastAlert == null || Duration.between(lastAlert, now).compareTo(ALERT_INTERVAL) >= 0;
    }
}
