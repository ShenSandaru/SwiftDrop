package com.swiftdrop.merchant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.swiftdrop.merchant.api.MerchantStatus;

class MerchantTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    @Test
    @DisplayName("New merchant starts in PENDING status with null decision fields and version 0")
    void newMerchantInitialState() {
        Merchant merchant = new Merchant(
                userId,
                "Acme Corp",
                "Retail",
                "PV12345",
                "+94771234567",
                "123 Main St, Colombo",
                "General retail store",
                "https://acme.example.com");

        assertThat(merchant.getId()).isNull();
        assertThat(merchant.getUserId()).isEqualTo(userId);
        assertThat(merchant.getBusinessName()).isEqualTo("Acme Corp");
        assertThat(merchant.getBusinessType()).isEqualTo("Retail");
        assertThat(merchant.getBusinessRegistrationNo()).isEqualTo("PV12345");
        assertThat(merchant.getContactPhone()).isEqualTo("+94771234567");
        assertThat(merchant.getBusinessAddress()).isEqualTo("123 Main St, Colombo");
        assertThat(merchant.getBusinessDescription()).isEqualTo("General retail store");
        assertThat(merchant.getWebsiteUrl()).isEqualTo("https://acme.example.com");
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.PENDING);
        assertThat(merchant.isApproved()).isFalse();
        assertThat(merchant.getStatusReason()).isNull();
        assertThat(merchant.getDecidedBy()).isNull();
        assertThat(merchant.getDecidedAt()).isNull();
        assertThat(merchant.getCreatedAt()).isNotNull();
        assertThat(merchant.getUpdatedAt()).isNotNull();
        assertThat(merchant.getVersion()).isZero();
    }

    @Test
    @DisplayName("Admin approval transitions PENDING merchant to APPROVED")
    void approvePendingMerchant() {
        Merchant merchant = new Merchant(
                userId, "Acme Corp", "Retail", "PV12345", "+94771234567", "123 Main St", null, null);

        merchant.approve(adminId);

        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(merchant.isApproved()).isTrue();
        assertThat(merchant.getDecidedBy()).isEqualTo(adminId);
        assertThat(merchant.getDecidedAt()).isNotNull();
        assertThat(merchant.getStatusReason()).isNull();
    }

    @Test
    @DisplayName("Admin rejection transitions PENDING merchant to REJECTED with reason")
    void rejectPendingMerchant() {
        Merchant merchant = new Merchant(
                userId, "Acme Corp", "Retail", "PV12345", "+94771234567", "123 Main St", null, null);

        merchant.reject(adminId, "Invalid registration document");

        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.REJECTED);
        assertThat(merchant.isApproved()).isFalse();
        assertThat(merchant.getDecidedBy()).isEqualTo(adminId);
        assertThat(merchant.getDecidedAt()).isNotNull();
        assertThat(merchant.getStatusReason()).isEqualTo("Invalid registration document");
    }

    @Test
    @DisplayName("Rejected merchant can resubmit profile, transitioning back to PENDING")
    void resubmitRejectedMerchant() {
        Merchant merchant = new Merchant(
                userId, "Acme Corp", "Retail", "PV12345", "+94771234567", "123 Main St", null, null);
        merchant.reject(adminId, "Invalid document");

        merchant.resubmit("Acme Global Corp", "Wholesale", "+94779999999", "456 High St, Colombo", "Global distribution", "https://acme.org");

        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.PENDING);
        assertThat(merchant.isApproved()).isFalse();
        assertThat(merchant.getBusinessName()).isEqualTo("Acme Global Corp");
        assertThat(merchant.getBusinessType()).isEqualTo("Wholesale");
        assertThat(merchant.getContactPhone()).isEqualTo("+94779999999");
        assertThat(merchant.getBusinessAddress()).isEqualTo("456 High St, Colombo");
        assertThat(merchant.getBusinessDescription()).isEqualTo("Global distribution");
        assertThat(merchant.getWebsiteUrl()).isEqualTo("https://acme.org");
        assertThat(merchant.getStatusReason()).isNull();
        assertThat(merchant.getDecidedBy()).isNull();
        assertThat(merchant.getDecidedAt()).isNull();
    }

    @Test
    @DisplayName("Admin can suspend an APPROVED merchant, and reinstate it back to APPROVED")
    void suspendAndReinstateMerchant() {
        Merchant merchant = new Merchant(
                userId, "Acme Corp", "Retail", "PV12345", "+94771234567", "123 Main St", null, null);
        merchant.approve(adminId);

        merchant.suspend(adminId, "Policy violation investigation");
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.SUSPENDED);
        assertThat(merchant.isApproved()).isFalse();
        assertThat(merchant.getStatusReason()).isEqualTo("Policy violation investigation");

        // Reinstate
        merchant.approve(adminId);
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(merchant.isApproved()).isTrue();
        assertThat(merchant.getStatusReason()).isNull();
    }

    @Test
    @DisplayName("Invalid state transitions throw IllegalStateException")
    void invalidTransitions() {
        Merchant merchant = new Merchant(
                userId, "Acme Corp", "Retail", "PV12345", "+94771234567", "123 Main St", null, null);

        // Cannot suspend a PENDING merchant
        assertThatThrownBy(() -> merchant.suspend(adminId, "Reason"))
                .isInstanceOf(IllegalStateException.class);

        // Cannot resubmit a PENDING merchant
        assertThatThrownBy(() -> merchant.resubmit("New Name", "Retail", "+94771234567", "Address", null, null))
                .isInstanceOf(IllegalStateException.class);

        merchant.approve(adminId);

        // Cannot reject an APPROVED merchant
        assertThatThrownBy(() -> merchant.reject(adminId, "Reason"))
                .isInstanceOf(IllegalStateException.class);
    }
}

