package com.swiftdrop.notification.application;

import java.util.Map;

/**
 * Abstraction for sending web/mobile push notifications.
 */
public interface PushNotificationSender {

    enum PushSendResult {
        SENT,
        INVALID_TOKEN
    }

    /**
     * Sends a push notification to the specified device registration token.
     *
     * @param token registration token
     * @param title notification title
     * @param body  notification body
     * @param data  optional key-value navigation/metadata payload
     * @return {@link PushSendResult#SENT} on success, or {@link PushSendResult#INVALID_TOKEN}
     *         when the provider reports the token is unregistered or invalid
     */
    PushSendResult send(String token, String title, String body, Map<String, String> data);
}
