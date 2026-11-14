package com.swiftdrop.merchant.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.merchant.application.MerchantApiKeyService;
import com.swiftdrop.merchant.security.MerchantAccessSecurity;

/**
 * An approved merchant's own API keys (SD-403, ADR 0008 §6). The merchant comes from the logged-in
 * user; another merchant's key answers 404.
 */
@RestController
@RequestMapping("/api/v1/merchants/me/api-keys")
@PreAuthorize("hasRole('MERCHANT') and @merchantAccess.isApproved(authentication)")
public class MerchantApiKeyController {

    private final MerchantApiKeyService apiKeys;

    public MerchantApiKeyController(MerchantApiKeyService apiKeys) {
        this.apiKeys = apiKeys;
    }

    /** Creates a key; the full key is in this response only, which must not be cached. */
    @PostMapping
    public ResponseEntity<CreatedApiKeyResponse> create(@Valid @RequestBody CreateApiKeyRequest request,
            Authentication authentication) {
        var issued = apiKeys.create(MerchantAccessSecurity.extractUserId(authentication), request.label());
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(CreatedApiKeyResponse.from(issued));
    }

    @GetMapping
    public List<ApiKeyResponse> list(Authentication authentication) {
        return apiKeys.list(MerchantAccessSecurity.extractUserId(authentication)).stream()
                .map(ApiKeyResponse::from)
                .toList();
    }

    /** Revokes a key. The row is kept so the list shows when it was revoked. */
    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID keyId, Authentication authentication) {
        apiKeys.revoke(MerchantAccessSecurity.extractUserId(authentication), keyId);
    }
}
