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

/**
 * A merchant's own webhook deliveries (SD-412): history, detail with attempt series, and manual redelivery.
 * Scoped by the authenticated merchant; another merchant's record answers 404.
 */
@RestController
@RequestMapping("/api/v1/merchants/me/webhook-deliveries")
@PreAuthorize("hasRole('MERCHANT') and @merchantAccess.isApproved(authentication)")
public class WebhookDeliveryController {

    private final WebhookDeliveryHistoryService history;
    private final WebhookRedeliveryService redelivery;
    private final MerchantAccessSecurity merchantAccess;

    public WebhookDeliveryController(WebhookDeliveryHistoryService history, WebhookRedeliveryService redelivery,
            MerchantAccessSecurity merchantAccess) {
        this.history = history;
        this.redelivery = redelivery;
        this.merchantAccess = merchantAccess;
    }

    @GetMapping
    public PageResponse<WebhookDeliveryView> list(Authentication authentication,
            @RequestParam(required = false) UUID endpointId,
            @RequestParam(required = false) WebhookDeliveryStatus status,
            @RequestParam(defaultValue = PageLimit.DEFAULT_PAGE_STRING) int page,
            @RequestParam(defaultValue = PageLimit.DEFAULT_SIZE_STRING) int size) {
        PageResult<WebhookDeliveryView> result = history.history(merchantAccess.getMerchantId(authentication),
                endpointId, status, new PageRequest(PageLimit.clampPage(page), PageLimit.clampSize(size)));
        return PageResponse.of(result.items(), result.page(), result.size(), result.totalItems());
    }

    @GetMapping("/{id}")
    public WebhookDeliveryDetail detail(@PathVariable UUID id, Authentication authentication) {
        return history.detail(id, merchantAccess.getMerchantId(authentication));
    }

    @PostMapping("/{id}/redeliveries")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RedeliveryResponse redeliver(@PathVariable UUID id, Authentication authentication) {
        int series = redelivery.redeliver(id, merchantAccess.getMerchantId(authentication),
                MerchantAccessSecurity.extractUserId(authentication), MerchantWebhookRedelivery.RequestedBy.MERCHANT);
        return new RedeliveryResponse(id, series, WebhookDeliveryStatus.PENDING.name());
    }
}
