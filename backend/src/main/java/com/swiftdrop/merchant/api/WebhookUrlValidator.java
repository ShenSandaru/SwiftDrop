package com.swiftdrop.merchant.api;

/**
 * Validates candidate webhook endpoint URLs at registration time (docs/merchant-contracts.md).
 * Owned by Member B (SSRF-safe resolver). Consumed by Member A during webhook CRUD.
 */
public interface WebhookUrlValidator {

    /**
     * Validates that the provided webhook URL conforms to URI syntax, protocol rules,
     * and SSRF IP/CIDR protection requirements.
     *
     * @param url the candidate URL string
     * @return validation result
     */
    ValidationResult validate(String url);

    record ValidationResult(boolean isValid, String message) {
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }
}
