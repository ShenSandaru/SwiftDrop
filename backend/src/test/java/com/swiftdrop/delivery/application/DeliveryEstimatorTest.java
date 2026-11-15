package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.HubRoute;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.pricing.api.ParcelSize;

class DeliveryEstimatorTest {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    /** 14 November 23:30 in Colombo, still 14 November 18:00 in UTC. */
    private static final Instant BOOKED = Instant.parse("2026-11-14T18:00:00Z");
    private static final UUID PICKUP_ZONE = UUID.randomUUID();
    private static final UUID DROPOFF_ZONE = UUID.randomUUID();
    private static final UUID BADULLA = UUID.randomUUID();
    private static final UUID COLOMBO_HUB = UUID.randomUUID();
    private static final UUID MONARAGALA = UUID.randomUUID();

    private final HubNetwork network = mock(HubNetwork.class);
    private final DeliveryEstimator estimator = new DeliveryEstimator(network, 1, 2, 3, COLOMBO);

    @Test
    void beforePickupCountsFromTheBookingOverTheRouteTheParcelWouldGet() {
        when(network.routeFor(PICKUP_ZONE, DROPOFF_ZONE)).thenReturn(new HubRoute(BADULLA, COLOMBO_HUB, MONARAGALA));

        assertThat(estimator.estimate(parcel(), null)).contains(LocalDate.parse("2026-11-17"));
    }

    @Test
    void aDirectRouteIsFasterThanOneThroughTheCentralHub() {
        when(network.routeFor(PICKUP_ZONE, DROPOFF_ZONE)).thenReturn(new HubRoute(BADULLA, null, MONARAGALA));

        assertThat(estimator.estimate(parcel(), null)).contains(LocalDate.parse("2026-11-16"));
    }

    @Test
    void aParcelWithinOneBranchIsFastest() {
        when(network.routeFor(PICKUP_ZONE, DROPOFF_ZONE)).thenReturn(new HubRoute(BADULLA, null, BADULLA));

        assertThat(estimator.estimate(parcel(), null)).contains(LocalDate.parse("2026-11-15"));
    }

    @Test
    void afterPickupCountsFromThePickupOverTheStoredRoute() {
        Parcel parcel = routed(ParcelStatus.IN_TRANSIT, BADULLA, COLOMBO_HUB, MONARAGALA);
        Instant pickedUp = Instant.parse("2026-11-16T04:00:00Z");

        assertThat(estimator.estimate(parcel, pickedUp)).contains(LocalDate.parse("2026-11-19"));
        verifyNoInteractions(network);
    }

    @Test
    void usesTheBusinessDayNotTheUtcDay() {
        DeliveryEstimator utc = new DeliveryEstimator(network, 1, 2, 3, ZoneId.of("UTC"));
        Parcel parcel = routed(ParcelStatus.AT_HUB, BADULLA, null, MONARAGALA);
        // 14 November 20:00 UTC is already 15 November in Colombo
        Instant pickedUp = Instant.parse("2026-11-14T20:00:00Z");

        assertThat(estimator.estimate(parcel, pickedUp)).contains(LocalDate.parse("2026-11-17"));
        assertThat(utc.estimate(parcel, pickedUp)).contains(LocalDate.parse("2026-11-16"));
    }

    @Test
    void finishedAndReturningParcelsHaveNoEstimate() {
        for (ParcelStatus status : new ParcelStatus[] {ParcelStatus.DELIVERED, ParcelStatus.RETURNED,
                ParcelStatus.CANCELLED, ParcelStatus.FAILED, ParcelStatus.RETURNING}) {
            assertThat(estimator.estimate(routed(status, BADULLA, null, MONARAGALA), BOOKED)).as("%s", status).isEmpty();
        }
    }

    @Test
    void aParcelWithoutAKnownRouteHasNoEstimate() {
        when(network.routeFor(PICKUP_ZONE, DROPOFF_ZONE)).thenThrow(new ParcelConflictException("not served"));
        assertThat(estimator.estimate(parcel(), null)).isEmpty();

        // Picked up before the hub network existed (ADR 0006)
        assertThat(estimator.estimate(routed(ParcelStatus.IN_TRANSIT, null, null, null), BOOKED)).isEmpty();
    }

    @Test
    void longerRoutesMayNotBeConfiguredFaster() {
        assertThatThrownBy(() -> new DeliveryEstimator(network, 2, 1, 3, COLOMBO)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DeliveryEstimator(network, 1, 3, 2, COLOMBO)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DeliveryEstimator(network, -1, 2, 3, COLOMBO)).isInstanceOf(IllegalStateException.class);
    }

    private static Parcel parcel() {
        return Parcel.forMerchant("SD7K3M9Q2XWB", UUID.randomUUID(), "ORD-1",
                new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Badulla", PICKUP_ZONE, "90000"),
                new ParcelAddress("Kumari Silva", "+94711234567", "4 Lake View", null, "Monaragala", DROPOFF_ZONE,
                        "91000"),
                ParcelSize.SMALL, 1000, "Books", new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()),
                BOOKED);
    }

    private static Parcel routed(ParcelStatus status, UUID origin, UUID via, UUID destination) {
        Parcel parcel = parcel();
        ReflectionTestUtils.setField(parcel, "status", status);
        ReflectionTestUtils.setField(parcel, "originHubId", origin);
        ReflectionTestUtils.setField(parcel, "viaHubId", via);
        ReflectionTestUtils.setField(parcel, "destinationHubId", destination);
        return parcel;
    }
}
