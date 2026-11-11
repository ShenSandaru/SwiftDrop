package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelAssignmentStatus;
import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.Staff;
import com.swiftdrop.delivery.domain.ActorRole;
import com.swiftdrop.delivery.domain.HubRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelAssignmentRecord;
import com.swiftdrop.delivery.domain.ParcelAssignmentRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryEntry;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.pricing.api.ParcelSize;

/** Dispatcher steps and customer cancellation (ADR 0009 §3, §6). */
class ParcelHubServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID DISPATCHER_ID = UUID.randomUUID();

    private final ParcelRepository parcels = mock(ParcelRepository.class);
    private final HubRepository hubs = mock(HubRepository.class);
    private final ParcelAssignmentRepository assignments = mock(ParcelAssignmentRepository.class);
    private final ParcelStatusHistoryRepository history = mock(ParcelStatusHistoryRepository.class);
    private ParcelHubService service;
    private Parcel parcel;

    @BeforeEach
    void setUp() {
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        when(history.save(any())).thenAnswer(call -> call.getArgument(0));
        when(outbox.save(any())).thenAnswer(call -> call.getArgument(0));
        ParcelEventRecorder recorder = new ParcelEventRecorder(history, outbox, new ParcelEventSerializer(),
                mock(ApplicationEventPublisher.class));
        service = new ParcelHubService(parcels, hubs, assignments, recorder, DeliveryPolicy.defaults(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        ParcelAddress address = new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo",
                UUID.randomUUID(), null);
        parcel = Parcel.forCustomer("SD7K3M9Q2XWB", CUSTOMER_ID, address, address, ParcelSize.SMALL, 1000, null,
                new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), NOW);
        ReflectionTestUtils.setField(parcel, "id", UUID.randomUUID());
        when(parcels.findByIdForUpdate(parcel.getId())).thenReturn(Optional.of(parcel));
    }

    @Test
    void ownerCancelsAndTheActiveTaskIsCancelled() {
        UUID courier = UUID.randomUUID();
        parcel.assignPickupCourier(courier, NOW);
        ParcelAssignmentRecord task = new ParcelAssignmentRecord(parcel.getId(), courier, ParcelAssignmentType.PICKUP,
                DISPATCHER_ID, NOW);
        when(assignments.findFirstByParcelIdAndStatus(parcel.getId(), ParcelAssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(task));

        service.cancelForCustomer(parcel.getId(), CUSTOMER_ID);

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CANCELLED);
        assertThat(task.getStatus()).isEqualTo(ParcelAssignmentStatus.CANCELLED);
        ArgumentCaptor<ParcelStatusHistoryEntry> entry = ArgumentCaptor.forClass(ParcelStatusHistoryEntry.class);
        verify(history).save(entry.capture());
        assertThat(entry.getValue().getActorRole()).isEqualTo(ActorRole.CUSTOMER);
    }

    @Test
    void anotherCustomersParcelIsNotFound() {
        assertThatThrownBy(() -> service.cancelForCustomer(parcel.getId(), UUID.randomUUID()))
                .isInstanceOf(ParcelNotFoundException.class);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
        verify(history, never()).save(any());
    }

    @Test
    void staffStepsAreRecordedWithTheirRole() {
        service.cancel(parcel.getId(), Staff.admin(DISPATCHER_ID));

        ArgumentCaptor<ParcelStatusHistoryEntry> entry = ArgumentCaptor.forClass(ParcelStatusHistoryEntry.class);
        verify(history).save(entry.capture());
        assertThat(entry.getValue().getActorRole()).isEqualTo(ActorRole.ADMIN);
        assertThat(entry.getValue().getActorUserId()).isEqualTo(DISPATCHER_ID);
    }

    @Test
    void unknownHubAndUnknownParcelAreRefused() {
        UUID hub = UUID.randomUUID();
        when(hubs.findById(hub)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.scanAtHub(parcel.getId(), hub, Staff.dispatcher(DISPATCHER_ID)))
                .isInstanceOf(ParcelConflictException.class).hasMessage("Unknown hub");
        assertThatThrownBy(() -> service.sendToNextHub(UUID.randomUUID(), Staff.dispatcher(DISPATCHER_ID)))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void failingNeedsAReasonAndAPickedUpParcel() {
        assertThatThrownBy(() -> service.markFailed(parcel.getId(), FailureReason.DAMAGED, null,
                Staff.dispatcher(DISPATCHER_ID))).isInstanceOf(ParcelConflictException.class);
        verify(history, never()).save(any());
    }
}
