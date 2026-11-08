package com.swiftdrop.delivery.infrastructure.outbox;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Relay metrics through Micrometer (ADR 0004 §4.2, §10), refreshed after every poll so a metrics scrape
 * never queries the database.
 */
class OutboxRelayMetrics {

    private final AtomicLong unpublished = new AtomicLong();
    private final AtomicLong oldestUnpublishedAgeSeconds = new AtomicLong();
    private final AtomicLong rowsAboveAttemptThreshold = new AtomicLong();
    private final AtomicLong lastCompletedPollEpochSeconds = new AtomicLong();
    private final AtomicReference<Instant> lastCompletedPoll = new AtomicReference<>();

    OutboxRelayMetrics(MeterRegistry registry) {
        Gauge.builder("swiftdrop.outbox.unpublished", unpublished, AtomicLong::get)
                .description("Outbox rows not yet published").register(registry);
        Gauge.builder("swiftdrop.outbox.oldest.unpublished.age", oldestUnpublishedAgeSeconds, AtomicLong::get)
                .baseUnit("seconds").description("Age of the oldest unpublished outbox row").register(registry);
        Gauge.builder("swiftdrop.outbox.attempts.exceeded", rowsAboveAttemptThreshold, AtomicLong::get)
                .description("Unpublished rows at or above the attempt alert threshold").register(registry);
        Gauge.builder("swiftdrop.outbox.last.poll", lastCompletedPollEpochSeconds, AtomicLong::get)
                .baseUnit("seconds").description("Epoch second of the last completed relay poll").register(registry);
    }

    void backlog(long unpublishedRows, long oldestAgeSeconds, long aboveThreshold) {
        unpublished.set(unpublishedRows);
        oldestUnpublishedAgeSeconds.set(oldestAgeSeconds);
        rowsAboveAttemptThreshold.set(aboveThreshold);
    }

    void pollCompleted(Instant at) {
        lastCompletedPoll.set(at);
        lastCompletedPollEpochSeconds.set(at.getEpochSecond());
    }

    long unpublished() {
        return unpublished.get();
    }

    Instant lastCompletedPoll() {
        return lastCompletedPoll.get();
    }
}
