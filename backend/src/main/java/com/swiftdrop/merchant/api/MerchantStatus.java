package com.swiftdrop.merchant.api;

/**
 * Status lifecycle of a merchant business account (MER-1, docs/merchant-contracts.md).
 */
public enum MerchantStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED
}

