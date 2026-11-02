package com.swiftdrop.delivery.web;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.application.ParcelBookingService;
import com.swiftdrop.delivery.application.ParcelReadService;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

/**
 * Customer bookings, delivery history, detail and receipts (CUS-2, CUS-4, CUS-5). Roles are checked
 * here; ownership is checked by {@link ParcelReadService}, which answers 404 for parcels the caller
 * may not see.
 */
@RestController
@RequestMapping("/api/v1/parcels")
class ParcelController {

    private final ParcelBookingService booking;
    private final ParcelReadService reads;

    ParcelController(ParcelBookingService booking, ParcelReadService reads) {
        this.booking = booking;
        this.reads = reads;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    ResponseEntity<ParcelCreatedResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateParcelRequest request) {
        Parcel parcel = booking.bookForCustomer(Callers.userId(jwt), request.toBooking());
        return ResponseEntity.created(URI.create("/api/v1/parcels/" + parcel.getId()))
                .body(ParcelCreatedResponse.from(parcel));
    }

    /** Customers see their own parcels, dispatchers/admins all; newest first. */
    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'DISPATCHER_ADMIN')")
    PageResponse<ParcelListItemResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) ParcelStatus status) {
        int pageNumber = page != null ? PageLimit.clampPage(page) : PageLimit.DEFAULT_PAGE_NUMBER;
        int pageSize = size != null ? PageLimit.clampSize(size) : PageLimit.DEFAULT_PAGE_SIZE;
        Page<Parcel> parcels = reads.list(Callers.viewer(jwt), status, pageNumber, pageSize);
        return PageResponse.of(parcels.getContent().stream().map(ParcelListItemResponse::from).toList(),
                pageNumber, pageSize, parcels.getTotalElements());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'COURIER', 'DISPATCHER_ADMIN')")
    ParcelDetailResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ParcelDetailResponse.from(reads.detail(id, Callers.viewer(jwt)));
    }

    @GetMapping("/{id}/receipt")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'DISPATCHER_ADMIN')")
    ReceiptResponse receipt(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ReceiptResponse.from(reads.receipt(id, Callers.viewer(jwt)));
    }
}
