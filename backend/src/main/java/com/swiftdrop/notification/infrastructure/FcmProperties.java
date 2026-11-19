package com.swiftdrop.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Firebase Cloud Messaging (FCM) push delivery.
 */
@ConfigurationProperties(prefix = "swiftdrop.notification.push.fcm")
public record FcmProperties(String serviceAccountPath) {
}
