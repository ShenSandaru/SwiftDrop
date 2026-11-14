package com.swiftdrop.merchant.web;

/** No order with this reference for the calling merchant; another merchant's order looks the same. */
class MerchantOrderNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    MerchantOrderNotFoundException() {
        super("Order not found");
    }
}
