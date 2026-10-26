package com.swiftdrop.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional settings to bootstrap the initial {@code DISPATCHER_ADMIN} account on startup.
 *
 * @param adminEmail    from {@code SWIFTDROP_BOOTSTRAP_ADMIN_EMAIL}
 * @param adminPassword from {@code SWIFTDROP_BOOTSTRAP_ADMIN_PASSWORD}
 */
@ConfigurationProperties("swiftdrop.identity.bootstrap")
public record BootstrapAdminProperties(String adminEmail, String adminPassword) {

    public BootstrapAdminProperties {
        boolean hasEmail = adminEmail != null && !adminEmail.isBlank();
        boolean hasPassword = adminPassword != null && !adminPassword.isBlank();
        if (hasEmail != hasPassword) {
            throw new IllegalStateException("Partial admin bootstrap configuration: both "
                    + "SWIFTDROP_BOOTSTRAP_ADMIN_EMAIL and SWIFTDROP_BOOTSTRAP_ADMIN_PASSWORD "
                    + "must be provided, or neither");
        }
    }

    public boolean isConfigured() {
        return adminEmail != null && !adminEmail.isBlank()
                && adminPassword != null && !adminPassword.isBlank();
    }

    /** Never exposes the password. */
    @Override
    public String toString() {
        return "BootstrapAdminProperties[adminEmail=" + adminEmail + ", configured=" + isConfigured() + "]";
    }
}
