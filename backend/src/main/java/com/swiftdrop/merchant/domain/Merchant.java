package com.swiftdrop.merchant.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.swiftdrop.merchant.api.MerchantStatus;

/**
 * Merchant business profile aggregate (ADR 0003, docs/merchant-contracts.md).
 * Represents a registered merchant business with approval lifecycle state.
 */
@Entity
@Table(name = "merchants")
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;

    @Column(name = "business_type", nullable = false, length = 50)
    private String businessType;

    @Column(name = "business_registration_no", nullable = false, unique = true, length = 100)
    private String businessRegistrationNo;

    @Column(name = "contact_phone", nullable = false, length = 20)
    private String contactPhone;

    @Column(name = "business_address", nullable = false, length = 500)
    private String businessAddress;

    @Column(name = "business_description", length = 500)
    private String businessDescription;

    @Column(name = "website_url", length = 2048)
    private String websiteUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MerchantStatus status;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Merchant() {
        // for JPA
    }

    public Merchant(UUID userId, String businessName, String businessType, String businessRegistrationNo,
            String contactPhone, String businessAddress, String businessDescription, String websiteUrl) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.businessName = normalizeRequired(businessName, "businessName");
        this.businessType = normalizeRequired(businessType, "businessType");
        this.businessRegistrationNo = normalizeRequired(businessRegistrationNo, "businessRegistrationNo");
        this.contactPhone = normalizeRequired(contactPhone, "contactPhone");
        this.businessAddress = normalizeRequired(businessAddress, "businessAddress");
        this.businessDescription = businessDescription != null && !businessDescription.isBlank() ? businessDescription.trim() : null;
        this.websiteUrl = websiteUrl != null && !websiteUrl.isBlank() ? websiteUrl.trim() : null;
        this.status = MerchantStatus.PENDING;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    private static String normalizeRequired(String value, String fieldName) {
        String trimmed = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }

    public void approve(UUID adminUserId) {
        Objects.requireNonNull(adminUserId, "adminUserId must not be null");
        if (this.status != MerchantStatus.PENDING && this.status != MerchantStatus.SUSPENDED) {
            throw new IllegalStateException("Cannot approve merchant with status " + this.status);
        }
        this.status = MerchantStatus.APPROVED;
        this.decidedBy = adminUserId;
        this.decidedAt = Instant.now();
        this.statusReason = null;
        this.updatedAt = Instant.now();
    }

    public void reject(UUID adminUserId, String reason) {
        Objects.requireNonNull(adminUserId, "adminUserId must not be null");
        if (this.status != MerchantStatus.PENDING) {
            throw new IllegalStateException("Cannot reject merchant with status " + this.status);
        }
        this.status = MerchantStatus.REJECTED;
        this.decidedBy = adminUserId;
        this.decidedAt = Instant.now();
        this.statusReason = reason != null && !reason.isBlank() ? reason.trim() : null;
        this.updatedAt = Instant.now();
    }

    public void suspend(UUID adminUserId, String reason) {
        Objects.requireNonNull(adminUserId, "adminUserId must not be null");
        if (this.status != MerchantStatus.APPROVED) {
            throw new IllegalStateException("Cannot suspend merchant with status " + this.status);
        }
        this.status = MerchantStatus.SUSPENDED;
        this.decidedBy = adminUserId;
        this.decidedAt = Instant.now();
        this.statusReason = reason != null && !reason.isBlank() ? reason.trim() : null;
        this.updatedAt = Instant.now();
    }

    public void resubmit(String businessName, String businessType, String contactPhone, String businessAddress,
            String businessDescription, String websiteUrl) {
        if (this.status != MerchantStatus.REJECTED) {
            throw new IllegalStateException("Only rejected merchants can resubmit registration, current: " + this.status);
        }
        this.businessName = normalizeRequired(businessName, "businessName");
        this.businessType = normalizeRequired(businessType, "businessType");
        this.contactPhone = normalizeRequired(contactPhone, "contactPhone");
        this.businessAddress = normalizeRequired(businessAddress, "businessAddress");
        this.businessDescription = businessDescription != null && !businessDescription.isBlank() ? businessDescription.trim() : null;
        this.websiteUrl = websiteUrl != null && !websiteUrl.isBlank() ? websiteUrl.trim() : null;
        this.status = MerchantStatus.PENDING;
        this.statusReason = null;
        this.decidedBy = null;
        this.decidedAt = null;
        this.updatedAt = Instant.now();
    }

    public boolean isApproved() {
        return this.status == MerchantStatus.APPROVED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = normalizeRequired(businessName, "businessName");
        this.updatedAt = Instant.now();
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = normalizeRequired(businessType, "businessType");
        this.updatedAt = Instant.now();
    }

    public String getBusinessRegistrationNo() {
        return businessRegistrationNo;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = normalizeRequired(contactPhone, "contactPhone");
        this.updatedAt = Instant.now();
    }

    public String getBusinessAddress() {
        return businessAddress;
    }

    public void setBusinessAddress(String businessAddress) {
        this.businessAddress = normalizeRequired(businessAddress, "businessAddress");
        this.updatedAt = Instant.now();
    }

    public String getBusinessDescription() {
        return businessDescription;
    }

    public void setBusinessDescription(String businessDescription) {
        this.businessDescription = businessDescription != null && !businessDescription.isBlank() ? businessDescription.trim() : null;
        this.updatedAt = Instant.now();
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl != null && !websiteUrl.isBlank() ? websiteUrl.trim() : null;
        this.updatedAt = Instant.now();
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
