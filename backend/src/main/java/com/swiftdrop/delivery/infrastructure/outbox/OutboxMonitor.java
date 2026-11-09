package com.swiftdrop.delivery.infrastructure.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import com.swiftdrop.delivery.domain.OutboxEvent;
import com.swiftdrop.delivery.domain.OutboxEventRepository;

/**
 * Outbox health (ADR 0004 §4.2, §9): metrics for the unpublished backlog, and an {@code ERROR} log
 * line when a threshold is crossed. Each alert is logged once when it starts and once when it
 * clears, so a lasting problem does not flood the log. Log lines carry IDs only, never payloads.
 */
public class OutboxMonitor {

    /** At most this many row IDs are listed in one alert line. */
    static final int MAX_IDS_IN_ALERT = 10;

    private static final Logger log = LoggerFactory.getLogger(OutboxMonitor.class);

    private final OutboxEventRepository outbox;
    private final OutboxRelay relay;
    private final OutboxRelayProperties properties;
    private final Clock clock;
    private final Instant startedAt;

    private final AtomicLong unpublished = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();
    private final AtomicLong rowsOverAttemptThreshold = new AtomicLong();

    private boolean oldestAlert;
    private boolean attemptsAlert;
    private boolean stalledAlert;

    OutboxMonitor(OutboxEventRepository outbox, OutboxRelay relay, OutboxRelayProperties properties,
            MeterRegistry meters, Clock clock) {
        this.outbox = outbox;
        this.relay = relay;
        this.properties = properties;
        this.clock = clock;
        this.startedAt = clock.instant();
        Gauge.builder("swiftdrop.outbox.unpublished", unpublished, AtomicLong::get)
                .description("Outbox rows not yet published to RabbitMQ").register(meters);
        Gauge.builder("swiftdrop.outbox.oldest.unpublished.age", oldestAgeSeconds, AtomicLong::get)
                .description("Age of the oldest unpublished outbox row").baseUnit("seconds").register(meters);
        Gauge.builder("swiftdrop.outbox.attempt.threshold.rows", rowsOverAttemptThreshold, AtomicLong::get)
                .description("Unpublished rows at or above the attempt alert threshold").register(meters);
        Gauge.builder("swiftdrop.outbox.last.poll.age", this, OutboxMonitor::secondsSinceLastPoll)
                .description("Time since the relay last completed a poll").baseUnit("seconds").register(meters);
    }

    /** Refreshes the metrics and checks the alert thresholds. Called by the scheduler. */
    public void check() {
        try {
            Instant now = clock.instant();
            long backlog = outbox.countByPublishedAtIsNull();
            Optional<OutboxEvent> oldest = outbox.findFirstByPublishedAtIsNullOrderByIdAsc();
            long overThreshold = outbox.countByPublishedAtIsNullAndAttemptsGreaterThanEqual(properties.alertAttempts());
            Duration oldestAge = oldest.map(row -> Duration.between(row.getCreatedAt(), now)).orElse(Duration.ZERO);

            unpublished.set(backlog);
            oldestAgeSeconds.set(oldestAge.toSeconds());
            rowsOverAttemptThreshold.set(overThreshold);

            checkOldest(oldest, oldestAge);
            checkAttempts(overThreshold);
            checkStalled(backlog, now);
        } catch (RuntimeException e) {
            log.warn("Outbox monitor check failed: {}", e.toString());
        }
    }

    private void checkOldest(Optional<OutboxEvent> oldest, Duration age) {
        boolean alert = oldest.isPresent() && age.compareTo(properties.alertOldestAge()) > 0;
        if (alert && !oldestAlert) {
            OutboxEvent row = oldest.get();
            log.error("Outbox alert: oldest unpublished row {} of parcel {} is {}s old (threshold {}s, last failure: {})",
                    row.getId(), row.getParcelId(), age.toSeconds(), properties.alertOldestAge().toSeconds(),
                    row.getLastFailureCategory());
        } else if (!alert && oldestAlert) {
            log.info("Outbox alert cleared: no unpublished row is older than {}s", properties.alertOldestAge().toSeconds());
        }
        oldestAlert = alert;
    }

    private void checkAttempts(long overThreshold) {
        boolean alert = overThreshold > 0;
        if (alert && !attemptsAlert) {
            List<String> rows = outbox.findByPublishedAtIsNullAndAttemptsGreaterThanEqualOrderByIdAsc(
                            properties.alertAttempts(), Limit.of(MAX_IDS_IN_ALERT)).stream()
                    .map(row -> row.getId() + " (parcel " + row.getParcelId() + ")")
                    .toList();
            log.error("Outbox alert: {} unpublished rows have {} or more failed attempts: {}", overThreshold,
                    properties.alertAttempts(), rows);
        } else if (!alert && attemptsAlert) {
            log.info("Outbox alert cleared: no row has {} or more failed attempts", properties.alertAttempts());
        }
        attemptsAlert = alert;
    }

    private void checkStalled(long backlog, Instant now) {
        Instant last = relay.lastCompletedPoll();
        Instant since = last != null ? last : startedAt;
        boolean alert = backlog > 0 && Duration.between(since, now).compareTo(properties.alertPollStale()) > 0;
        if (alert && !stalledAlert) {
            log.error("Outbox alert: no relay poll completed for {}s while {} rows are unpublished",
                    Duration.between(since, now).toSeconds(), backlog);
        } else if (!alert && stalledAlert) {
            log.info("Outbox alert cleared: the relay is polling again");
        }
        stalledAlert = alert;
    }

    private double secondsSinceLastPoll() {
        Instant last = relay.lastCompletedPoll();
        return last == null ? Double.NaN : Duration.between(last, clock.instant()).toMillis() / 1000.0;
    }
}
