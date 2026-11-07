package com.swiftdrop.merchant.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;

/**
 * Security expression bean {@code @merchantAccess} used in {@code @PreAuthorize} to enforce
 * that merchant-scoped operations are permitted only when the merchant profile is {@code APPROVED}.
 * Loads status directly from the database on every request, ensuring instant revocation or approval.
 */
@Component("merchantAccess")
public class MerchantAccessSecurity {

    private final MerchantRepository merchantRepository;

    public MerchantAccessSecurity(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    /**
     * Checks if the authenticated user is an APPROVED merchant.
     *
     * @param authentication current Spring Security authentication
     * @return true if the merchant exists and is APPROVED
     * @throws AccessDeniedException if the merchant does not exist or is not APPROVED
     */
    public boolean isApproved(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }

        UUID userId = extractUserId(authentication);
        Optional<Merchant> merchant = merchantRepository.findByUserId(userId);

        if (merchant.isEmpty()) {
            throw new AccessDeniedException("No merchant profile associated with this account");
        }

        if (!merchant.get().isApproved()) {
            throw new AccessDeniedException("Merchant account is not approved. Current status: " + merchant.get().getStatus());
        }

        return true;
    }

    /**
     * Resolves the merchant ID corresponding to the authenticated user.
     *
     * @param authentication current Spring Security authentication
     * @return the merchant UUID
     * @throws AccessDeniedException if no merchant exists for this user
     */
    public UUID getMerchantId(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return merchantRepository.findByUserId(userId)
                .map(Merchant::getId)
                .orElseThrow(() -> new AccessDeniedException("No merchant profile associated with this account"));
    }

    public static UUID extractUserId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("Invalid user identity in authentication token");
        }
    }
}

