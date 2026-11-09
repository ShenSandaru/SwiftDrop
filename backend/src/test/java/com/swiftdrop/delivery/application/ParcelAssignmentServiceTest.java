package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

import com.swiftdrop.delivery.api.ParcelAssignmentStatus;
import com.swiftdrop.delivery.api.ParcelAssignmentSummary;
import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelAssignmentRecord;
import com.swiftdrop.delivery.domain.ParcelAssignmentRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.pricing.api.ParcelSize;

class ParcelAssignmentServiceTest {

    private static final UUID PARCEL_ID = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final UUID COURIER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID DISPATCHER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final Instant NOW = Instant.parse("2026-11-03T10:15:30Z");

    private ParcelRepository parcels;
    private ParcelAssignmentRepository assignments;
    private ParcelAssignmentService service;
    private Parcel parcel;

    @BeforeEach
    void setUp() {
        parcels = mock(ParcelRepository.class);
        assignments = mock(ParcelAssignmentRepository.class);
        service = new ParcelAssignmentService(parcels, assignments, Clock.fixed(NOW, ZoneOffset.UTC));
        when(assignments.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(parcels.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        ParcelAddress address = new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo",
                UUID.randomUUID(), null);
        parcel = Parcel.forCustomer("SD7K3M9Q2XWB", UUID.randomUUID(), address, address, ParcelSize.SMALL, 1000, null,
                new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), NOW.minusSeconds(60));
        when(parcels.findById(PARCEL_ID)).thenReturn(Optional.of(parcel));
    }

    @Test
    void assignsUnderOptimisticLocking() {
        service.assignCourier(PARCEL_ID, COURIER_ID, DISPATCHER_ID);

        verify(parcels).findById(PARCEL_ID);
        verify(parcels).saveAndFlush(parcel);
        assertThat(parcel.getAssignedCourierId()).isEqualTo(COURIER_ID);
        assertThat(parcel.getAssignedAt()).isEqualTo(NOW);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
        verify(assignments).saveAndFlush(org.mockito.ArgumentMatchers.argThat(
                row -> row.toSummary().type() == ParcelAssignmentType.PICKUP
                        && row.toSummary().status() == ParcelAssignmentStatus.ACTIVE));
    }

    @Test
    void unknownParcelIsNotFound() {
        when(parcels.findByIdForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignCourier(UUID.randomUUID(), COURIER_ID, DISPATCHER_ID))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void parcelPastCreatedIsAConflict() {
        parcel.assignCourier(COURIER_ID, NOW);
        parcel.changeStatus(ParcelStatus.PICKED_UP, COURIER_ID, null, false, NOW);

        assertThatThrownBy(() -> service.assignCourier(PARCEL_ID, UUID.randomUUID(), DISPATCHER_ID))
                .isInstanceOf(ParcelConflictException.class);
    }

    @Test
    void assigningParcelPastCreatedIsAConflictRegardlessOfType() {
        parcel.assignCourier(COURIER_ID, NOW);
        parcel.changeStatus(ParcelStatus.PICKED_UP, COURIER_ID, null, false, NOW);

        assertThatThrownBy(() -> service.assignCourier(
                PARCEL_ID, UUID.randomUUID(), DISPATCHER_ID, ParcelAssignmentType.DELIVERY))
                .isInstanceOf(ParcelConflictException.class);
    }

    @Test
    void assigningSameCourierReturnsExistingAssignmentIdempotently() {
        parcel.assignCourier(COURIER_ID, NOW.minusSeconds(30));
        ParcelAssignmentRecord previous = new ParcelAssignmentRecord(
                PARCEL_ID, COURIER_ID, ParcelAssignmentType.PICKUP, DISPATCHER_ID, NOW.minusSeconds(30));
        when(assignments.findFirstByParcelIdAndStatus(PARCEL_ID, ParcelAssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(previous));

        ParcelAssignmentSummary summary = service.assignCourier(PARCEL_ID, COURIER_ID, DISPATCHER_ID,
                ParcelAssignmentType.PICKUP);

        assertThat(summary.id()).isEqualTo(previous.toSummary().id());
        assertThat(summary.courierId()).isEqualTo(COURIER_ID);
        assertThat(previous.getStatus()).isEqualTo(ParcelAssignmentStatus.ACTIVE);
    }

    @Test
    void courierCanHaveMultipleActiveParcelAssignments() {
        UUID parcel2Id = UUID.fromString("5a0e0000-0000-0000-0000-000000000002");
        ParcelAddress address = new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo",
                UUID.randomUUID(), null);
        Parcel parcel2 = Parcel.forCustomer("SD7K3M9Q2XWC", UUID.randomUUID(), address, address, ParcelSize.SMALL, 1000, null,
                new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), NOW.minusSeconds(60));
        when(parcels.findById(parcel2Id)).thenReturn(Optional.of(parcel2));

        ParcelAssignmentSummary first = service.assignCourier(PARCEL_ID, COURIER_ID, DISPATCHER_ID,
                ParcelAssignmentType.PICKUP);
        ParcelAssignmentSummary second = service.assignCourier(parcel2Id, COURIER_ID, DISPATCHER_ID,
                ParcelAssignmentType.PICKUP);

        assertThat(first.courierId()).isEqualTo(COURIER_ID);
        assertThat(second.courierId()).isEqualTo(COURIER_ID);
        assertThat(first.parcelId()).isEqualTo(PARCEL_ID);
        assertThat(second.parcelId()).isEqualTo(parcel2Id);
    }

