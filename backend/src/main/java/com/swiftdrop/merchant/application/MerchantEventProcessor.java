package com.swiftdrop.merchant.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantEventAnomaly;
import com.swiftdrop.merchant.domain.MerchantEventAnomalyRepository;
import com.swiftdrop.merchant.domain.MerchantEventState;
import com.swiftdrop.merchant.domain.MerchantEventStateRepository;
import com.swiftdrop.merchant.domain.MerchantPendingEvent;
import com.swiftdrop.merchant.domain.MerchantPendingEventRepository;
import com.swiftdrop.merchant.domain.MerchantProcessedEvent;
import com.swiftdrop.merchant.domain.MerchantProcessedEventRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpoint;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;

/**
 * Applies parcel status events to merchant webhooks (SD-410, ADR 0004 §4.3, §7.1), one transaction per
 * event. Customer parcels are ignored. For a merchant parcel the state row is locked, so concurrent
 * deliveries of the same parcel run one after the other; every check below runs under that lock:
 * <ul>
 * <li>an event already processed or pending is a duplicate and has no effect;</li>
 * <li>an event contradicting stored events is quarantined as an anomaly, never silently dropped;</li>
 * <li>the next sequence is applied, followed by any pending events that now follow without a gap;</li>
 * <li>a later sequence waits as pending until the gap is closed or reconciled.</li>
 * </ul>
 * Applying an event creates one {@code PENDING} delivery record per enabled endpoint subscribed to the new
 * status, if the merchant is {@code APPROVED} <em>when the event is processed</em>; the sequence advances
 * either way, so a suspended merchant's events never block later ones.
 */
