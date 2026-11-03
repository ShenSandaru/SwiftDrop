package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.application.CourierParcelService.CourierParcel;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.pricing.api.ParcelSize;

class CourierParcelServiceTest {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final UUID COURIER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    private final ParcelRepository parcels = mock(ParcelRepository.class);
    private final DeliveryProofRepository proofs = mock(DeliveryProofRepository.class);

    @Test
    void todayStartsAtMidnightInColomboNotUtc() {
        // 01:00 on 4 Nov in Colombo is still 3 Nov in UTC
        CourierParcelService service = at("2026-11-03T19:30:00Z");

        assertThat(service.startOfToday()).isEqualTo(Instant.parse("2026-11-03T18:30:00Z"));
    }

    @Test
    void justBeforeColomboMidnightBelongsToThePreviousDay() {
        CourierParcelService service = at("2026-11-03T18:29:59Z");

        assertThat(service.startOfToday()).isEqualTo(Instant.parse("2026-11-02T18:30:00Z"));
    }

    @Test
    void listAsksForUnfinishedOrFinishedTodayAndMarksProofAndNextStatuses() {
        CourierParcelService service = at("2026-11-03T10:00:00Z");
        Parcel outForDelivery = parcel(ParcelStatus.OUT_FOR_DELIVERY);
        Parcel delivered = parcel(ParcelStatus.DELIVERED);
        when(parcels.findCourierDay(eq(COURIER_ID), any(), any())).thenReturn(List.of(outForDelivery, delivered));
        when(proofs.findParcelIdsWithProof(any())).thenReturn(Set.of(outForDelivery.getId()));

        List<CourierParcel> today = service.today(COURIER_ID);

        assertThat(today).extracting(CourierParcel::proofCaptured).containsExactly(true, false);
        assertThat(today.get(0).nextStatuses()).containsExactlyInAnyOrder(ParcelStatus.DELIVERED, ParcelStatus.FAILED);
        assertThat(today.get(1).nextStatuses()).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ParcelStatus>> terminal = ArgumentCaptor.forClass(Collection.class);
        verify(parcels).findCourierDay(eq(COURIER_ID), terminal.capture(), eq(Instant.parse("2026-11-02T18:30:00Z")));
        assertThat(terminal.getValue()).containsExactlyInAnyOrder(ParcelStatus.DELIVERED, ParcelStatus.FAILED);
    }

    @Test
    void emptyDaySkipsTheProofQuery() {
        when(parcels.findCourierDay(any(), any(), any())).thenReturn(List.of());

        assertThat(at("2026-11-03T10:00:00Z").today(COURIER_ID)).isEmpty();
        verifyNoInteractions(proofs);
    }

    private CourierParcelService at(String instant) {
        return new CourierParcelService(parcels, proofs, COLOMBO, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private static Parcel parcel(ParcelStatus status) {
        ParcelAddress address = new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo",
                UUID.randomUUID(), null);
        Parcel parcel = Parcel.forCustomer("SD7K3M9Q2XWB", UUID.randomUUID(), address, address, ParcelSize.SMALL, 1000,
                null, new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), Instant.now());
        ReflectionTestUtils.setField(parcel, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(parcel, "status", status);
        return parcel;
    }
}
