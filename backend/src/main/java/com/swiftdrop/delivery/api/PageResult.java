package com.swiftdrop.delivery.api;

import java.util.List;

/**
 * One page of a {@code delivery.api} query result (PR-01). Field names match
 * {@code shared.web.PageResponse}, so web layers can copy them across directly.
 *
 * @param items the page content, never {@code null}
 * @param page 0-based page number
 * @param totalItems number of matching items across all pages
 */
public record PageResult<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public PageResult {
        items = items != null ? List.copyOf(items) : List.of();
    }

    public static <T> PageResult<T> of(List<T> items, PageRequest request, long totalItems) {
        int totalPages = (int) ((Math.max(0, totalItems) + request.size() - 1) / request.size());
        return new PageResult<>(items, request.page(), request.size(), Math.max(0, totalItems), totalPages);
    }
}
