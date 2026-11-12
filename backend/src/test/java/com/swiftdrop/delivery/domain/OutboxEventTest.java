package com.swiftdrop.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OutboxEventTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:15:30Z");

    private static OutboxEvent newRow() {
        return new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), 2, "parcel.status-changed", "{}", NOW);
    }

    @Test
    void newRowIsUnpublishedAndDueAtOnce() {
        OutboxEvent row = newRow();

        assertThat(row.isPublished()).isFalse();
        assertThat(row.getAttempts()).isZero();
        assertThat(row.isDue(NOW)).isTrue();
    }

    @Test
    void failureCountsTheAttemptAndPostponesTheRow() {
        OutboxEvent row = newRow();

        row.recordFailure("returned", NOW.plusSeconds(1));

        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastFailureCategory()).isEqualTo("returned");
        assertThat(row.isDue(NOW)).isFalse();
        assertThat(row.isDue(NOW.plusSeconds(1))).isTrue();
        assertThat(row.isPublished()).isFalse();
    }

    @Test
    void publishedRowKeepsItsLastFailureForReference() {
        OutboxEvent row = newRow();
        row.recordFailure("nack", NOW);

        row.markPublished(NOW.plusSeconds(2));

        assertThat(row.isPublished()).isTrue();
        assertThat(row.getPublishedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(row.getLastFailureCategory()).isEqualTo("nack");
    }

    @Test
    void publishedRowCannotBePublishedOrFailAgain() {
        OutboxEvent row = newRow();
        row.markPublished(NOW);

        assertThatThrownBy(() -> row.markPublished(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> row.recordFailure("nack", NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failureCategoryMustFitItsColumn() {
        OutboxEvent row = newRow();

        assertThatThrownBy(() -> row.recordFailure(" ", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> row.recordFailure("x".repeat(41), NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThat(row.getAttempts()).isZero();
    }
}
