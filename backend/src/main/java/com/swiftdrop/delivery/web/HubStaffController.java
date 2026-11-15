package com.swiftdrop.delivery.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.delivery.application.HubStaffAdministration;

/**
 * Home hubs of dispatchers and couriers (ADR 0012 §5). Only the admin reads or changes them: they
 * decide who sees which parcels.
 */
@RestController
@RequestMapping("/api/v1/hubs")
@PreAuthorize("hasRole('ADMIN')")
class HubStaffController {

    private final HubStaffAdministration staff;

    HubStaffController(HubStaffAdministration staff) {
        this.staff = staff;
    }

    @GetMapping("/staff")
    List<StaffMemberResponse> list() {
        return staff.list().stream().map(StaffMemberResponse::from).toList();
    }

    /** Idempotent. A head office dispatcher becomes a hub dispatcher. */
    @PutMapping("/{hubId}/staff/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void addToHub(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID hubId, @PathVariable UUID userId) {
        staff.addToHub(hubId, userId, Callers.userId(jwt));
    }

    @DeleteMapping("/{hubId}/staff/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeFromHub(@PathVariable UUID hubId, @PathVariable UUID userId) {
        staff.removeFromHub(hubId, userId);
    }

    /** Idempotent. Replaces the dispatcher's hubs. */
    @PutMapping("/head-office-staff/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void makeHeadOffice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        staff.makeHeadOffice(userId, Callers.userId(jwt));
    }

    @DeleteMapping("/head-office-staff/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeFromHeadOffice(@PathVariable UUID userId) {
        staff.removeFromHeadOffice(userId);
    }
}