@Service
@EnableConfigurationProperties(MerchantEventProperties.class)
public class MerchantEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(MerchantEventProcessor.class);

    /** What happened to an event; for logs and tests. */
    public enum Outcome {
        IGNORED, APPLIED, PENDING, DUPLICATE, ANOMALY
    }

    private final MerchantEventStateRepository states;
    private final MerchantProcessedEventRepository processed;
    private final MerchantPendingEventRepository pending;
    private final MerchantEventAnomalyRepository anomalies;
    private final MerchantWebhookDeliveryRepository deliveries;
    private final MerchantWebhookEndpointRepository endpoints;
    private final MerchantRepository merchants;
    private final ParcelQueries parcels;
    private final MerchantEventJson json;
    private final MerchantEventMetrics metrics;
    private final MerchantEventProperties properties;
    private final Clock clock;

    @Autowired
    public MerchantEventProcessor(MerchantEventStateRepository states, MerchantProcessedEventRepository processed,
            MerchantPendingEventRepository pending, MerchantEventAnomalyRepository anomalies,
            MerchantWebhookDeliveryRepository deliveries, MerchantWebhookEndpointRepository endpoints,
            MerchantRepository merchants, ParcelQueries parcels, MerchantEventJson json, MerchantEventMetrics metrics,
            MerchantEventProperties properties) {
        this(states, processed, pending, anomalies, deliveries, endpoints, merchants, parcels, json, metrics, properties,
                Clock.systemUTC());
    }

    MerchantEventProcessor(MerchantEventStateRepository states, MerchantProcessedEventRepository processed,
            MerchantPendingEventRepository pending, MerchantEventAnomalyRepository anomalies,
            MerchantWebhookDeliveryRepository deliveries, MerchantWebhookEndpointRepository endpoints,
            MerchantRepository merchants, ParcelQueries parcels, MerchantEventJson json, MerchantEventMetrics metrics,
            MerchantEventProperties properties, Clock clock) {
        this.states = states;
        this.processed = processed;
        this.pending = pending;
        this.anomalies = anomalies;
        this.deliveries = deliveries;
        this.endpoints = endpoints;
        this.merchants = merchants;
        this.parcels = parcels;
        this.json = json;
        this.metrics = metrics;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Handles one event from the queue.
     *
     * @param payload the message body exactly as received; stored for webhooks and pending events
     */
    @Transactional
    public Outcome process(ParcelStatusChanged event, String payload) {
        ParcelStatusChangedData data = event.data();
        if (data.source() != ParcelSource.MERCHANT) {
            return Outcome.IGNORED;
        }
        UUID parcelId = data.parcelId();
        UUID merchantId = data.ownerMerchantId();
        if (merchantId == null) {
            return anomaly(event, payload, MerchantEventAnomaly.Kind.OWNERSHIP_MISMATCH, "merchant parcel without merchant");
        }
        if (!states.existsById(parcelId)) {
            if (!ownsParcel(merchantId, data)) {
                return anomaly(event, payload, MerchantEventAnomaly.Kind.OWNERSHIP_MISMATCH,
                        "merchant does not own the parcel");
            }
            states.insertIfAbsent(parcelId, merchantId, clock.instant());
        }
        MerchantEventState state = states.lockByParcelId(parcelId)
                .orElseThrow(() -> new IllegalStateException("event state missing for parcel " + parcelId));
        if (!state.getMerchantId().equals(merchantId)) {
            return anomaly(event, payload, MerchantEventAnomaly.Kind.OWNERSHIP_MISMATCH,
                    "merchant differs from the parcel's recorded merchant");
        }

        Optional<Outcome> known = classifyKnown(event, payload, state);
        if (known.isPresent()) {
            return known.get();
        }
        int sequence = data.sequence();
        if (sequence == state.getLastAppliedSequence() + 1) {
            apply(state, event, payload);
            drainPending(state);
            return Outcome.APPLIED;
        }
        Instant now = clock.instant();
        pending.save(new MerchantPendingEvent(event.eventId(), parcelId, sequence, payload, now,
                now.plus(properties.gracePeriod())));
        metrics.deferred();
        log.info("Merchant event pending: eventId={}, parcelId={}, sequence={}, lastApplied={}", event.eventId(),
                parcelId, sequence, state.getLastAppliedSequence());
        return Outcome.PENDING;
    }

    /**
     * Duplicate or anomaly if the event's ID or its parcel and sequence are already stored, or its sequence
     * was already applied. Checks processed and pending events; runs under the parcel's lock.
     */
    private Optional<Outcome> classifyKnown(ParcelStatusChanged event, String payload, MerchantEventState state) {
        UUID parcelId = event.data().parcelId();
        int sequence = event.data().sequence();
        Optional<MerchantProcessedEvent> processedById = processed.findById(event.eventId());
        if (processedById.isPresent()) {
            return Optional.of(sameSlot(processedById.get().getParcelId(), processedById.get().getSequence(), parcelId,
                    sequence) ? duplicate(event) : identityConflict(event, payload));
        }
        Optional<MerchantPendingEvent> pendingById = pending.findById(event.eventId());
        if (pendingById.isPresent()) {
            return Optional.of(sameSlot(pendingById.get().getParcelId(), pendingById.get().getSequence(), parcelId,
                    sequence) ? duplicate(event) : identityConflict(event, payload));
        }
        if (processed.findByParcelIdAndSequence(parcelId, sequence).isPresent()
                || pending.findByParcelIdAndSequence(parcelId, sequence).isPresent()) {
            return Optional.of(anomaly(event, payload, MerchantEventAnomaly.Kind.SEQUENCE_CONFLICT,
                    "another event is stored for this parcel and sequence"));
        }
        if (sequence <= state.getLastAppliedSequence()) {
            return Optional.of(anomaly(event, payload, MerchantEventAnomaly.Kind.UNKNOWN_DUPLICATE,
                    "sequence already applied by an event that is no longer kept"));
        }
        return Optional.empty();
    }

    /**
     * Applies one event that follows the parcel's last applied sequence: delivery records (if approved),
     * processed ID and the new sequence. Callers hold the parcel's lock and checked the sequence.
     */
    void apply(MerchantEventState state, ParcelStatusChanged event, String payload) {
        ParcelStatusChangedData data = event.data();
        Instant now = clock.instant();
        boolean approved = merchants.findById(state.getMerchantId()).map(Merchant::isApproved).orElse(false);
        int records = 0;
        if (approved) {
            for (MerchantWebhookEndpoint endpoint : endpoints.findActiveForMerchantAndEvent(state.getMerchantId(),
                    data.newStatus())) {
                records += deliveries.insertPending(UUID.randomUUID(), endpoint.getId(), state.getMerchantId(),
                        event.eventId(), data.parcelId(), data.sequence(), data.newStatus().name(), payload, now);
            }
        }
        processed.save(new MerchantProcessedEvent(event.eventId(), data.parcelId(), data.sequence(), now));
        state.advanceTo(data.sequence(), now);
        metrics.applied();
        log.info("Merchant event applied: eventId={}, parcelId={}, sequence={}, webhookRecords={}{}", event.eventId(),
                data.parcelId(), data.sequence(), records, approved ? "" : " (merchant not approved)");
    }

    /** Applies pending events that now follow the last applied sequence without a gap, and deletes them. */
    void drainPending(MerchantEventState state) {
        Optional<MerchantPendingEvent> next;
        while ((next = pending.findByParcelIdAndSequence(state.getParcelId(), state.getLastAppliedSequence() + 1))
                .isPresent()) {
            MerchantPendingEvent waiting = next.get();
            apply(state, json.parse(waiting.getPayload()), waiting.getPayload());
            pending.delete(waiting);
        }
    }

    private boolean ownsParcel(UUID merchantId, ParcelStatusChangedData data) {
        if (data.merchantOrderReference() == null || merchants.findById(merchantId).isEmpty()) {
            return false;
        }
        return parcels.findByMerchantReference(merchantId, data.merchantOrderReference())
                .map(parcel -> parcel.id().equals(data.parcelId()))
                .orElse(false);
    }

    private static boolean sameSlot(UUID storedParcel, int storedSequence, UUID parcelId, int sequence) {
        return storedParcel.equals(parcelId) && storedSequence == sequence;
    }

    private Outcome duplicate(ParcelStatusChanged event) {
        metrics.duplicate();
        log.info("Merchant event duplicate skipped: eventId={}, parcelId={}, sequence={}", event.eventId(),
                event.data().parcelId(), event.data().sequence());
        return Outcome.DUPLICATE;
    }

    private Outcome identityConflict(ParcelStatusChanged event, String payload) {
        return anomaly(event, payload, MerchantEventAnomaly.Kind.IDENTITY_CONFLICT,
                "event ID already stored for another parcel or sequence");
    }

    /** Quarantines the event; a redelivery of an already quarantined event adds no second row. */
    private Outcome anomaly(ParcelStatusChanged event, String payload, MerchantEventAnomaly.Kind kind, String detail) {
        if (anomalies.findByEventId(event.eventId()).stream().anyMatch(stored -> stored.getKind() == kind)) {
            return duplicate(event);
        }
        anomalies.save(new MerchantEventAnomaly(event.eventId(), event.data().parcelId(), event.data().sequence(), kind,
                detail, payload, clock.instant()));
        metrics.anomaly(kind);
        log.error("Merchant event quarantined ({}): eventId={}, parcelId={}, sequence={}", kind, event.eventId(),
                event.data().parcelId(), event.data().sequence());
        return Outcome.ANOMALY;
    }
}
