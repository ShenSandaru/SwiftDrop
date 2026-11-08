package com.swiftdrop.delivery.infrastructure.outbox;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.swiftdrop.delivery.domain.OutboxEventRepository;

/**
 * Daily cleanup of published outbox rows older than the retention (ADR 0004 §9). Unpublished rows are
 * never deleted; status history keeps every event rebuildable (ADR 0004 §2).
 */
class OutboxRetention {

    private static final Logger log = LoggerFactory.getLogger(OutboxRetention.class);

    private final OutboxEventRepository outbox;
    private final Duration retention;
    private final Clock clock;

    OutboxRetention(OutboxEventRepository outbox, Duration retention, Clock clock) {
        this.outbox = outbox;
        this.retention = retention;
        this.clock = clock;
    }

    void deleteExpired() {
        try {
            int deleted = outbox.deletePublishedBefore(clock.instant().minus(retention));
            if (deleted > 0) {
                log.info("Deleted {} published outbox rows older than {}", deleted, retention);
            }
        } catch (RuntimeException e) {
            log.warn("Outbox retention cleanup failed: {}", e.getClass().getSimpleName());
        }
    }
}
