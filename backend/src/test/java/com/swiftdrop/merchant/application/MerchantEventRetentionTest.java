package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.swiftdrop.merchant.domain.MerchantProcessedEventRepository;

class MerchantEventRetentionTest {

    private static final Instant NOW = Instant.parse("2026-11-14T03:45:00Z");

    private final MerchantProcessedEventRepository processed = mock(MerchantProcessedEventRepository.class);
    private final MerchantEventProperties properties = new MerchantEventProperties(null, null, null, null, null, null,
            null, null, null, 1_000);
    private final MerchantEventRetention retention = new MerchantEventRetention(processed, properties,
            new TransactionTemplate(mock(PlatformTransactionManager.class)), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("Deletes IDs older than 30 days in batches until a batch is not full")
    void batches() {
        Instant cutoff = NOW.minus(Duration.ofDays(30));
        when(processed.deleteProcessedBefore(cutoff, 1_000)).thenReturn(1_000, 1_000, 250);

        assertThat(retention.deleteExpired()).isEqualTo(2_250);

        verify(processed, times(3)).deleteProcessedBefore(eq(cutoff), eq(1_000));
    }

    @Test
    @DisplayName("One run is bounded even if the backlog never ends")
    void boundedRun() {
        when(processed.deleteProcessedBefore(NOW.minus(Duration.ofDays(30)), 1_000)).thenReturn(1_000);

        assertThat(retention.deleteExpired()).isEqualTo(1_000 * MerchantEventRetention.MAX_BATCHES_PER_RUN);
    }
}
