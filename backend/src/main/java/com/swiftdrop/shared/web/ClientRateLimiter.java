package com.swiftdrop.shared.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits how many requests one client may make per minute, for public endpoints (the assistant,
 * ADR 0010 §4, and public tracking). In memory and per application instance, which is enough for a
 * single backend. Clients are identified by a string the caller chooses (the remote address);
 * nothing else about them is kept. Each module creates its own limiter with its own limit.
 */
public class ClientRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Deque<Instant>> sent = new ConcurrentHashMap<>();
    private final int limit;
    private final Clock clock;

    public ClientRateLimiter(int limitPerMinute) {
        this(limitPerMinute, Clock.systemUTC());
    }

    ClientRateLimiter(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    /** Records a request from the client and returns whether it is within the limit. */
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
