package com.swiftdrop.notification.application;

/**
 * Abstraction for sending SMS notifications.
 */
public interface SmsNotificationSender {

    /**
     * Sends an SMS notification to the specified phone number.
     *
     * @param recipientPhoneNumber the recipient's phone number
     * @param message              the SMS message text
     */
    void send(String recipientPhoneNumber, String message);
}

