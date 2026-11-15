package com.swiftdrop.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A saved delivery or pickup address for a user.
 */
@Entity
@Table(name = "user_addresses")
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(name = "recipient_name", nullable = false, length = 200)
    private String recipientName;

    @Column(name = "phone_number", nullable = false, length = 30)
    private String phoneNumber;

    @Column(name = "street_address", nullable = false, length = 255)
    private String streetAddress;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserAddress() {
        // for JPA
    }

    public UserAddress(
            UUID userId,
            String label,
            String recipientName,
            String phoneNumber,
            String streetAddress,
            String city,
            String postalCode,
            boolean isDefault) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.label = Objects.requireNonNull(label, "label").trim();
        this.recipientName = Objects.requireNonNull(recipientName, "recipientName").trim();
        this.phoneNumber = Objects.requireNonNull(phoneNumber, "phoneNumber").trim();
        this.streetAddress = Objects.requireNonNull(streetAddress, "streetAddress").trim();
        this.city = Objects.requireNonNull(city, "city").trim();
        this.postalCode = postalCode != null && !postalCode.isBlank() ? postalCode.trim() : null;
        this.isDefault = isDefault;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = Objects.requireNonNull(label, "label").trim();
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = Objects.requireNonNull(recipientName, "recipientName").trim();
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = Objects.requireNonNull(phoneNumber, "phoneNumber").trim();
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = Objects.requireNonNull(streetAddress, "streetAddress").trim();
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = Objects.requireNonNull(city, "city").trim();
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode != null && !postalCode.isBlank() ? postalCode.trim() : null;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

