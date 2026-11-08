package com.swiftdrop.merchant.web;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.merchant.api.AuthenticatedMerchant;
import com.swiftdrop.merchant.application.MerchantOrderService;

/**
 * Merchant order API (SD-405, MER-2), called by merchant systems with an API key (ADR 0008). The
 * merchant is always the one behind the key, never a value from the request.
 */
@RestController
@RequestMapping("/api/v1/merchant-orders")
@PreAuthorize("hasAuthority('" + AuthenticatedMerchant.AUTHORITY + "')")
public class MerchantOrderController {

    private final MerchantOrderService orders;

    public MerchantOrderController(MerchantOrderService orders) {
        this.orders = orders;
    }

    /** {@code 201} with a {@code Location} for a new order; {@code 200} with the existing order for a repeated reference. */
    @PostMapping
    public ResponseEntity<MerchantOrderCreatedResponse> create(@Valid @RequestBody MerchantOrderRequest request,
            @AuthenticationPrincipal AuthenticatedMerchant merchant) {
        CreatedParcel parcel = orders.create(request.toNewParcel(merchant.merchantId()));
        var body = MerchantOrderCreatedResponse.from(request.merchantOrderReference(), parcel);
        if (!parcel.newlyCreated()) {
            return ResponseEntity.ok(body);
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{merchantOrderReference}")
                .buildAndExpand(request.merchantOrderReference())
                .encode()
                .toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping("/{merchantOrderReference}")
    public MerchantOrderResponse get(@PathVariable String merchantOrderReference,
            @AuthenticationPrincipal AuthenticatedMerchant merchant) {
        return orders.find(merchant.merchantId(), merchantOrderReference)
                .map(MerchantOrderResponse::from)
                .orElseThrow(MerchantOrderNotFoundException::new);
    }
}
