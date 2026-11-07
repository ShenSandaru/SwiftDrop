package com.swiftdrop.merchant.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import com.swiftdrop.merchant.api.MerchantStatus;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;

@ExtendWith(MockitoExtension.class)
class MerchantAccessSecurityTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private Authentication authentication;

    private MerchantAccessSecurity security;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        security = new MerchantAccessSecurity(merchantRepository);
    }

    @Test
    @DisplayName("isApproved returns true for APPROVED merchant")
    void approvedMerchantAccessGranted() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(userId.toString());

        Merchant merchant = new Merchant(
                userId, "Store", "Retail", "BR-1", "+94771234567", "Addr", null, null);
        merchant.approve(UUID.randomUUID());

        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(merchant));

        assertThat(security.isApproved(authentication)).isTrue();
    }

    @Test
    @DisplayName("isApproved throws AccessDeniedException for PENDING merchant")
    void pendingMerchantAccessDenied() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(userId.toString());

        Merchant merchant = new Merchant(
                userId, "Store", "Retail", "BR-1", "+94771234567", "Addr", null, null);

        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(merchant));

        assertThatThrownBy(() -> security.isApproved(authentication))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    @DisplayName("isApproved throws AccessDeniedException for REJECTED or SUSPENDED merchant")
    void rejectedOrSuspendedMerchantAccessDenied() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(userId.toString());

        Merchant merchant = new Merchant(
                userId, "Store", "Retail", "BR-1", "+94771234567", "Addr", null, null);
        merchant.reject(UUID.randomUUID(), "Bad documents");

        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(merchant));

        assertThatThrownBy(() -> security.isApproved(authentication))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("REJECTED");
    }

    @Test
    @DisplayName("isApproved throws AccessDeniedException when merchant profile not found")
    void merchantNotFoundAccessDenied() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(userId.toString());

        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> security.isApproved(authentication))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No merchant profile");
    }
}

