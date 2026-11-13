package com.swiftdrop.delivery.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import com.swiftdrop.delivery.domain.OutboxEventRepository;

class OutboxRetentionTest {

    private static final Instant NOW = Instant.parse("2026-11-09T03:30:00Z");

    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    private final OutboxRetention retention = new OutboxRetention(outbox, TransactionOperations.withoutTransaction(),
            OutboxRelayProperties.defaults(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void publishedRowsOlderThanTheRetentionAreDeleted() {
        retention.deletePublished();

        verify(outbox).deletePublishedBefore(NOW.minus(Duration.ofDays(7)));
    }

    @Test
    void cleanupFailureIsLoggedNotThrown() {
        when(outbox.deletePublishedBefore(any())).thenThrow(new IllegalStateException("database unavailable"));

        assertThatCode(retention::deletePublished).doesNotThrowAnyException();
    }
}
