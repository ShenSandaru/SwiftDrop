package com.swiftdrop.merchant.web;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.merchant.application.MerchantPortalReadService;
import com.swiftdrop.merchant.security.MerchantAccessSecurity;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

/**
 * Read-side views of the merchant portal (Phase 5). The merchant ID always comes from the signed-in user, never
 * from the URL or a parameter; another merchant's order answers 404.
 *
 * <p>The webhook delivery history ({@code GET /api/v1/merchants/me/webhook-deliveries}) is served by
 * {@link WebhookDeliveryController} over {@code WebhookDeliveryQuery}, so it is not repeated here.
 */
@RestController
@RequestMapping("/api/v1/merchants/me")
@PreAuthorize("hasRole('MERCHANT') and @merchantAccess.isApproved(authentication)")
public class MerchantPortalController {

    private final MerchantPortalReadService readService;
    private final MerchantAccessSecurity merchantAccess;

    public MerchantPortalController(MerchantPortalReadService readService, MerchantAccessSecurity merchantAccess) {
        this.readService = readService;
        this.merchantAccess = merchantAccess;
    }

    @GetMapping("/dashboard")
    public MerchantDashboardResponse dashboard(Authentication authentication) {
        return MerchantDashboardResponse.from(readService.dashboard(merchantAccess.getMerchantId(authentication)));
    }

    @GetMapping("/orders")
    public PageResponse<MerchantOrderSummaryResponse> orders(Authentication authentication,
            @RequestParam(required = false) ParcelStatus status,
            @RequestParam(defaultValue = PageLimit.DEFAULT_PAGE_STRING) int page,
            @RequestParam(defaultValue = PageLimit.DEFAULT_SIZE_STRING) int size) {
        PageResult<ParcelSummary> result = readService.orders(merchantAccess.getMerchantId(authentication), status,
                new PageRequest(PageLimit.clampPage(page), PageLimit.clampSize(size)));
        return PageResponse.of(result.items().stream().map(MerchantOrderSummaryResponse::from).toList(),
                result.page(), result.size(), result.totalItems());
    }

    @GetMapping("/orders/{id}")
    public MerchantOrderDetailResponse order(@PathVariable UUID id, Authentication authentication) {
        return MerchantOrderDetailResponse.from(readService.order(merchantAccess.getMerchantId(authentication), id));
    }
}
