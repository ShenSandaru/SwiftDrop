package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.domain.ActorRole;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEvent;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryEntry;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.pricing.api.ParcelSize;

/** The service with the real recorder and serializer; only repositories and the publisher are mocked. */
class ParcelStatusServiceTest {

    private static final UUID PARCEL_ID = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID COURIER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final Instant NOW = Instant.parse("2026-11-03T12:01:02Z");

    private ParcelRepository parcels;
    private DeliveryProofRepository proofs;
    private ParcelStatusHistoryRepository history;
    private OutboxEventRepository outbox;
    private ApplicationEventPublisher publisher;
    private ParcelStatusService service;
    private Parcel parcel;

    @BeforeEach
    void setUp() {
        parcels = mock(ParcelRepository.class);
        proofs = mock(DeliveryProofRepository.class);
        history = mock(ParcelStatusHistoryRepository.class);
        outbox = mock(OutboxEventRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        when(history.save(any())).thenAnswer(call -> call.getArgument(0));
        when(outbox.save(any())).thenAnswer(call -> call.getArgument(0));
        ParcelEventRecorder recorder = new ParcelEventRecorder(history, outbox, new ParcelEventSerializer(), publisher);
        service = new ParcelStatusService(parcels, proofs, recorder, Clock.fixed(NOW, ZoneOffset.UTC));

        parcel = Parcel.forCustomer("SD7K3M9Q2XWB", CUSTOMER_ID,
                new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo", UUID.randomUUID(), null),
                new ParcelAddress("Kumari Silva", "+94711234567", "4 Lake View", null, "Kandy", UUID.randomUUID(), null),
                ParcelSize.SMALL, 1000, null, new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()),
                NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(parcel, "id", PARCEL_ID);
        ReflectionTestUtils.setField(parcel, "assignedCourierId", COURIER_ID);
        ReflectionTestUtils.setField(parcel, "assignedAt", NOW.minusSeconds(1800));
        when(parcels.findByIdForUpdate(PARCEL_ID)).thenReturn(Optional.of(parcel));
    }

    @Test
    void pickupLocksParcelThenWritesHistoryThenOutboxThenPublishes() {
        ParcelStatusChanged event = service.changeStatus(PARCEL_ID,
                new StatusChange(ParcelStatus.PICKED_UP, null, "Collected at gate"), Actor.courier(COURIER_ID));

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(event.data().previousStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(event.data().newStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(event.data().sequence()).isEqualTo(2);
        assertThat(event.occurredAt()).isEqualTo(NOW);

        InOrder order = inOrder(parcels, history, outbox, publisher);
        order.verify(parcels).findByIdForUpdate(PARCEL_ID);
        order.verify(history).save(any());
        order.verify(outbox).save(any());
        order.verify(publisher).publishEvent(event);
    }

    @Test
    void historyKeepsTheNoteAndActorWhileTheOutboxPayloadLeavesThemOut() {
        service.changeStatus(PARCEL_ID, new StatusChange(ParcelStatus.PICKED_UP, null, "Call Nimal at +94771234567"),
                Actor.courier(COURIER_ID));

        ArgumentCaptor<ParcelStatusHistoryEntry> entry = ArgumentCaptor.forClass(ParcelStatusHistoryEntry.class);
        verify(history).save(entry.capture());
        assertThat(entry.getValue().getNote()).isEqualTo("Call Nimal at +94771234567");
        assertThat(entry.getValue().getActorUserId()).isEqualTo(COURIER_ID);
        assertThat(entry.getValue().getActorRole()).isEqualTo(ActorRole.COURIER);
        assertThat(entry.getValue().getSequence()).isEqualTo(2);

        ArgumentCaptor<OutboxEvent> row = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(row.capture());
        assertThat(row.getValue().getEventId()).isEqualTo(entry.getValue().getEventId());
        assertThat(row.getValue().getSequence()).isEqualTo(2);
        assertThat(row.getValue().getPayload())
                .contains("\"newStatus\":\"PICKED_UP\"")
                .doesNotContain("Nimal", "+9477", "Temple", "Kumari", "Colombo", "Kandy", COURIER_ID.toString());
    }

    @Test
    void failureReasonReachesHistoryAndEvent() {
        service.changeStatus(PARCEL_ID, new StatusChange(ParcelStatus.PICKED_UP, null, null), Actor.courier(COURIER_ID));

        ParcelStatusChanged event = service.changeStatus(PARCEL_ID,
                new StatusChange(ParcelStatus.FAILED, FailureReason.DAMAGED, null), Actor.courier(COURIER_ID));

        assertThat(event.data().failureReasonCode()).isEqualTo(FailureReason.DAMAGED);
        assertThat(event.data().sequence()).isEqualTo(3);
    }

    @Test
    void deliveredChecksProofOnlyForDeliveredAndRejectsWithoutIt() {
        ReflectionTestUtils.setField(parcel, "status", ParcelStatus.OUT_FOR_DELIVERY);
        ReflectionTestUtils.setField(parcel, "lastSequence", 4);
        when(proofs.existsByParcelId(PARCEL_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.changeStatus(PARCEL_ID, new StatusChange(ParcelStatus.DELIVERED, null, null),
                Actor.courier(COURIER_ID))).isInstanceOf(ParcelConflictException.class);
        assertNothingRecorded();

        when(proofs.existsByParcelId(PARCEL_ID)).thenReturn(true);
        assertThat(service.changeStatus(PARCEL_ID, new StatusChange(ParcelStatus.DELIVERED, null, null),
                Actor.courier(COURIER_ID)).data().sequence()).isEqualTo(5);
    }

    @Test
    void proofIsNotQueriedForOtherTransitions() {
        service.changeStatus(PARCEL_ID, new StatusChange(ParcelStatus.PICKED_UP, null, null), Actor.courier(COURIER_ID));

        verifyNoInteractions(proofs);
    }

    @Test
    void unknownParcelAndOtherCourierAreNotFoundAndRecordNothing() {
        when(parcels.findByIdForUpdate(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changeStatus(UUID.randomUUID(),
                new StatusChange(ParcelStatus.PICKED_UP, null, null), Actor.courier(COURIER_ID)))
                .isInstanceOf(ParcelNotFoundException.class);

        when(parcels.findByIdForUpdate(PARCEL_ID)).thenReturn(Optional.of(parcel));
        assertThatThrownBy(() -> service.changeStatus(PARCEL_ID, new StatusChange(ParcelStatus.PICKED_UP, null, null),
                Actor.courier(UUID.randomUUID()))).isInstanceOf(ParcelNotFoundException.class);

        assertNothingRecorded();
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
    }

    @Test
    void illegalTransitionIsAConflictAndRecordsNothing() {
        assertThatThrownBy(() -> service.changeStatus(PARCEL_ID, new StatusChange(ParcelStatus.IN_TRANSIT, null, null),
                Actor.courier(COURIER_ID))).isInstanceOf(ParcelConflictException.class);

        assertNothingRecorded();
    }

    @Test
    void onlyCouriersMayChangeStatus() {
        assertThatThrownBy(() -> service.changeStatus(PARCEL_ID, new StatusChange(ParcelStatus.PICKED_UP, null, null),
                Actor.customer(CUSTOMER_ID))).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(parcels);
    }

    private void assertNothingRecorded() {
        verify(history, never()).save(any());
        verify(outbox, never()).save(any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }
}
