package com.swiftdrop.merchant.web;

import java.util.List;
import java.util.Map;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.merchant.application.MerchantPortalReadService;

/**
 * {@code GET /api/v1/merchants/me/dashboard}: order counts per parcel status (every status, zero included) and
 * the newest orders.
 */
public record MerchantDashboardResponse(Map<ParcelStatus, Long> countsByStatus, long totalOrders, long activeOrders,
        long deliveredOrders, List<MerchantOrderSummaryResponse> recentOrders) {

    static MerchantDashboardResponse from(MerchantPortalReadService.Dashboard dashboard) {
        return new MerchantDashboardResponse(
                dashboard.countsByStatus(),
                dashboard.totalOrders(),
                dashboard.activeOrders(),
                dashboard.countsByStatus().getOrDefault(ParcelStatus.DELIVERED, 0L),
                dashboard.recentOrders().stream().map(MerchantOrderSummaryResponse::from).toList());
    }
}
