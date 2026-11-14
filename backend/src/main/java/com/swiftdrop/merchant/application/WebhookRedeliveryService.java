package com.swiftdrop.merchant.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookDelivery;
import com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookRedelivery;
import com.swiftdrop.merchant.domain.MerchantWebhookRedeliveryRepository;
import com.swiftdrop.merchant.domain.WebhookDeliveryStatus;

/**
 * Manual webhook redelivery (SD-412, ADR 0004 §7.5). A {@code FAILED} or {@code SECRET_UNAVAILABLE} record starts a
 * new attempt series with the normal per-series limit; at most {@value MerchantWebhookDelivery#MAX_MANUAL_REDELIVERIES}
 * times per record. The record is locked, so concurrent requests start one series. The same body and event ID are
 * sent again by the sender, signed with the endpoint's current secret and a fresh timestamp. Every redelivery is
 * recorded with who requested it, in which role and when.
 */
@Service
public class WebhookRedeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookRedeliveryService.class);

    private final MerchantWebhookDeliveryRepository deliveries;
    private final MerchantWebhookRedeliveryRepository redeliveries;
    private final MerchantWebhookEndpointRepository endpoints;
    private final MerchantRepository merchants;
    private final Clock clock;

    @Autowired
    public WebhookRedeliveryService(MerchantWebhookDeliveryRepository deliveries,
            MerchantWebhookRedeliveryRepository redeliveries, MerchantWebhookEndpointRepository endpoints,
            MerchantRepository merchants) {
        this(deliveries, redeliveries, endpoints, merchants, Clock.systemUTC());
    }

    WebhookRedeliveryService(MerchantWebhookDeliveryRepository deliveries,
            MerchantWebhookRedeliveryRepository redeliveries, MerchantWebhookEndpointRepository endpoints,
            MerchantRepository merchants, Clock clock) {
        this.deliveries = deliveries;
        this.redeliveries = redeliveries;
        this.endpoints = endpoints;
        this.merchants = merchants;
        this.clock = clock;
    }

    /**
     * @param merchantId the caller's merchant, or {@code null} when an admin requests it
     * @param userId     the requesting user, recorded in the audit row and on the series' attempts
     * @return the new series number, 1 to 3
     * @throws WebhookDeliveryNotFoundException if missing or another merchant's
     * @throws RedeliveryRefusedException       if the record, its endpoint or its merchant does not allow it
     */
    @Transactional
    public int redeliver(UUID deliveryId, UUID merchantId, UUID userId, MerchantWebhookRedelivery.RequestedBy role) {
        MerchantWebhookDelivery delivery = deliveries.lockById(deliveryId)
                .filter(found -> merchantId == null || found.getMerchantId().equals(merchantId))
                .orElseThrow(WebhookDeliveryNotFoundException::new);
        WebhookDeliveryStatus previous = delivery.getStatus();
        switch (previous) {
            case PENDING, IN_PROGRESS -> throw new RedeliveryRefusedException("A delivery series is still running");
            case SUCCEEDED -> throw new RedeliveryRefusedException("The webhook was already delivered");
            case FAILED, SECRET_UNAVAILABLE -> {
                // redeliverable, subject to the cap
            }
        }
        if (delivery.getManualRedeliveries() >= MerchantWebhookDelivery.MAX_MANUAL_REDELIVERIES) {
            throw new RedeliveryRefusedException("The redelivery limit of "
                    + MerchantWebhookDelivery.MAX_MANUAL_REDELIVERIES + " is used up");
        }
        boolean endpointActive = endpoints.findById(delivery.getEndpointId())
                .filter(endpoint -> !endpoint.isDeleted() && endpoint.isEnabled()
                        && endpoint.getMerchantId().equals(delivery.getMerchantId()))
                .isPresent();
        if (!endpointActive) {
            throw new RedeliveryRefusedException("The webhook endpoint is disabled or deleted");
        }
        if (!merchants.findById(delivery.getMerchantId()).map(Merchant::isApproved).orElse(false)) {
            throw new RedeliveryRefusedException("The merchant is not approved");
        }

        Instant now = clock.instant();
        int series = delivery.redeliver(userId, now);
        redeliveries.save(new MerchantWebhookRedelivery(delivery.getId(), series, userId, role, previous, now));
        log.info("Webhook redelivery requested: deliveryId={}, eventId={}, series={}, by={} ({})", delivery.getId(),
                delivery.getEventId(), series, userId, role);
        return series;
    }
}
