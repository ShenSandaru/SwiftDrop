package com.swiftdrop.notification.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.ParcelEvents;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;
import com.swiftdrop.notification.domain.NotificationChannel;
import com.swiftdrop.notification.domain.NotificationConsumerState;
import com.swiftdrop.notification.domain.NotificationConsumerStateRepository;
import com.swiftdrop.notification.domain.NotificationDeliveryStatus;
import com.swiftdrop.notification.domain.NotificationPendingEvent;
import com.swiftdrop.notification.domain.NotificationPendingEventRepository;
import com.swiftdrop.notification.domain.NotificationPendingEventStatus;
import com.swiftdrop.notification.domain.NotificationRecord;
import com.swiftdrop.notification.domain.NotificationRecordRepository;

/**
 * Core event processor for the Notification module (ADR 0003, ADR 0004 §4.3).
 * Coordinates PostgreSQL advisory locking, per-parcel sequence watermark enforcement,
 * out-of-order event buffering, gap recovery via {@link ParcelEvents}, contiguous buffer draining,
 * and recipient-aware in-app notification creation via {@link NotificationTemplateResolver}.
 */
@Service
public class NotificationEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventProcessor.class);

    private final NotificationConsumerStateRepository consumerStateRepository;
    private final NotificationPendingEventRepository pendingEventRepository;
    private final NotificationRecordRepository recordRepository;
    private final NotificationTemplateResolver templateResolver;
    private final ParcelStatusEventParser eventParser;
    private final ParcelEvents parcelEvents;
    private final UserDirectory userDirectory;

    public NotificationEventProcessor(
            NotificationConsumerStateRepository consumerStateRepository,
            NotificationPendingEventRepository pendingEventRepository,
            NotificationRecordRepository recordRepository,
            NotificationTemplateResolver templateResolver,
            ParcelStatusEventParser eventParser,
            ParcelEvents parcelEvents,
            UserDirectory userDirectory) {
        this.consumerStateRepository = consumerStateRepository;
        this.pendingEventRepository = pendingEventRepository;
        this.recordRepository = recordRepository;
        this.templateResolver = templateResolver;
        this.eventParser = eventParser;
        this.parcelEvents = parcelEvents;
        this.userDirectory = userDirectory;
    }

    /**
     * Atomically processes an incoming {@link ParcelStatusChanged} event.
     *
     * @param event the parsed and validated event envelope
     * @param rawPayload the canonical raw JSON message payload (preserved if buffering is needed)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void process(ParcelStatusChanged event, String rawPayload) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(rawPayload, "rawPayload must not be null");

        UUID parcelId = event.data().parcelId();
        int sequence = event.data().sequence();

        // 1. Acquire transaction-level advisory lock on parcel
        consumerStateRepository.acquireParcelLock(parcelId);

        // 2. Load current consumer state
        Optional<NotificationConsumerState> existingState = consumerStateRepository.findById(parcelId);
        int lastApplied = existingState.map(NotificationConsumerState::getLastAppliedSequence).orElse(0);
        int expected = lastApplied + 1;

        if (sequence <= lastApplied) {
            // Case 1: Duplicate or already applied event
            log.debug("Event already applied for parcel {} (sequence {}, current watermark {})", parcelId, sequence, lastApplied);
            // Clean up any lingering pending event row for this sequence if present
            pendingEventRepository.findByParcelIdAndSequence(parcelId, sequence).ifPresent(pendingEventRepository::delete);
            return;
        }

        if (sequence == expected) {
            // Case 2: In-order arrival
            ParcelStatusChanged eventToApply = event;
            Optional<NotificationPendingEvent> existingPendingOpt = pendingEventRepository.findByParcelIdAndSequence(parcelId, sequence);
            if (existingPendingOpt.isPresent()) {
                NotificationPendingEvent existingPending = existingPendingOpt.get();
                if (existingPending.getStatus() == NotificationPendingEventStatus.UNRESOLVED) {
                    log.warn("Pending event for parcel {} sequence {} is quarantined as UNRESOLVED. Halting processing.", parcelId, sequence);
                    return;
                }
                ParcelStatusChanged parsedCanonical;
                try {
                    parsedCanonical = eventParser.parse(existingPending.getPayload());
                } catch (Exception e) {
                    log.error("Failed to parse canonical buffered payload for parcel {} sequence {}. Marking UNRESOLVED.", parcelId, sequence, e);
                    existingPending.markUnresolved();
                    pendingEventRepository.save(existingPending);
                    return;
                }

                if (!existingPending.getParcelId().equals(parsedCanonical.data().parcelId())
                        || existingPending.getSequence() != parsedCanonical.data().sequence()) {
                    log.error("Corrupted pending event payload detected for parcel {} sequence {} (payload parcelId: {}, sequence: {}). Marking UNRESOLVED.",
                            parcelId, sequence, parsedCanonical.data().parcelId(), parsedCanonical.data().sequence());
                    existingPending.markUnresolved();
                    pendingEventRepository.save(existingPending);
                    return;
                }

                eventToApply = parsedCanonical;
                pendingEventRepository.delete(existingPending);
            }

            applyEvent(eventToApply);
            NotificationConsumerState state = updateWatermark(existingState, parcelId, sequence, eventToApply.occurredAt());

            // Drain any contiguous buffered events that follow
            drainContiguousPendingEvents(state);
            return;
        }

        // Case 3: Out-of-order arrival (sequence > expected)
        NotificationPendingEvent bufferedTriggerEvent = bufferPendingEventIfAbsent(event.eventId(), parcelId, sequence, rawPayload, event.occurredAt());

        // Attempt gap recovery from Delivery history
        List<ParcelStatusChanged> recoveredEvents = parcelEvents.eventsBetween(parcelId, expected, sequence - 1);

        int expectedRecoveredCount = (sequence - 1) - expected + 1;
        if (!isRecoveredHistoryComplete(recoveredEvents, expected, expectedRecoveredCount)) {
            // Incomplete history: do not partially apply. Retain buffered event and commit cleanly.
            log.debug("Incomplete history recovered for parcel {} (expected range {}-{}, got {} items). Buffering.",
                    parcelId, expected, sequence - 1, recoveredEvents != null ? recoveredEvents.size() : 0);
            return;
        }

        // Complete history recovered: apply sequentially
        NotificationConsumerState state = existingState.orElse(null);
        for (ParcelStatusChanged recovered : recoveredEvents) {
            applyEvent(recovered);
            state = updateWatermark(Optional.ofNullable(state), parcelId, recovered.data().sequence(), recovered.occurredAt());
            // Clean up any matching pending row for recovered sequence
            pendingEventRepository.findByParcelIdAndSequence(parcelId, recovered.data().sequence())
                    .ifPresent(pendingEventRepository::delete);
        }

        // Now apply event S
        applyEvent(event);
        state = updateWatermark(Optional.ofNullable(state), parcelId, sequence, event.occurredAt());
        pendingEventRepository.delete(bufferedTriggerEvent);

        // Drain any contiguous buffered events that follow
        drainContiguousPendingEvents(state);
    }

    private void drainContiguousPendingEvents(NotificationConsumerState state) {
        UUID parcelId = state.getParcelId();
        int currentWatermark = state.getLastAppliedSequence();

        while (true) {
            int nextSeq = currentWatermark + 1;
            Optional<NotificationPendingEvent> nextPendingOpt = pendingEventRepository.findByParcelIdAndSequence(parcelId, nextSeq);
            if (nextPendingOpt.isEmpty()) {
                break;
            }

            NotificationPendingEvent nextPending = nextPendingOpt.get();
            if (nextPending.getStatus() == NotificationPendingEventStatus.UNRESOLVED) {
                log.warn("Sequence {} for parcel {} is quarantined as UNRESOLVED. Halting draining.", nextSeq, parcelId);
                break;
            }

            ParcelStatusChanged pendingEvent;
            try {
                pendingEvent = eventParser.parse(nextPending.getPayload());
            } catch (Exception e) {
                log.error("Failed to parse buffered payload for parcel {} sequence {}. Marking UNRESOLVED.", parcelId, nextSeq, e);
                nextPending.markUnresolved();
                pendingEventRepository.save(nextPending);
                break;
            }

            // Consistency validation: verify payload parcelId and sequence match database entity
            if (!nextPending.getParcelId().equals(pendingEvent.data().parcelId())
                    || nextPending.getSequence() != pendingEvent.data().sequence()) {
                log.error("Corrupted pending event payload detected for parcel {} sequence {} (payload parcelId: {}, sequence: {}). Marking UNRESOLVED.",
                        parcelId, nextSeq, pendingEvent.data().parcelId(), pendingEvent.data().sequence());
                nextPending.markUnresolved();
                pendingEventRepository.save(nextPending);
                break;
            }

            applyEvent(pendingEvent);
            state.advanceSequence(nextSeq, pendingEvent.occurredAt());
            consumerStateRepository.save(state);
            pendingEventRepository.delete(nextPending);
            currentWatermark = nextSeq;
        }
    }

    private void applyEvent(ParcelStatusChanged event) {
        if (event.data().source() == ParcelSource.CUSTOMER) {
            UUID customerId = event.data().ownerCustomerId();
            if (customerId == null) {
                throw new EventParsingException("CUSTOMER parcel event must have non-null ownerCustomerId (parcelId="
                        + event.data().parcelId() + ")");
            }

            Optional<UserSummary> customer = userDirectory.findUser(customerId);
            if (customer.isPresent() && customer.get().enabled()) {
                templateResolver.resolve(event).ifPresent(content -> {
                    // Check idempotency pre-insert
                    if (!recordRepository.existsByEventIdAndChannelAndRecipientUserId(
                            event.eventId(), NotificationChannel.IN_APP, customerId)) {
                        NotificationRecord record = new NotificationRecord(
                                event.eventId(),
                                customerId,
                                event.data().parcelId(),
                                event.data().trackingNumber(),
                                content.title(),
                                content.message(),
                                NotificationChannel.IN_APP,
                                NotificationDeliveryStatus.NOT_APPLICABLE,
                                event.occurredAt());
                        recordRepository.save(record);
                    }
                });
            } else {
                log.debug("Customer account {} missing or disabled for parcel {}. Skipping notification creation.",
                        customerId, event.data().parcelId());
            }
        }
        // For MERCHANT parcels, ownerMerchantId is never passed to UserDirectory and no customer notification is emitted.
    }

    private NotificationConsumerState updateWatermark(
            Optional<NotificationConsumerState> existingState,
            UUID parcelId,
            int sequence,
            Instant occurredAt) {
        NotificationConsumerState state;
        if (existingState.isPresent()) {
            state = existingState.get();
            state.advanceSequence(sequence, occurredAt);
        } else {
            state = new NotificationConsumerState(parcelId, sequence, occurredAt);
        }
        return consumerStateRepository.save(state);
    }

    private NotificationPendingEvent bufferPendingEventIfAbsent(UUID eventId, UUID parcelId, int sequence, String rawPayload, Instant occurredAt) {
        Optional<NotificationPendingEvent> existing = pendingEventRepository.findByParcelIdAndSequence(parcelId, sequence);
        if (existing.isPresent()) {
            return existing.get();
        }
        NotificationPendingEvent pending = new NotificationPendingEvent(
                eventId,
                parcelId,
                sequence,
                rawPayload,
                occurredAt);
        return pendingEventRepository.save(pending);
    }

    private boolean isRecoveredHistoryComplete(List<ParcelStatusChanged> recoveredEvents, int fromSequence, int expectedCount) {
        if (recoveredEvents == null || recoveredEvents.size() != expectedCount) {
            return false;
        }
        for (int i = 0; i < expectedCount; i++) {
            if (recoveredEvents.get(i).data().sequence() != fromSequence + i) {
                return false;
            }
        }
        return true;
    }
}

