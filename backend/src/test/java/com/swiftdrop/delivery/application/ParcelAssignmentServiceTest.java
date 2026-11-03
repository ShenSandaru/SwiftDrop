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

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.pricing.api.ParcelSize;

class ParcelAssignmentServiceTest {

    private static final UUID PARCEL_ID = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final UUID COURIER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID DISPATCHER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final Instant NOW = Instant.parse("2026-11-03T10:15:30Z");

    private ParcelRepository parcels;
    private ParcelAssignmentService service;
    private Parcel parcel;

    @BeforeEach
    void setUp() {
        parcels = mock(ParcelRepository.class);
        service = new ParcelAssignmentService(parcels, Clock.fixed(NOW, ZoneOffset.UTC));
        ParcelAddress address = new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo",
                UUID.randomUUID(), null);
        parcel = Parcel.forCustomer("SD7K3M9Q2XWB", UUID.randomUUID(), address, address, ParcelSize.SMALL, 1000, null,
                new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), NOW.minusSeconds(60));
        when(parcels.findByIdForUpdate(PARCEL_ID)).thenReturn(Optional.of(parcel));
    }

    @Test
    void assignsUnderTheParcelLock() {
        service.assignCourier(PARCEL_ID, COURIER_ID, DISPATCHER_ID);

        verify(parcels).findByIdForUpdate(PARCEL_ID);
        assertThat(parcel.getAssignedCourierId()).isEqualTo(COURIER_ID);
        assertThat(parcel.getAssignedAt()).isEqualTo(NOW);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
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
    void allIdsAreRequired() {
        assertThatThrownBy(() -> service.assignCourier(PARCEL_ID, null, DISPATCHER_ID))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.assignCourier(PARCEL_ID, COURIER_ID, null))
                .isInstanceOf(NullPointerException.class);
    }
}