    @Test
    void reassigningParcelToDifferentCourierMarksPreviousAsReassigned() {
        parcel.assignPickupCourier(COURIER_ID, NOW.minusSeconds(30));
        ParcelAssignmentRecord previous = new ParcelAssignmentRecord(
                PARCEL_ID, COURIER_ID, ParcelAssignmentType.PICKUP, DISPATCHER_ID, NOW.minusSeconds(30));
        when(assignments.findFirstByParcelIdAndStatus(PARCEL_ID, ParcelAssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(previous));
        UUID replacementCourierId = UUID.randomUUID();

        ParcelAssignmentSummary summary = service.assignCourier(
                PARCEL_ID, replacementCourierId, DISPATCHER_ID, ParcelAssignmentType.PICKUP);

        assertThat(summary.courierId()).isEqualTo(replacementCourierId);
        assertThat(summary.status()).isEqualTo(ParcelAssignmentStatus.ACTIVE);
        assertThat(summary.completedAt()).isNull();

        assertThat(previous.getStatus()).isEqualTo(ParcelAssignmentStatus.REASSIGNED);
        assertThat(previous.toSummary().completedAt()).isEqualTo(NOW);
        assertThat(parcel.getAssignedCourierId()).isEqualTo(replacementCourierId);
        assertThat(parcel.getAssignedAt()).isEqualTo(NOW);

        verify(assignments).saveAndFlush(previous);
        verify(parcels).saveAndFlush(parcel);
    }

    @Test
    void reassigningWithMismatchedActiveHistoryThrowsConflict() {
        parcel.assignPickupCourier(COURIER_ID, NOW.minusSeconds(30));
        UUID otherCourierId = UUID.randomUUID();
        ParcelAssignmentRecord previous = new ParcelAssignmentRecord(
                PARCEL_ID, otherCourierId, ParcelAssignmentType.PICKUP, DISPATCHER_ID, NOW.minusSeconds(30));
        when(assignments.findFirstByParcelIdAndStatus(PARCEL_ID, ParcelAssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(previous));

        assertThatThrownBy(() -> service.assignCourier(
                PARCEL_ID, UUID.randomUUID(), DISPATCHER_ID, ParcelAssignmentType.PICKUP))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("The current parcel assignment does not match its history");
    }

    @Test
    void reassigningWithMissingActiveHistoryThrowsConflict() {
        parcel.assignPickupCourier(COURIER_ID, NOW.minusSeconds(30));
        when(assignments.findFirstByParcelIdAndStatus(PARCEL_ID, ParcelAssignmentStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignCourier(
                PARCEL_ID, UUID.randomUUID(), DISPATCHER_ID, ParcelAssignmentType.PICKUP))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("The current parcel assignment has no active history record");
    }

    @Test
    void allIdsAreRequired() {
        assertThatThrownBy(() -> service.assignCourier(PARCEL_ID, null, DISPATCHER_ID))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.assignCourier(PARCEL_ID, COURIER_ID, null))
                .isInstanceOf(NullPointerException.class);
    }
}
