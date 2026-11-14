package com.swiftdrop.merchant.application;

/** No such delivery record, or not the caller's: answered with 404 either way (ADR 0004 §7.5). */
public class WebhookDeliveryNotFoundException extends RuntimeException {

    public WebhookDeliveryNotFoundException() {
        super("Webhook delivery not found");
    }
}
