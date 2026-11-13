package com.swiftdrop.delivery.infrastructure.outbox;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

import com.swiftdrop.delivery.domain.OutboxEventRepository;

/**
 * Daily cleanup of published outbox rows older than the retention (ADR 0004 §9). Unpublished rows
 * are never deleted. Past events can still be rebuilt from the status history (ADR 0004 §2).
 */
public class OutboxRetention {

    private static final Logger log = LoggerFactory.getLogger(OutboxRetention.class);

    private final OutboxEventRepository outbox;
    private final TransactionOperations transactions;
    private final OutboxRelayProperties properties;
    private final Clock clock;

    OutboxRetention(OutboxEventRepository outbox, TransactionOperations transactions,
            OutboxRelayProperties properties, Clock clock) {
        this.outbox = outbox;
        this.transactions = transactions;
        this.properties = properties;
        this.clock = clock;
    }

    /** Deletes old published rows. Called by the scheduler once a day. */
    public void deletePublished() {
        try {
            Instant cutoff = clock.instant().minus(properties.retention());
            Integer deleted = transactions.execute(status -> outbox.deletePublishedBefore(cutoff));
            if (deleted != null && deleted > 0) {
                log.info("Deleted {} published outbox rows older than {}", deleted, cutoff);
            }
        } catch (RuntimeException e) {
            log.warn("Outbox cleanup failed: {}", e.toString());
        }
    }
}
