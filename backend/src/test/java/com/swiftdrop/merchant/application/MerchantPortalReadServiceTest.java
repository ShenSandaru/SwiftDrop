package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.merchant.api.MerchantOrderQuery;
import com.swiftdrop.pricing.api.ParcelSize;

class MerchantPortalReadServiceTest {

    private final MerchantOrderQuery orders = mock(MerchantOrderQuery.class);
    private final MerchantPortalReadService service = new MerchantPortalReadService(orders);
    private final UUID merchantId = UUID.randomUUID();

    @Test
    void dashboardTotalsAllStatusesAndCountsOnlyNonFinalAsActive() {
        Map<ParcelStatus, Long> counts = new EnumMap<>(ParcelStatus.class);
        for (ParcelStatus s : ParcelStatus.values()) {
            counts.put(s, 0L);
        }
        counts.put(ParcelStatus.CREATED, 2L);
        counts.put(ParcelStatus.OUT_FOR_DELIVERY, 1L);
        counts.put(ParcelStatus.DELIVERED, 4L);
        counts.put(ParcelStatus.CANCELLED, 1L);
        ParcelSummary recent = new ParcelSummary(UUID.randomUUID(), "SD1", ParcelStatus.CREATED, ParcelSize.SMALL,
                300, ParcelSource.MERCHANT, null, merchantId, "ORD-1", null, Instant.now());
        when(orders.countParcelsByStatus(merchantId)).thenReturn(counts);
        when(orders.findParcelsByMerchant(merchantId, null, new PageRequest(0, MerchantPortalReadService.RECENT_ORDERS)))
                .thenReturn(PageResult.of(List.of(recent), new PageRequest(0, 5), 1));

        MerchantPortalReadService.Dashboard dashboard = service.dashboard(merchantId);

        assertThat(dashboard.totalOrders()).isEqualTo(8);
        assertThat(dashboard.activeOrders()).isEqualTo(3);
        assertThat(dashboard.recentOrders()).containsExactly(recent);
    }

    @Test
    void unknownOrAnotherMerchantsOrderThrowsNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orders.findOrderDetail(merchantId, orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.order(merchantId, orderId)).isInstanceOf(PortalOrderNotFoundException.class);
    }
}
