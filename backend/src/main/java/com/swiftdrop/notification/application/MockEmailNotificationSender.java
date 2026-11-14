package com.swiftdrop.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Local/mock {@link EmailNotificationSender} implementation that logs email dispatches
 * without connecting to an external SMTP or email provider.
 */
@Component
public class MockEmailNotificationSender implements EmailNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(MockEmailNotificationSender.class);

    @Override
    public void send(String recipientEmail, String subject, String body) {
        log.info("[MOCK EMAIL] to={}, subject={}, body={}", recipientEmail, subject, body);
    }
}

