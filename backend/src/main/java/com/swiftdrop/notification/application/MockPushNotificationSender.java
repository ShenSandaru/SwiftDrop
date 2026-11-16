package com.swiftdrop.notification.application;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local/mock {@link PushNotificationSender} implementation that logs push dispatches
 * without connecting to Firebase Cloud Messaging.
 */
@Component
@ConditionalOnProperty(
        name = "swiftdrop.notification.push.provider",
        havingValue = "mock",
        matchIfMissing = true)
public class MockPushNotificationSender implements PushNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(MockPushNotificationSender.class);

    @Override
    public PushSendResult send(String token, String title, String body, Map<String, String> data) {
        log.info("[MOCK PUSH] title={}, body={}, dataKeys={}",
                title, body, data != null ? data.keySet() : "[]");
        return PushSendResult.SENT;
    }
}
