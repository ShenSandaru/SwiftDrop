package com.swiftdrop.shared.web;

/**
 * Constants and utility methods for pagination parameters across all modules (contract §3.4).
 *
 * <p>Standard defaults:
 * <ul>
 *   <li>Default page size: 20</li>
 *   <li>Maximum page size: 100</li>
 *   <li>Minimum page size: 1</li>
 *   <li>Default page number: 0</li>
 * </ul>
 *
 * <p>Policy decision for SD-002: Page sizes exceeding {@value #MAX_PAGE_SIZE} are clamped to
 * {@value #MAX_PAGE_SIZE} when parsed via {@link #clampSize(int)}, while query parameter
 * validation annotations (such as {@code @Max(PageLimit.MAX_PAGE_SIZE)}) reject requests
 * with HTTP 400 Bad Request and {@code invalidFields: ["size"]} if strict validation is configured.
 */
public final class PageLimit {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MIN_PAGE_SIZE = 1;
    public static final int DEFAULT_PAGE_NUMBER = 0;

    public static final String DEFAULT_PAGE_STRING = "0";
    public static final String DEFAULT_SIZE_STRING = "20";

    private PageLimit() {
    }

    /**
     * Clamps the requested page size between {@value #MIN_PAGE_SIZE} and {@value #MAX_PAGE_SIZE}.
     * If {@code requestedSize} is less than {@value #MIN_PAGE_SIZE}, returns {@value #DEFAULT_PAGE_SIZE}.
     * If {@code requestedSize} exceeds {@value #MAX_PAGE_SIZE}, it is clamped to {@value #MAX_PAGE_SIZE}.
     */
    public static int clampSize(int requestedSize) {
        if (requestedSize < MIN_PAGE_SIZE) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requestedSize, MAX_PAGE_SIZE);
    }

    /**
     * Ensures the page index is non-negative.
     */
    public static int clampPage(int requestedPage) {
        return Math.max(DEFAULT_PAGE_NUMBER, requestedPage);
    }
}
