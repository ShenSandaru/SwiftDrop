package com.swiftdrop.identity.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * A user account of any role. Hibernate generates the UUID when the user is first persisted.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    /** {@code null} for users who sign in only with an external provider. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "avatar_photo")
    private byte[] avatarPhoto;

    @Column(name = "avatar_content_type", length = 50)
    private String avatarContentType;

    @Column(name = "avatar_updated_at")
    private Instant avatarUpdatedAt;

    @Column(name = "hub_id")
    private UUID hubId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_name", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    protected User() {
        // for JPA
    }

    public User(String email, String passwordHash, String fullName, Set<Role> roles) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.enabled = true;
        this.createdAt = Instant.now();
        this.roles = EnumSet.copyOf(Objects.requireNonNull(roles, "roles"));
        if (this.roles.isEmpty()) {
            throw new IllegalArgumentException("A user needs at least one role");
        }
    }

    /** Trims and lower-cases an email address, matching the database check constraint. */
    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email").trim().toLowerCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = Objects.requireNonNull(fullName, "fullName").trim();
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber != null && !phoneNumber.isBlank() ? phoneNumber.trim() : null;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Customer accounts are considered complete when they have a human name
     * (not empty, and not defaulting to their email address) and a contact phone number.
     * Non-customer accounts (e.g. admin or staff) are always considered complete.
     */
    public boolean isProfileCompleted() {
        if (!roles.contains(Role.CUSTOMER)) {
            return true;
        }
        return fullName != null
                && !fullName.isBlank()
                && !fullName.trim().equalsIgnoreCase(email.trim())
                && phoneNumber != null
                && !phoneNumber.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public UUID getHubId() {
        return hubId;
    }

    public void setHubId(UUID hubId) {
        this.hubId = hubId;
    }

    public byte[] getAvatarPhoto() {
        return avatarPhoto == null ? null : avatarPhoto.clone();
    }

    public String getAvatarContentType() {
        return avatarContentType;
    }

    public Instant getAvatarUpdatedAt() {
        return avatarUpdatedAt;
    }

    public boolean hasAvatar() {
        return avatarPhoto != null && avatarPhoto.length > 0;
    }

    public void updateAvatar(byte[] photo, String contentType) {
        this.avatarPhoto = photo == null ? null : photo.clone();
        this.avatarContentType = contentType;
        this.avatarUpdatedAt = Instant.now();
    }

    public void removeAvatar() {
        this.avatarPhoto = null;
        this.avatarContentType = null;
        this.avatarUpdatedAt = Instant.now();
    }
}
