package com.swiftdrop.merchant.application;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.swiftdrop.merchant.domain.MerchantProcessedEventRepository;

/**
 * Deletes processed event IDs older than the retention (ADR 0004 §9: 30 days) in short batches, each in its
 * own transaction. Older redeliveries are still recognized by the parcel's last applied sequence.
 */
public class MerchantEventRetention {

    private static final Logger log = LoggerFactory.getLogger(MerchantEventRetention.class);
    /** Bounds one run even if the backlog is huge; the rest is deleted on the next day. */
    static final int MAX_BATCHES_PER_RUN = 100;

    private final MerchantProcessedEventRepository processed;
    private final MerchantEventProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public MerchantEventRetention(MerchantProcessedEventRepository processed, MerchantEventProperties properties,
            PlatformTransactionManager transactionManager) {
        this(processed, properties, new TransactionTemplate(transactionManager), Clock.systemUTC());
    }

    MerchantEventRetention(MerchantProcessedEventRepository processed, MerchantEventProperties properties,
            TransactionTemplate transactions, Clock clock) {
        this.processed = processed;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** @return the number of IDs deleted */
    public int deleteExpired() {
        Instant cutoff = clock.instant().minus(properties.processedRetention());
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            Integer deleted = transactions.execute(status ->
                    processed.deleteProcessedBefore(cutoff, properties.retentionBatchSize()));
            int count = deleted == null ? 0 : deleted;
            total += count;
            if (count < properties.retentionBatchSize()) {
                break;
            }
        }
        if (total > 0) {
            log.info("Deleted {} merchant processed event IDs older than {}", total, cutoff);
        }
        return total;
    }
}
