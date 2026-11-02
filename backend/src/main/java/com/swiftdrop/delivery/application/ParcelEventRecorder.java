package com.swiftdrop.delivery.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;
import com.swiftdrop.delivery.domain.OutboxEvent;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryEntry;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;

/**
 * Records a parcel's latest status (creation or change) as a history entry and an outbox event in
 * the caller's transaction (ADR 0004 §3), and publishes the event in-process. In-process listeners
 * such as {@code tracking} must use {@code @TransactionalEventListener} so they only see committed
 * changes.
 */
@Component
public class ParcelEventRecorder {

    private final ParcelStatusHistoryRepository history;
    private final OutboxEventRepository outbox;
    private final ParcelEventSerializer serializer;
    private final ApplicationEventPublisher publisher;

    public ParcelEventRecorder(ParcelStatusHistoryRepository history, OutboxEventRepository outbox,
            ParcelEventSerializer serializer, ApplicationEventPublisher publisher) {
        this.history = history;
        this.outbox = outbox;
        this.serializer = serializer;
        this.publisher = publisher;
    }

    /**
     * Records the parcel's current status and sequence, which the caller has just set.
     *
     * @param previousStatus {@code null} for the creation event
     * @param note optional courier note; stored in history only, never in the event
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ParcelStatusChanged record(Parcel parcel, ParcelStatus previousStatus, Actor actor, String note,
            FailureReason failureReason, Instant occurredAt) {
        Objects.requireNonNull(parcel.getId(), "parcel must be persisted first");
        ParcelStatusHistoryEntry entry = history.save(new ParcelStatusHistoryEntry(parcel.getId(),
                parcel.getLastSequence(), previousStatus, parcel.getStatus(), actor.userId(), actor.role(), note,
                failureReason, UUID.randomUUID(), occurredAt));
        ParcelStatusChanged event = eventFor(parcel, entry);
        outbox.save(new OutboxEvent(event.eventId(), parcel.getId(), entry.getSequence(), event.eventType(),
                serializer.toJson(event), occurredAt));
        publisher.publishEvent(event);
        return event;
    }

    /**
     * The event of one history entry. Used both when recording and when rebuilding past events, so a
     * rebuilt event equals the original outbox event (ADR 0004 §4.3).
     */
    public static ParcelStatusChanged eventFor(Parcel parcel, ParcelStatusHistoryEntry entry) {
        if (!parcel.getId().equals(entry.getParcelId())) {
            throw new IllegalArgumentException("History entry belongs to another parcel");
        }
        return ParcelStatusChanged.of(entry.getEventId(), entry.getOccurredAt(), new ParcelStatusChangedData(
                parcel.getId(),
                parcel.getTrackingNumber(),
                entry.getFromStatus(),
                entry.getToStatus(),
                entry.getSequence(),
                parcel.getSource(),
                parcel.getCustomerId(),
                parcel.getMerchantId(),
                parcel.getMerchantOrderReference(),
                entry.getFailureReasonCode()));
    }
}
