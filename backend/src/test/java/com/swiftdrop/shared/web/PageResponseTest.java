package com.swiftdrop.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PageResponseTest {

    @Test
    void pageCalculationMatchesContractExamples() {
        // Contract §3.4: totalItems: 57, size: 20 -> totalPages: 3
        PageResponse<String> page = PageResponse.of(List.of("a", "b"), 0, 20, 57);
        assertThat(page.items()).containsExactly("a", "b");
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.totalItems()).isEqualTo(57);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void pageCalculationHandlesExactMultiples() {
        PageResponse<String> page = PageResponse.of(List.of(), 1, 20, 40);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void pageCalculationHandlesZeroTotalItems() {
        PageResponse<String> page = PageResponse.of(List.of(), 0, 20, 0);
        assertThat(page.totalPages()).isEqualTo(0);
        assertThat(page.totalItems()).isEqualTo(0);
    }

    @Test
    void defensiveCopyProtectsItemsList() {
        List<String> mutable = new ArrayList<>(List.of("one", "two"));
        PageResponse<String> page = new PageResponse<>(mutable, 0, 10, 2, 1);

        mutable.add("three");
        assertThat(page.items()).containsExactly("one", "two");

        assertThatThrownBy(() -> page.items().add("four"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullItemsDefaultsToEmptyList() {
        PageResponse<String> page = new PageResponse<>(null, 0, 10, 0, 0);
        assertThat(page.items()).isEmpty();
    }

    @Test
    void pageLimitClampsSizeBetweenMinAndMax() {
        // Beyond maximum limit (100) -> clamped to 100
        assertThat(PageLimit.clampSize(150)).isEqualTo(100);
        assertThat(PageLimit.clampSize(101)).isEqualTo(100);
        assertThat(PageLimit.clampSize(100)).isEqualTo(100);

        // Within normal boundaries
        assertThat(PageLimit.clampSize(50)).isEqualTo(50);
        assertThat(PageLimit.clampSize(20)).isEqualTo(20);
        assertThat(PageLimit.clampSize(1)).isEqualTo(1);

        // Below minimum limit (1) -> resets to default 20
        assertThat(PageLimit.clampSize(0)).isEqualTo(20);
        assertThat(PageLimit.clampSize(-5)).isEqualTo(20);
    }

    @Test
    void pageLimitClampsPageToNonNegative() {
        assertThat(PageLimit.clampPage(0)).isEqualTo(0);
        assertThat(PageLimit.clampPage(5)).isEqualTo(5);
        assertThat(PageLimit.clampPage(-1)).isEqualTo(0);
        assertThat(PageLimit.clampPage(-100)).isEqualTo(0);
    }
}
