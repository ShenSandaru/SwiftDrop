package com.swiftdrop.delivery.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.delivery.application.HubAdministration;

/**
 * The hub network (ADR 0009 §1). Dispatchers read it to scan parcels at hubs; only the admin
 * changes it. Hubs hold no personal data.
 */
@RestController
@RequestMapping("/api/v1/hubs")
class HubController {

    private final HubAdministration hubs;

    HubController(HubAdministration hubs) {
        this.hubs = hubs;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
    List<HubResponse> list() {
        return hubs.list().stream().map(HubResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    HubResponse create(@Valid @RequestBody HubRequest request) {
        return HubResponse.from(hubs.create(request.details(), request.code()));
    }

    /** Changes everything but the code, which history and events record. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    HubResponse update(@PathVariable UUID id, @Valid @RequestBody HubRequest request) {
        return HubResponse.from(hubs.update(id, request.details(), request.activeOrDefault()));
    }
}
