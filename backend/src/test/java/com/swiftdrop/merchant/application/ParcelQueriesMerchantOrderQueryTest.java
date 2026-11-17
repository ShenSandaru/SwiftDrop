package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.delivery.api.PublicStatusEvent;
import com.swiftdrop.delivery.api.PublicTrackingView;
import com.swiftdrop.merchant.api.MerchantOrderDetailView;
import com.swiftdrop.pricing.api.ParcelSize;

class ParcelQueriesMerchantOrderQueryTest {

    private final ParcelQueries parcels = mock(ParcelQueries.class);
    private final ParcelQueriesMerchantOrderQuery query = new ParcelQueriesMerchantOrderQuery(parcels);
    private final UUID merchantId = UUID.randomUUID();

    private static ParcelSummary parcel(UUID id, String tracking, UUID merchantId) {
        return new ParcelSummary(id, tracking, ParcelStatus.AT_HUB, ParcelSize.SMALL, 500, ParcelSource.MERCHANT,
                null, merchantId, "ORD-" + tracking, UUID.randomUUID(), Instant.parse("2026-11-14T10:00:00Z"));
    }

    @Test
    void countsEveryStatusFromTheMerchantScopedQuery() {
        when(parcels.findParcelsByMerchant(eq(merchantId), any(ParcelStatus.class), any(PageRequest.class)))
                .thenAnswer(call -> PageResult.of(List.of(), call.getArgument(2),
                        call.getArgument(1) == ParcelStatus.DELIVERED ? 7 : 0));

        Map<ParcelStatus, Long> counts = query.countParcelsByStatus(merchantId);

        assertThat(counts).hasSize(ParcelStatus.values().length);
        assertThat(counts.get(ParcelStatus.DELIVERED)).isEqualTo(7L);
        assertThat(counts.get(ParcelStatus.CREATED)).isZero();
    }

    @Test
    void findsOrderDetailOnALaterPageWithTimeline() {
        UUID wanted = UUID.randomUUID();
        List<ParcelSummary> firstPage = java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> parcel(UUID.randomUUID(), "T" + i, merchantId)).toList();
        when(parcels.findParcelsByMerchant(merchantId, null, new PageRequest(0, 100)))
                .thenReturn(PageResult.of(firstPage, new PageRequest(0, 100), 101));
        when(parcels.findParcelsByMerchant(merchantId, null, new PageRequest(1, 100)))
                .thenReturn(PageResult.of(List.of(parcel(wanted, "SDWANTED", merchantId)), new PageRequest(1, 100), 101));
        when(parcels.findPublicTrackingView("SDWANTED")).thenReturn(Optional.of(new PublicTrackingView("SDWANTED",
                ParcelStatus.AT_HUB, "Colombo", "Kandy",
                List.of(new PublicStatusEvent(ParcelStatus.CREATED, Instant.parse("2026-11-14T10:00:00Z")),
                        new PublicStatusEvent(ParcelStatus.AT_HUB, Instant.parse("2026-11-14T12:00:00Z"),
                                "Colombo Hub", null)))));

        Optional<MerchantOrderDetailView> detail = query.findOrderDetail(merchantId, wanted);

        assertThat(detail).isPresent();
        assertThat(detail.get().trackingNumber()).isEqualTo("SDWANTED");
        assertThat(detail.get().pickupCity()).isEqualTo("Colombo");
        assertThat(detail.get().courierAssigned()).isTrue();
        assertThat(detail.get().history()).extracting(MerchantOrderDetailView.StatusEvent::status)
                .containsExactly(ParcelStatus.CREATED, ParcelStatus.AT_HUB);
        assertThat(detail.get().history().get(1).hubName()).isEqualTo("Colombo Hub");
    }

    @Test
    void anotherMerchantsOrderIsNotFound() {
        when(parcels.findParcelsByMerchant(eq(merchantId), isNull(), any(PageRequest.class)))
                .thenReturn(PageResult.of(List.of(parcel(UUID.randomUUID(), "MINE", merchantId)),
                        new PageRequest(0, 100), 1));

        assertThat(query.findOrderDetail(merchantId, UUID.randomUUID())).isEmpty();
        verify(parcels, never()).findPublicTrackingView(any());
    }

    @Test
    void blankReferenceIsNotLookedUp() {
        assertThat(query.findByMerchantReference(merchantId, "  ")).isEmpty();
        verify(parcels, never()).findByMerchantReference(any(), any());
    }
}
