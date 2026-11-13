package com.swiftdrop.merchant.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.merchant.api.WebhookDeliveryQuery;
import com.swiftdrop.merchant.api.WebhookDeliveryView;
import com.swiftdrop.merchant.domain.MerchantWebhookAttempt;
import com.swiftdrop.merchant.domain.MerchantWebhookAttemptRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookDelivery;
import com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpoint;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookRedelivery;
import com.swiftdrop.merchant.domain.MerchantWebhookRedeliveryRepository;
import com.swiftdrop.merchant.domain.WebhookDeliveryStatus;

/**
 * Webhook delivery history (SD-412, ADR 0004 §7.5). Merchant callers always pass their own merchant ID; only
 * admin callers pass {@code null} to see all merchants. Pages are newest first with the ID as tie-breaker.
 * Nothing here exposes payloads, headers, signatures or secrets.
 */
@Service
@Transactional(readOnly = true)
public class WebhookDeliveryHistoryService implements WebhookDeliveryQuery {

    private final MerchantWebhookDeliveryRepository deliveries;
    private final MerchantWebhookAttemptRepository attempts;
    private final MerchantWebhookRedeliveryRepository redeliveries;
    private final MerchantWebhookEndpointRepository endpoints;

    public WebhookDeliveryHistoryService(MerchantWebhookDeliveryRepository deliveries,
            MerchantWebhookAttemptRepository attempts, MerchantWebhookRedeliveryRepository redeliveries,
            MerchantWebhookEndpointRepository endpoints) {
        this.deliveries = deliveries;
        this.attempts = attempts;
        this.redeliveries = redeliveries;
        this.endpoints = endpoints;
    }

    @Override
    public PageResult<WebhookDeliveryView> findDeliveriesByMerchant(UUID merchantId, UUID endpointId, PageRequest page) {
        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId is required");
        }
        return history(merchantId, endpointId, null, page);
    }

    /**
     * @param merchantId the merchant to list, or {@code null} for every merchant (admin only)
     * @param endpointId optional endpoint filter
     * @param status     optional status filter
     */
    public PageResult<WebhookDeliveryView> history(UUID merchantId, UUID endpointId, WebhookDeliveryStatus status,
            PageRequest page) {
        Page<MerchantWebhookDelivery> found = deliveries.findHistory(merchantId, endpointId, status,
                org.springframework.data.domain.PageRequest.of(page.page(), page.size()));
        Map<UUID, String> urls = urls(found.getContent().stream().map(MerchantWebhookDelivery::getEndpointId)
                .collect(Collectors.toSet()));
        List<WebhookDeliveryView> views = found.getContent().stream()
                .map(delivery -> view(delivery, urls.get(delivery.getEndpointId())))
                .toList();
        return PageResult.of(views, page, found.getTotalElements());
    }

    /**
     * @param merchantId the caller's merchant, or {@code null} for an admin
     * @throws WebhookDeliveryNotFoundException if missing or another merchant's
     */
    public WebhookDeliveryDetail detail(UUID deliveryId, UUID merchantId) {
        MerchantWebhookDelivery delivery = (merchantId == null
                ? deliveries.findById(deliveryId)
                : deliveries.findByIdAndMerchantId(deliveryId, merchantId))
                .orElseThrow(WebhookDeliveryNotFoundException::new);
        List<MerchantWebhookAttempt> recorded = attempts.findByDeliveryIdOrderByAttemptNumberAsc(delivery.getId());
        List<WebhookDeliveryDetail.Series> series = new ArrayList<>();
        series.add(new WebhookDeliveryDetail.Series(0, null, null, null, null, attemptsOf(recorded, 0)));
        for (MerchantWebhookRedelivery redelivery : redeliveries.findByDeliveryIdOrderBySeriesAsc(delivery.getId())) {
            series.add(new WebhookDeliveryDetail.Series(redelivery.getSeries(), redelivery.getRequestedByUserId(),
                    redelivery.getRequestedByRole().name(), redelivery.getRequestedAt(),
                    redelivery.getPreviousStatus().name(), attemptsOf(recorded, redelivery.getSeries())));
        }
        return new WebhookDeliveryDetail(delivery.getId(), delivery.getMerchantId(), delivery.getEndpointId(),
                urls(Set.of(delivery.getEndpointId())).get(delivery.getEndpointId()), delivery.getEventId(),
                delivery.getParcelId(), delivery.getSequence(), delivery.getEventType(), delivery.getStatus().name(),
                delivery.getAttempts(), delivery.getLastHttpStatus(),
                delivery.getLastOutcome() == null ? null : delivery.getLastOutcome().name(),
                delivery.getStatus() == WebhookDeliveryStatus.PENDING ? delivery.getNextAttemptAt() : null,
                delivery.getCreatedAt(), delivery.getCompletedAt(), delivery.getManualRedeliveries(),
                MerchantWebhookDelivery.MAX_MANUAL_REDELIVERIES - delivery.getManualRedeliveries(),
                delivery.isRedeliverable(), series);
    }

    private static List<WebhookDeliveryDetail.Attempt> attemptsOf(List<MerchantWebhookAttempt> recorded, int series) {
        return recorded.stream()
                .filter(attempt -> attempt.getSeries() == series)
                .map(attempt -> new WebhookDeliveryDetail.Attempt(attempt.getAttemptNumber(), attempt.getAttemptedAt(),
                        attempt.getOutcome().name(), attempt.getHttpStatus(), attempt.getDurationMs()))
                .toList();
    }

    private static WebhookDeliveryView view(MerchantWebhookDelivery delivery, String url) {
        return new WebhookDeliveryView(delivery.getId(), delivery.getEndpointId(), url == null ? "" : url,
                delivery.getEventType(), delivery.getLastHttpStatus(), delivery.getStatus().name(),
                delivery.getAttempts(),
                delivery.getStatus() == WebhookDeliveryStatus.PENDING ? delivery.getNextAttemptAt() : null,
                delivery.getCreatedAt(), delivery.getCompletedAt());
    }

    /** Endpoint URLs, including deleted endpoints (their records stay in the history). */
    private Map<UUID, String> urls(Set<UUID> endpointIds) {
        return endpoints.findAllById(endpointIds).stream()
                .collect(Collectors.toMap(MerchantWebhookEndpoint::getId, MerchantWebhookEndpoint::getUrl,
                        (first, second) -> first, java.util.HashMap::new));
    }
}
