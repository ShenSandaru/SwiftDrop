package com.swiftdrop.shared.web;

import java.util.List;

/**
 * Standard page shape for all SwiftDrop REST endpoints (contract §3.4).
 *
 * @param <T> the item type
 * @param items the page content
 * @param page the 0-based page number
 * @param size the page size
 * @param totalItems the total number of matching items across all pages
 * @param totalPages the total number of pages
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {

    public PageResponse {
        items = items != null ? List.copyOf(items) : List.of();
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        int calculatedPages = size <= 0 ? 0 : (int) Math.ceil((double) Math.max(0, totalItems) / size);
        return new PageResponse<>(items, Math.max(0, page), Math.max(1, size), Math.max(0, totalItems), calculatedPages);
    }
}
