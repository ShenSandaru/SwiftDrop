package com.swiftdrop.pricing.web;

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

import com.swiftdrop.pricing.application.PricingRuleAdministration;

/**
 * Admin API for managing delivery pricing rules (ADM-3, contract §4.2).
 * Admin-only: list, create, and update pricing rules per zone pair.
 */
@RestController
@RequestMapping("/api/v1/pricing-rules")
class PricingRuleController {

    private final PricingRuleAdministration pricingRules;

    PricingRuleController(PricingRuleAdministration pricingRules) {
        this.pricingRules = pricingRules;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    List<PricingRuleResponse> list() {
        return pricingRules.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    PricingRuleResponse get(@PathVariable UUID id) {
        return pricingRules.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    PricingRuleResponse create(@Valid @RequestBody PricingRuleRequest request) {
        return pricingRules.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    PricingRuleResponse update(@PathVariable UUID id, @Valid @RequestBody PricingRuleRequest request) {
        return pricingRules.update(id, request);
    }
}

