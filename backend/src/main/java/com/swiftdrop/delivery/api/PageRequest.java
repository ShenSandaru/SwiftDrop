package com.swiftdrop.delivery.api;

/**
 * Framework-independent page request for {@code delivery.api} queries (PR-01), so consumers do not
 * depend on Spring Data types.
 *
 * @param page 0-based page number
 * @param size page size, 1 to {@value #MAX_SIZE}
 */
public record PageRequest(int page, int size) {

    public static final int MAX_SIZE = 100;

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }
}
