package com.swiftdrop.notification.application;

/**
 * Abstraction for sending email notifications.
 */
public interface EmailNotificationSender {

    /**
     * Sends an email notification to the specified recipient.
     *
     * @param recipientEmail the recipient's email address
     * @param subject        the email subject
     * @param body           the email body text
     */
    void send(String recipientEmail, String subject, String body);
}

