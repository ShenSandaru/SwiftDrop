package com.swiftdrop.merchant.infrastructure.webhook;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Answers lookups from a fixed table without touching DNS. Successive answers for one host are
 * returned in order (the last one repeats), which simulates DNS rebinding.
 */
final class StubHostResolver implements HostResolver {

    private final Map<String, Deque<InetAddress[]>> answers = new HashMap<>();
    private final Map<String, Integer> lookups = new HashMap<>();

    /** Adds one answer for {@code host}, made of the given address literals. */
    StubHostResolver answer(String host, String... literals) {
        InetAddress[] addresses = Arrays.stream(literals).map(StubHostResolver::address).toArray(InetAddress[]::new);
        answers.computeIfAbsent(host, ignored -> new ArrayDeque<>()).addLast(addresses);
        return this;
    }

    StubHostResolver answer(String host, InetAddress... addresses) {
        answers.computeIfAbsent(host, ignored -> new ArrayDeque<>()).addLast(addresses);
        return this;
    }

    int lookups(String host) {
        return lookups.getOrDefault(host, 0);
    }

    @Override
    public synchronized InetAddress[] resolve(String host) throws UnknownHostException {
        lookups.merge(host, 1, Integer::sum);
        Deque<InetAddress[]> queue = answers.get(host);
        if (queue == null || queue.isEmpty()) {
            throw new UnknownHostException(host);
        }
        return queue.size() > 1 ? queue.pollFirst() : queue.peekFirst();
    }

    static InetAddress address(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(literal, e);
        }
    }
}
