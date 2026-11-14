package com.swiftdrop.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class ClientRateLimiterTest {

    /** A clock the test moves by hand. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-11-13T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void allowsUpToTheLimitThenRefusesWithinTheMinute() {
        ClientRateLimiter limiter = new ClientRateLimiter(3, new TestClock());

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
    }

    @Test
    void countsEachClientSeparately() {
        ClientRateLimiter limiter = new ClientRateLimiter(1, new TestClock());

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("b")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
    }

    @Test
    void allowsAgainOnceTheMinuteHasPassed() {
        TestClock clock = new TestClock();
        ClientRateLimiter limiter = new ClientRateLimiter(1, clock);
        assertThat(limiter.tryAcquire("a")).isTrue();

        clock.advance(Duration.ofSeconds(59));
        assertThat(limiter.tryAcquire("a")).isFalse();

        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire("a")).isTrue();
    }

    @Test
    void aRefusedMessageDoesNotExtendTheWait() {
        TestClock clock = new TestClock();
        ClientRateLimiter limiter = new ClientRateLimiter(1, clock);
        limiter.tryAcquire("a");

        clock.advance(Duration.ofSeconds(30));
        assertThat(limiter.tryAcquire("a")).isFalse();

        clock.advance(Duration.ofSeconds(30));
        assertThat(limiter.tryAcquire("a")).isTrue();
    }
}
