package com.swiftdrop.merchant.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.merchant.application.MerchantWebhookService;
import com.swiftdrop.merchant.security.MerchantAccessSecurity;

/**
 * REST controller for merchant webhook endpoint subscription management (Phase 4).
 * Base path: {@code /api/v1/merchants/me/webhook-endpoints}.
 * <p>
 * Enforces authenticated merchant principal resolution and requires approved status
 * for creating, updating, deleting, or rotating endpoints.
 * Never outputs sensitive credentials or logs request bodies (NFR-04).
 */
@RestController
@RequestMapping("/api/v1/merchants/me/webhook-endpoints")
@PreAuthorize("hasRole('MERCHANT') and @merchantAccess.isApproved(authentication)")
public class MerchantWebhookEndpointController {

    private final MerchantWebhookService webhookService;

    public MerchantWebhookEndpointController(MerchantWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * Creates a new webhook endpoint subscription.
     * Returns the raw signing secret once in the response.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateWebhookEndpointResponse createEndpoint(
            @Valid @RequestBody CreateWebhookEndpointRequest request,
            Authentication authentication) {
        UUID userId = MerchantAccessSecurity.extractUserId(authentication);
        return webhookService.createEndpoint(userId, request);
    }

    /**
     * Lists all active webhook endpoints for the authenticated merchant.
     */
    @GetMapping
    public List<WebhookEndpointResponse> listEndpoints(Authentication authentication) {
        UUID userId = MerchantAccessSecurity.extractUserId(authentication);
        return webhookService.listEndpoints(userId);
    }

    /**
     * Retrieves detail for a specific webhook endpoint.
     */
    @GetMapping("/{id}")
    public WebhookEndpointResponse getEndpoint(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = MerchantAccessSecurity.extractUserId(authentication);
        return webhookService.getEndpoint(userId, id);
    }

    /**
     * Updates an existing webhook endpoint.
     */
    @PatchMapping("/{id}")
    public WebhookEndpointResponse updateEndpoint(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWebhookEndpointRequest request,
            Authentication authentication) {
        UUID userId = MerchantAccessSecurity.extractUserId(authentication);
        return webhookService.updateEndpoint(userId, id, request);
    }

    /**
     * Soft-deletes a webhook endpoint.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEndpoint(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = MerchantAccessSecurity.extractUserId(authentication);
        webhookService.deleteEndpoint(userId, id);
    }

    /**
     * Rotates the signing secret for an endpoint, returning the new secret once.
     */
    @PostMapping("/{id}/secret-rotations")
    public RotateSecretResponse rotateSecret(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = MerchantAccessSecurity.extractUserId(authentication);
        return webhookService.rotateSecret(userId, id);
    }
}

