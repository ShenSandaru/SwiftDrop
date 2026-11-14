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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.merchant.api.MerchantStatus;
import com.swiftdrop.merchant.application.MerchantApprovalService;
import com.swiftdrop.merchant.application.MerchantRegistrationService;
import com.swiftdrop.merchant.security.MerchantAccessSecurity;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

/**
 * REST controller for merchant registration, profile self-service, and admin approval workflows (Phase 2 & 3).
 */
@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantRegistrationService registrationService;
    private final MerchantApprovalService approvalService;

    public MerchantController(
            MerchantRegistrationService registrationService,
            MerchantApprovalService approvalService) {
        this.registrationService = registrationService;
        this.approvalService = approvalService;
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
        MerchantStatusResponse status = approvalService.getMerchantStatusForUser(userId);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant profile not found for user");
        }
        return ResponseEntity.ok(status);
    }

    /**
     * Merchant self-service: update profile.
     * If the merchant is in REJECTED status, this update automatically resubmits the application to PENDING.
     */
    @PatchMapping("/me")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<MerchantStatusResponse> updateMyProfile(
            @Valid @RequestBody MerchantProfileUpdateRequest request,
            Authentication authentication) {
        UUID userId = MerchantAccessSecurity.extractUserId(authentication);
        MerchantStatusResponse updated = approvalService.updateProfile(userId, request);
        return ResponseEntity.ok(updated);
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
        Page<MerchantStatusResponse> resultPage = approvalService.findMerchants(status, pageable);
        return PageResponse.of(resultPage.getContent(), resultPage.getNumber(), resultPage.getSize(), resultPage.getTotalElements());
    }

    /**
     * Retrieves full merchant details including status audit history (Admin only).
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MerchantDetailResponse getMerchantDetail(@PathVariable UUID id) {
        return approvalService.getMerchantDetail(id);
    }

    /**
     * Admin decision endpoint for approving, rejecting, or suspending a merchant application (Phase 3).
     */
    @PostMapping("/{id}/approval-decisions")
    @PreAuthorize("hasRole('ADMIN')")
    public MerchantStatusResponse decideApproval(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalDecisionRequest request,
            Authentication authentication) {
        UUID adminUserId = MerchantAccessSecurity.extractUserId(authentication);
        return approvalService.reviewDecision(id, request, adminUserId);
    }

    /**
     * Backward-compatible endpoint for existing approval actions (Phase 2 review modal).
     */
    @PostMapping("/{merchantId}/approval")
    @PreAuthorize("hasRole('ADMIN')")
    public MerchantStatusResponse reviewApplication(
            @PathVariable UUID merchantId,
            @Valid @RequestBody MerchantApprovalRequest request,
            Authentication authentication) {
        UUID adminUserId = MerchantAccessSecurity.extractUserId(authentication);
        ApprovalDecisionRequest.Decision decision = request.decision() == MerchantApprovalRequest.Decision.APPROVE
                ? ApprovalDecisionRequest.Decision.APPROVE
                : ApprovalDecisionRequest.Decision.REJECT;
        return approvalService.reviewDecision(merchantId, new ApprovalDecisionRequest(decision, request.reason()), adminUserId);
    }
}
