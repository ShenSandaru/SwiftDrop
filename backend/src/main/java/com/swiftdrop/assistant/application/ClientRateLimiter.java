package com.swiftdrop.assistant.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Limits how many messages one client may send per minute (ADR 0010 §4). In memory and per
 * application instance, which is enough for a single backend. Clients are identified by a string
 * the caller chooses (the remote address); nothing else about them is kept.
 */
@Component
public class ClientRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Deque<Instant>> sent = new ConcurrentHashMap<>();
    private final int limit;
    private final Clock clock;

    @Autowired
    ClientRateLimiter(AssistantProperties properties) {
        this(properties.rateLimitPerMinute(), Clock.systemUTC());
    }

    ClientRateLimiter(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    /** Records a message from the client and returns whether it is within the limit. */
    public boolean tryAcquire(String client) {
        Instant now = clock.instant();
        if (sent.size() > CLEANUP_THRESHOLD) {
            sent.values().removeIf(times -> isIdle(times, now));
        }
        Deque<Instant> times = sent.computeIfAbsent(client, key -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && !times.peekFirst().isAfter(now.minus(WINDOW))) {
                times.removeFirst();
            }
            if (times.size() >= limit) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    private static boolean isIdle(Deque<Instant> times, Instant now) {
        synchronized (times) {
            return times.isEmpty() || !times.peekLast().isAfter(now.minus(WINDOW));
        }
    }
}
