package com.swiftdrop.merchant.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.merchant.api.MerchantOrderDetailView;
import com.swiftdrop.merchant.api.MerchantOrderQuery;

/**
 * Read-side views of the merchant portal (Phase 5): dashboard, order list and order detail. Codes only against
 * {@link MerchantOrderQuery}; every method takes the merchant ID from the caller's principal, never from input.
 */
@Service
public class MerchantPortalReadService {

    /** Orders shown in the dashboard's recent-activity list. */
    public static final int RECENT_ORDERS = 5;

    private final MerchantOrderQuery orders;

    public MerchantPortalReadService(MerchantOrderQuery orders) {
        this.orders = Objects.requireNonNull(orders, "orders must not be null");
    }

    /** Counts per parcel status (every status present, zero included) and the newest orders. */
    public Dashboard dashboard(UUID merchantId) {
        Map<ParcelStatus, Long> counts = orders.countParcelsByStatus(merchantId);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long active = counts.entrySet().stream()
                .filter(entry -> !entry.getKey().isFinal())
                .mapToLong(Map.Entry::getValue)
                .sum();
        List<ParcelSummary> recent = orders
                .findParcelsByMerchant(merchantId, null, new PageRequest(0, RECENT_ORDERS))
                .items();
        return new Dashboard(counts, total, active, recent);
    }

    /** The merchant's orders, newest first, optionally of one status. */
    public PageResult<ParcelSummary> orders(UUID merchantId, ParcelStatus status, PageRequest page) {
        return orders.findParcelsByMerchant(merchantId, status, page);
    }

    /**
     * One of the merchant's orders with its timeline.
     *
     * @throws PortalOrderNotFoundException if the order does not exist or belongs to another merchant
     */
    public MerchantOrderDetailView order(UUID merchantId, UUID orderId) {
        return orders.findOrderDetail(merchantId, orderId).orElseThrow(PortalOrderNotFoundException::new);
    }

    /** Dashboard data before it is shaped for the web layer. */
    public record Dashboard(Map<ParcelStatus, Long> countsByStatus, long totalOrders, long activeOrders,
            List<ParcelSummary> recentOrders) {
    }
}
