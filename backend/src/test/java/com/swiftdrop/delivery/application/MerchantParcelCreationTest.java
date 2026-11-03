package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.pricing.api.ParcelSize;

class MerchantParcelCreationTest {

    private static final UUID MERCHANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final Instant NOW = Instant.parse("2026-11-03T10:15:30Z");

    private ParcelBookingService booking;
    private ParcelRepository parcels;
    private MerchantParcelCreation creation;

    @BeforeEach
    void setUp() {
        booking = mock(ParcelBookingService.class);
        parcels = mock(ParcelRepository.class);
        creation = new MerchantParcelCreation(booking, parcels);
    }

    @Test
    void newOrderIsBookedAndReportedAsNewlyCreated() {
        Parcel parcel = merchantParcel();
        when(parcels.findByMerchantIdAndMerchantOrderReference(MERCHANT_ID, "ORD-1")).thenReturn(Optional.empty());
        when(booking.bookForMerchant(any())).thenReturn(parcel);

        CreatedParcel created = creation.createMerchantParcel(order(" ORD-1 "));

        assertThat(created.newlyCreated()).isTrue();
        assertThat(created.parcelId()).isEqualTo(parcel.getId());
        assertThat(created.feeAmount()).isEqualByComparingTo("650.00");
    }

    @Test
    void repeatedOrderReturnsExistingParcelWithoutBooking() {
        Parcel existing = merchantParcel();
        when(parcels.findByMerchantIdAndMerchantOrderReference(MERCHANT_ID, "ORD-1")).thenReturn(Optional.of(existing));

        CreatedParcel created = creation.createMerchantParcel(order("ORD-1"));

        assertThat(created.newlyCreated()).isFalse();
        assertThat(created.trackingNumber()).isEqualTo(existing.getTrackingNumber());
        verifyNoInteractions(booking);
    }

    @Test
    void lostRaceReturnsTheWinnersParcel() {
        Parcel winner = merchantParcel();
        when(parcels.findByMerchantIdAndMerchantOrderReference(MERCHANT_ID, "ORD-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(booking.bookForMerchant(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        CreatedParcel created = creation.createMerchantParcel(order("ORD-1"));

        assertThat(created.newlyCreated()).isFalse();
        assertThat(created.parcelId()).isEqualTo(winner.getId());
    }

    @Test
    void otherConstraintViolationsAreRethrown() {
        when(parcels.findByMerchantIdAndMerchantOrderReference(any(), any())).thenReturn(Optional.empty());
        when(booking.bookForMerchant(any())).thenThrow(new DataIntegrityViolationException("something else"));

        assertThatThrownBy(() -> creation.createMerchantParcel(order("ORD-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void merchantAndReferenceAreRequired() {
        assertThatThrownBy(() -> creation.createMerchantParcel(new NewMerchantParcel(null, "ORD-1", contact(), contact(),
                ParcelSize.SMALL, 1000, null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> creation.createMerchantParcel(order(" "))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(booking, parcels);
    }

    @Test
    void bookingReceivesTheOrderUnchanged() {
        when(parcels.findByMerchantIdAndMerchantOrderReference(any(), any())).thenReturn(Optional.empty());
        when(booking.bookForMerchant(any())).thenReturn(merchantParcel());
        NewMerchantParcel order = order("ORD-1");

        creation.createMerchantParcel(order);

        verify(booking).bookForMerchant(order);
    }

    private static NewMerchantParcel order(String reference) {
        return new NewMerchantParcel(MERCHANT_ID, reference, contact(), contact(), ParcelSize.SMALL, 1000, null);
    }

    private static DeliveryContact contact() {
        return new DeliveryContact("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo", UUID.randomUUID(),
                null);
    }

    private static Parcel merchantParcel() {
        ParcelAddress address = new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo",
                UUID.randomUUID(), null);
        Parcel parcel = Parcel.forMerchant("SD7K3M9Q2XWB", MERCHANT_ID, "ORD-1", address, address, ParcelSize.SMALL,
                1000, null, new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), NOW);
        ReflectionTestUtils.setField(parcel, "id", UUID.randomUUID());
        return parcel;
    }
}
