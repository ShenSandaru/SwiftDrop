package com.swiftdrop.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Local/mock {@link SmsNotificationSender} implementation that logs SMS dispatches
 * without connecting to an external SMS gateway.
 */
@Component
public class MockSmsNotificationSender implements SmsNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(MockSmsNotificationSender.class);

    @Override
    public void send(String recipientPhoneNumber, String message) {
        log.info("[MOCK SMS] to={}, message={}", recipientPhoneNumber, message);
    }
}

