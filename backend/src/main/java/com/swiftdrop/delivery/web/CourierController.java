package com.swiftdrop.delivery.web;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.swiftdrop.delivery.application.Actor;
import com.swiftdrop.delivery.application.CourierParcelService;
import com.swiftdrop.delivery.application.DeliveryProofService;
import com.swiftdrop.delivery.application.ParcelReadService;
import com.swiftdrop.delivery.application.ParcelStatusService;

/**
 * Courier operations (COU-2 to COU-4). Only the parcel's assigned courier may change it; any other
 * courier gets 404, so couriers cannot discover each other's deliveries (TST-2).
 */
@RestController
@PreAuthorize("hasRole('COURIER')")
class CourierController {

    private final CourierParcelService courierParcels;
    private final ParcelStatusService statuses;
    private final DeliveryProofService proofs;
    private final ParcelReadService reads;

    CourierController(CourierParcelService courierParcels, ParcelStatusService statuses, DeliveryProofService proofs,
            ParcelReadService reads) {
        this.courierParcels = courierParcels;
        this.statuses = statuses;
        this.proofs = proofs;
        this.reads = reads;
    }

    /** Deliveries for the day: unfinished ones plus those finished today. */
    @GetMapping("/api/v1/courier/parcels")
    List<CourierParcelResponse> today(@AuthenticationPrincipal Jwt jwt) {
        return courierParcels.today(Callers.userId(jwt)).stream().map(CourierParcelResponse::from).toList();
    }

    @PostMapping("/api/v1/parcels/{id}/status-changes")
    ParcelDetailResponse changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody StatusChangeRequest request) {
        statuses.changeStatus(id, request.toChange(), Actor.courier(Callers.userId(jwt)));
        return ParcelDetailResponse.from(reads.detail(id, Callers.viewer(jwt)));
    }

    /** Multipart: {@code recipientName} and an optional {@code photo} (JPEG or PNG, at most 2 MB). */
    @PutMapping(path = "/api/v1/parcels/{id}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ProofResponse captureProof(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @RequestParam String recipientName, @RequestPart(required = false) MultipartFile photo)
            throws IOException {
        byte[] bytes = photo == null || photo.isEmpty() ? null : photo.getBytes();
        return ProofResponse.from(proofs.capture(id, Actor.courier(Callers.userId(jwt)), recipientName, bytes));
    }
}
