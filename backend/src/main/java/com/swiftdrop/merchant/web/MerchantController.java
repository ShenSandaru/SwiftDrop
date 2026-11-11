package com.swiftdrop.merchant.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.merchant.api.MerchantStatus;
import com.swiftdrop.merchant.application.MerchantAdminService;
import com.swiftdrop.merchant.application.MerchantRegistrationService;
import com.swiftdrop.merchant.security.MerchantAccessSecurity;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

/**
 * REST controller for merchant registration, status, and admin approval workflows (MER-1).
 */
@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantRegistrationService registrationService;
    private final MerchantAdminService adminService;

    public MerchantController(
            MerchantRegistrationService registrationService,
            MerchantAdminService adminService) {
        this.registrationService = registrationService;
        this.adminService = adminService;
    }

    /**
     * Public endpoint to submit a new merchant application.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantApplicationResponse apply(@Valid @RequestBody MerchantApplicationRequest request) {
        return registrationService.register(request);
    }

    /**
     * Allows any authenticated user with the MERCHANT role to inspect their application and approval status.
     * Works for PENDING, APPROVED, REJECTED, and SUSPENDED merchants.
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<MerchantStatusResponse> getMyStatus(Authentication authentication) {
        UUID userId = MerchantAccessSecurity.extractUserId(authentication);
        MerchantStatusResponse status = adminService.getMerchantStatusForUser(userId);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant profile not found for user");
        }
        return ResponseEntity.ok(status);
    }

    /**
     * Lists merchant applications for administrators (optionally filtered by status, e.g. PENDING).
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<MerchantStatusResponse> listMerchants(
            @RequestParam(required = false) MerchantStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int boundedPage = PageLimit.clampPage(page);
        int boundedSize = PageLimit.clampSize(size);
        Pageable pageable = PageRequest.of(boundedPage, boundedSize);
        Page<MerchantStatusResponse> resultPage = adminService.findMerchants(status, pageable);
        return PageResponse.of(resultPage.getContent(), resultPage.getNumber(), resultPage.getSize(), resultPage.getTotalElements());
    }

    /**
     * Admin approval or rejection endpoint for a merchant application.
     */
    @PostMapping("/{merchantId}/approval")
    @PreAuthorize("hasRole('ADMIN')")
    public MerchantStatusResponse reviewApplication(
            @PathVariable UUID merchantId,
            @Valid @RequestBody MerchantApprovalRequest request,
            Authentication authentication) {
        UUID adminUserId = MerchantAccessSecurity.extractUserId(authentication);
        return adminService.reviewApplication(merchantId, request, adminUserId);
    }
}
