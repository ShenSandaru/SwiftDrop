package com.swiftdrop.delivery.web;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.application.DeliveryProofService;
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
    private final DeliveryProofService proofs;

    ParcelController(ParcelBookingService booking, ParcelReadService reads, DeliveryProofService proofs) {
        this.booking = booking;
        this.reads = reads;
        this.proofs = proofs;
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

    /** Proof photo for the owner, the assigned courier or an admin; never cached. */
    @GetMapping("/{id}/proof/photo")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'COURIER', 'DISPATCHER_ADMIN')")
    ResponseEntity<byte[]> proofPhoto(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        DeliveryProofService.Photo photo = proofs.photo(id, Callers.viewer(jwt))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(photo.bytes());
    }
}
