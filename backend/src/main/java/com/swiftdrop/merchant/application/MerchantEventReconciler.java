package com.swiftdrop.merchant.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.swiftdrop.delivery.api.ParcelEvents;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.merchant.domain.MerchantEventState;
import com.swiftdrop.merchant.domain.MerchantEventStateRepository;
import com.swiftdrop.merchant.domain.MerchantPendingEvent;
import com.swiftdrop.merchant.domain.MerchantPendingEventRepository;
import com.swiftdrop.merchant.domain.MerchantProcessedEventRepository;

/**
 * Closes sequence gaps that did not close by themselves (ADR 0004 §4.3). For a parcel whose pending events
 * waited longer than the grace period, the missing events are rebuilt from delivery's history with their
 * original IDs and applied in order; then the pending events that now follow are applied.
 *
 * <p>Safe with several instances: each parcel is handled in its own transaction holding the parcel's state
 * lock ({@code SKIP LOCKED}, so a busy parcel is left for the next run), and the pending events are read again
 * after the lock. Only a contiguous run of sequences is applied: the state never advances across a sequence
 * that history does not return. A gap that stays open is retried with backoff; after the configured number of
 * attempts the parcel is marked stuck. Pending events are never deleted unless applied.
 */
public class MerchantEventReconciler {

    private static final Logger log = LoggerFactory.getLogger(MerchantEventReconciler.class);

    private final MerchantEventStateRepository states;
    private final MerchantPendingEventRepository pending;
    private final MerchantProcessedEventRepository processed;
    private final ParcelEvents history;
    private final MerchantEventProcessor processor;
    private final MerchantEventJson json;
    private final MerchantEventMetrics metrics;
    private final MerchantEventProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public MerchantEventReconciler(MerchantEventStateRepository states, MerchantPendingEventRepository pending,
            MerchantProcessedEventRepository processed, ParcelEvents history, MerchantEventProcessor processor,
            MerchantEventJson json, MerchantEventMetrics metrics, MerchantEventProperties properties,
            PlatformTransactionManager transactionManager) {
        this(states, pending, processed, history, processor, json, metrics, properties,
                new TransactionTemplate(transactionManager), Clock.systemUTC());
    }

    MerchantEventReconciler(MerchantEventStateRepository states, MerchantPendingEventRepository pending,
            MerchantProcessedEventRepository processed, ParcelEvents history, MerchantEventProcessor processor,
            MerchantEventJson json, MerchantEventMetrics metrics, MerchantEventProperties properties,
            TransactionTemplate transactions, Clock clock) {
        this.states = states;
        this.pending = pending;
        this.processed = processed;
        this.history = history;
        this.processor = processor;
        this.json = json;
        this.metrics = metrics;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** One scheduled run: reconciles up to the batch size of parcels with due pending events. */
    public void reconcileDue() {
        List<UUID> due = transactions.execute(status -> pending.findDueParcelIds(clock.instant(),
                Limit.of(properties.reconcileBatchSize())));
        for (UUID parcelId : due == null ? List.<UUID>of() : due) {
            try {
                transactions.executeWithoutResult(status -> reconcile(parcelId));
            } catch (RuntimeException e) {
                // The transaction rolled back; record the failed attempt in a new one.
                log.warn("Merchant event reconciliation failed: parcelId={}, cause={}", parcelId,
                        e.getClass().getSimpleName());
                transactions.executeWithoutResult(status -> recordFailure(parcelId));
            }
        }
    }

    /** Reconciles one parcel; runs inside a transaction. */
    void reconcile(UUID parcelId) {
        Optional<MerchantEventState> locked = states.tryLockByParcelId(parcelId);
        if (locked.isEmpty() || locked.get().isStuck()) {
            return;
        }
        MerchantEventState state = locked.get();
        Instant now = clock.instant();
        List<MerchantPendingEvent> waiting = pending.findByParcelIdOrderBySequenceAsc(parcelId);
        if (waiting.isEmpty() || waiting.get(0).getNextReconcileAt().isAfter(now)) {
            return;
        }
        int firstPending = waiting.get(0).getSequence();
        if (firstPending > state.getLastAppliedSequence() + 1) {
            List<ParcelStatusChanged> missing = history.eventsBetween(parcelId, state.getLastAppliedSequence() + 1,
                    firstPending - 1).stream()
                    .sorted(Comparator.comparingInt(event -> event.data().sequence()))
                    .toList();
            for (ParcelStatusChanged event : missing) {
                if (event.data().sequence() != state.getLastAppliedSequence() + 1 || !belongs(event, state)) {
                    break;
                }
                processor.apply(state, event, json.toJson(event));
            }
        }
        processor.drainPending(state);
        if (!pending.findByParcelIdOrderBySequenceAsc(parcelId).isEmpty()) {
            recordFailure(state, now);
        } else {
            log.info("Merchant event gap reconciled: parcelId={}, lastApplied={}", parcelId,
                    state.getLastAppliedSequence());
        }
    }

    /**
     * A rebuilt event may be applied only if it is this parcel's, owned by the recorded merchant, and its ID
     * is not already stored for another sequence.
     */
    private boolean belongs(ParcelStatusChanged event, MerchantEventState state) {
        boolean valid = event.data().parcelId().equals(state.getParcelId())
                && event.data().source() == ParcelSource.MERCHANT
                && state.getMerchantId().equals(event.data().ownerMerchantId())
                && processed.findById(event.eventId()).isEmpty()
                && pending.findById(event.eventId()).isEmpty();
        if (!valid) {
            log.error("Merchant event history rejected: eventId={}, parcelId={}, sequence={}", event.eventId(),
                    state.getParcelId(), event.data().sequence());
        }
        return valid;
    }

    private void recordFailure(UUID parcelId) {
        states.tryLockByParcelId(parcelId).ifPresent(state -> recordFailure(state, clock.instant()));
    }

    /** The gap is still open: back off, and mark the parcel stuck once the attempts are used up. */
    private void recordFailure(MerchantEventState state, Instant now) {
        List<MerchantPendingEvent> waiting = pending.findByParcelIdOrderBySequenceAsc(state.getParcelId());
        if (waiting.isEmpty()) {
            return;
        }
        int failures = waiting.get(0).getReconcileAttempts() + 1;
        waiting.forEach(event -> event.reconcileFailed(now, properties.reconcileBackoffAfter(failures)));
        if (failures >= properties.reconcileAttempts() && !state.isStuck()) {
            state.markStuck(now);
            metrics.stuck();
            log.error("Merchant parcel events stuck: parcelId={}, lastApplied={}, firstPending={}, attempts={}",
                    state.getParcelId(), state.getLastAppliedSequence(), waiting.get(0).getSequence(), failures);
        }
    }
}
