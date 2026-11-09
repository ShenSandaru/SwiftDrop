package com.swiftdrop.delivery.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.domain.OutboxEvent;
import com.swiftdrop.delivery.domain.OutboxEventRepository;

/** Metrics and alert log lines of the relay (ADR 0004 §4.2, §9, §11). */
class OutboxMonitorTest {

    private static final Instant START = Instant.parse("2026-11-09T10:00:00Z");

    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    private final OutboxRelay relay = mock(OutboxRelay.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final MutableClock clock = new MutableClock(START);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(OutboxMonitor.class);
    private OutboxMonitor monitor;

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
        monitor = new OutboxMonitor(outbox, relay, OutboxRelayProperties.defaults(), meters, clock);
        backlog();
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private OutboxEvent row(Duration age) {
        return new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), 1, ParcelStatusChanged.EVENT_TYPE, "{}",
                clock.instant().minus(age));
    }

    /** The unpublished rows the repository reports; the first is the oldest. */
    private void backlog(OutboxEvent... rows) {
        when(outbox.countByPublishedAtIsNull()).thenReturn((long) rows.length);
        when(outbox.findFirstByPublishedAtIsNullOrderByIdAsc())
                .thenReturn(rows.length == 0 ? Optional.empty() : Optional.of(rows[0]));
        List<OutboxEvent> failing = List.of(rows).stream().filter(row -> row.getAttempts() >= 10).toList();
        when(outbox.countByPublishedAtIsNullAndAttemptsGreaterThanEqual(anyInt())).thenReturn((long) failing.size());
        when(outbox.findByPublishedAtIsNullAndAttemptsGreaterThanEqualOrderByIdAsc(anyInt(), any(Limit.class)))
                .thenReturn(failing);
    }

    private List<String> lines(Level level) {
        return logs.list.stream().filter(event -> event.getLevel() == level).map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private double gauge(String name) {
        return meters.get(name).gauge().value();
    }

    @Test
    void metricsShowTheBacklog() {
        OutboxEvent oldest = row(Duration.ofSeconds(90));
        backlog(oldest, row(Duration.ofSeconds(10)));
        when(relay.lastCompletedPoll()).thenReturn(START.minusSeconds(3));

        monitor.check();

        assertThat(gauge("swiftdrop.outbox.unpublished")).isEqualTo(2);
        assertThat(gauge("swiftdrop.outbox.oldest.unpublished.age")).isEqualTo(90);
        assertThat(gauge("swiftdrop.outbox.attempt.threshold.rows")).isZero();
        assertThat(gauge("swiftdrop.outbox.last.poll.age")).isEqualTo(3);
        assertThat(lines(Level.ERROR)).isEmpty();
    }

    @Test
    void lastPollAgeIsUnknownBeforeTheFirstPoll() {
        assertThat(gauge("swiftdrop.outbox.last.poll.age")).isNaN();
    }

    @Test
    void oldRowAlertIsLoggedOnceAndClearedOnce() {
        when(relay.lastCompletedPoll()).thenReturn(START);
        OutboxEvent stuck = row(Duration.ofMinutes(3));
        backlog(stuck);

        monitor.check();
        monitor.check();

        assertThat(lines(Level.ERROR)).singleElement().asString()
                .contains("oldest unpublished row", stuck.getParcelId().toString(), "180s");

        backlog();
        monitor.check();

        assertThat(lines(Level.INFO)).singleElement().asString().contains("cleared");
    }

    @Test
    void rowsWithManyAttemptsAreListedByIdOnly() {
        when(relay.lastCompletedPoll()).thenReturn(START);
        OutboxEvent failing = row(Duration.ofSeconds(30));
        for (int i = 0; i < 10; i++) {
            failing.recordFailure("returned", START);
        }
        backlog(failing);

        monitor.check();

        assertThat(gauge("swiftdrop.outbox.attempt.threshold.rows")).isEqualTo(1);
        assertThat(lines(Level.ERROR)).singleElement().asString()
                .contains("10 or more failed attempts", failing.getParcelId().toString())
                .doesNotContain("{}");
    }

    @Test
    void stalledRelayIsAlertedOnlyWhileRowsWait() {
        clock.advance(Duration.ofMinutes(2));
        // A fresh row, so only the stalled relay is alerted, not an old row
        backlog(row(Duration.ofSeconds(5)));

        monitor.check();

        assertThat(lines(Level.ERROR)).singleElement().asString().contains("no relay poll completed");

        when(relay.lastCompletedPoll()).thenReturn(clock.instant());
        monitor.check();

        assertThat(lines(Level.INFO)).singleElement().asString().contains("polling again");
    }

    @Test
    void idleRelayWithNothingToPublishIsNotAnAlert() {
        clock.advance(Duration.ofMinutes(10));

        monitor.check();

        assertThat(lines(Level.ERROR)).isEmpty();
    }

    @Test
    void repositoryFailureIsLoggedNotThrown() {
        when(outbox.countByPublishedAtIsNull()).thenThrow(new IllegalStateException("database unavailable"));

        monitor.check();

        assertThat(lines(Level.WARN)).singleElement().asString().contains("monitor check failed");
    }

    /** A clock the test moves forward. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
