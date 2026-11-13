package com.swiftdrop.merchant.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.merchant.api.WebhookDeliveryView;
import com.swiftdrop.merchant.application.WebhookDeliveryDetail;
import com.swiftdrop.merchant.application.WebhookDeliveryHistoryService;
import com.swiftdrop.merchant.application.WebhookRedeliveryService;
import com.swiftdrop.merchant.domain.MerchantWebhookRedelivery;
import com.swiftdrop.merchant.domain.WebhookDeliveryStatus;
import com.swiftdrop.merchant.security.MerchantAccessSecurity;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

/** Webhook deliveries of all merchants for administrators (SD-412, ADR 0004 §7.5). Secrets are never shown. */
@RestController
@RequestMapping("/api/v1/webhook-deliveries")
@PreAuthorize("hasRole('ADMIN')")
public class AdminWebhookDeliveryController {

    private final WebhookDeliveryHistoryService history;
    private final WebhookRedeliveryService redelivery;

    public AdminWebhookDeliveryController(WebhookDeliveryHistoryService history, WebhookRedeliveryService redelivery) {
        this.history = history;
        this.redelivery = redelivery;
    }

    @GetMapping
    public PageResponse<WebhookDeliveryView> list(
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) UUID endpointId,
            @RequestParam(required = false) WebhookDeliveryStatus status,
            @RequestParam(defaultValue = PageLimit.DEFAULT_PAGE_STRING) int page,
            @RequestParam(defaultValue = PageLimit.DEFAULT_SIZE_STRING) int size) {
        PageResult<WebhookDeliveryView> result = history.history(merchantId, endpointId, status,
                new PageRequest(PageLimit.clampPage(page), PageLimit.clampSize(size)));
        return PageResponse.of(result.items(), result.page(), result.size(), result.totalItems());
    }

    @GetMapping("/{id}")
    public WebhookDeliveryDetail detail(@PathVariable UUID id) {
        return history.detail(id, null);
    }

    @PostMapping("/{id}/redeliveries")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RedeliveryResponse redeliver(@PathVariable UUID id, Authentication authentication) {
        int series = redelivery.redeliver(id, null, MerchantAccessSecurity.extractUserId(authentication),
                MerchantWebhookRedelivery.RequestedBy.ADMIN);
        return new RedeliveryResponse(id, series, WebhookDeliveryStatus.PENDING.name());
    }
}
