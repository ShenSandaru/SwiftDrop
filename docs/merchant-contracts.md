# Merchant Module — Inter-Member & Cross-Module Contracts (Phase 0)

This document formalizes the agreed contracts, boundaries, and architectural decisions between
**Member A** (Merchant account, approval lifecycle, webhook CRUD, portal UI) and **Member B**
(Machine API credentials, order intake, RabbitMQ event consumption, SSRF-safe webhook delivery).

Reference: [docs/architecture.md](architecture.md), [ADR 0003](adr/0003-backend-modules-and-boundaries.md),
and the Merchant Module Two-Member Contribution Split.

---

## 1. Ownership Boundary

| Area | Owner | Boundary & Deliverables |
|---|---|---|
| **Merchant profile, registration & approval** | **Member A** | `merchants` & `merchant_status_history` tables, registration service & controller, admin approval workflow, audit trail. |
| **Merchant portal & read-side views** | **Member A** | Merchant dashboard, profile & approval status view, orders list/details read views. |
| **Webhook endpoint CRUD** | **Member A** | `merchant_webhook_endpoints` & `merchant_webhook_endpoint_events` tables, endpoint configuration API & UI, secret generation & AES-GCM encryption. |
| **Machine credentials & order intake** | **Member B** | API keys / OAuth2 client credentials storage & validation, secured order creation API (`/api/v1/merchant/parcels`), integration with `delivery.api`. |
| **Event consumption & webhook delivery** | **Member B** | RabbitMQ consumer for canonical parcel-status events, webhook delivery execution, retry policy & backoff, delivery attempt records. |
| **SSRF-safe HTTP client (SD-409)** | **Member B** | Validating DNS resolver, IP/CIDR blocklists, DNS rebinding protection, HTTP client configuration. |

---

## 2. Contracts Published by Member A

All public interfaces and shared records are published under package `com.swiftdrop.merchant.api`
so that Member B and other modules can access them while conforming strictly to `ModuleBoundaryTest`.

### 2.1 `MerchantLookup`
Used by Member B to resolve merchant identity and check approval status on order intake and webhook delivery.

```java
package com.swiftdrop.merchant.api;

import java.util.Optional;
import java.util.UUID;

public interface MerchantLookup {

    /** Finds a merchant profile by merchant ID. */
    Optional<MerchantView> findById(UUID merchantId);

    /** Finds a merchant profile associated with the user account ID. */
    Optional<MerchantView> findByUserId(UUID userId);

    /** Returns true if the merchant exists and has status APPROVED. */
    boolean isApproved(UUID merchantId);
}
```

Supporting records and enums:

```java
public enum MerchantStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED
}

public record MerchantView(
    UUID id,
    UUID userId,
    String businessName,
    String businessRegistrationNo,
    String contactPhone,
    String businessAddress,
    String websiteUrl,
    MerchantStatus status,
    String statusReason,
    Instant createdAt
) {
    public boolean isApproved() {
        return status == MerchantStatus.APPROVED;
    }
}
```

### 2.2 `MerchantStatusChanged` (Spring Application Event)
Published by Member A when a merchant's status changes. Member B listens asynchronously to this event
to immediately revoke or disable active machine API credentials when a merchant is `SUSPENDED` or `REJECTED`.
Member A never invokes Member B's internal code directly.

```java
package com.swiftdrop.merchant.api;

import java.time.Instant;
import java.util.UUID;

public record MerchantStatusChanged(
    UUID merchantId,
    UUID userId,
    MerchantStatus fromStatus,
    MerchantStatus toStatus,
    UUID actorUserId,
    String reason,
    Instant occurredAt
) {
    public boolean isRevocation() {
        return toStatus == MerchantStatus.SUSPENDED || toStatus == MerchantStatus.REJECTED;
    }
}
```

### 2.3 `WebhookSubscriptionQuery`
Used by Member B during event processing to discover all active webhook endpoints configured for a given
merchant and parcel event type, and to obtain the decrypted signing secret needed for HMAC calculation.

```java
package com.swiftdrop.merchant.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.swiftdrop.delivery.api.ParcelStatus;

public interface WebhookSubscriptionQuery {

    /**
     * Finds all active (enabled and not soft-deleted) webhook endpoints for a merchant
     * subscribed to the given parcel event type. Decrypts the signing secret before returning.
     */
    List<WebhookSubscriptionView> findActiveFor(UUID merchantId, ParcelStatus eventType);
}

public record WebhookSubscriptionView(
    UUID id,
    UUID merchantId,
    String url,
    Set<ParcelStatus> eventTypes,
    String signingSecret
) {}
```

---

## 3. Contracts Consumed by Member A (Owned by Member B / Other Modules)

Member A defines these interfaces in `com.swiftdrop.merchant.api` (or consumes existing `api` packages)
and provides fallback stubs until the owning member delivers the full implementation.

### 3.1 `WebhookUrlValidator` (Owner: Member B)
Used by Member A during webhook endpoint creation or modification to validate that candidate URLs
satisfy security requirements without duplicating SSRF/DNS/IP-blocklist logic.

```java
package com.swiftdrop.merchant.api;

public interface WebhookUrlValidator {

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
```
*Member A provides a fallback bean that verifies basic URI syntax and HTTPS/HTTP protocols until Member B's bean is wired.*

### 3.2 `WebhookDeliveryQuery` (Owner: Member B)
Used by Member A to display the webhook delivery history screen in the merchant portal.

```java
package com.swiftdrop.merchant.api;

import java.time.Instant;
import java.util.UUID;
import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelStatus;

public interface WebhookDeliveryQuery {

    /**
     * Returns a paginated history of webhook deliveries for a merchant, optionally
     * filtered by endpoint ID.
     */
    PageResult<WebhookDeliveryView> findDeliveriesByMerchant(
        UUID merchantId,
        UUID endpointId,
        PageRequest page
    );
}

public record WebhookDeliveryView(
    UUID id,
    UUID endpointId,
    String endpointUrl,
    ParcelStatus eventType,
    Integer responseStatus,
    String status, // e.g. "SUCCESS", "FAILED", "PENDING_RETRY"
    int attemptCount,
    Instant nextRetryAt,
    Instant createdAt,
    Instant completedAt
) {}
```

### 3.3 `MerchantOrderQuery` (Owner: Delivery Module)
Used by Member A for the merchant dashboard and orders list.
The `delivery` module's `com.swiftdrop.delivery.api.ParcelQueries` already provides:
- `findParcelsByMerchant(UUID merchantId, ParcelStatus status, PageRequest page)`
- `findByMerchantReference(UUID merchantId, String merchantOrderReference)`

Member A defines an adapter facade `MerchantOrderQuery` to bundle these along with parcel status counts:

```java
package com.swiftdrop.merchant.api;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;

public interface MerchantOrderQuery {
    PageResult<ParcelSummary> findParcelsByMerchant(UUID merchantId, ParcelStatus status, PageRequest page);
    Optional<ParcelSummary> findByMerchantReference(UUID merchantId, String merchantOrderReference);
    Map<ParcelStatus, Long> countParcelsByStatus(UUID merchantId);
}
```

---

## 4. Key Decisions Agreed in Writing

### 4.1 Approval Lifecycle and States
- **States:**
  - `PENDING`: Initial state upon registration.
  - `APPROVED`: Admin approved. Unlocks machine API credential usage and webhook CRUD.
  - `REJECTED`: Admin rejected with reason. The merchant can view the reason and resubmit.
  - `SUSPENDED`: Admin suspended active merchant. API order intake and webhook delivery are halted.
- **Valid Transitions:**
  - `PENDING → APPROVED` (Admin decision)
  - `PENDING → REJECTED` (Admin decision with reason)
  - `REJECTED → PENDING` (Merchant profile updated and resubmitted)
  - `APPROVED → SUSPENDED` (Admin action with reason)
  - `SUSPENDED → APPROVED` (Admin reinstated)
- **Audit Requirement:**
  Every transition appends a record to `merchant_status_history` (`from_status`, `to_status`, `actor_user_id`, `reason`, `created_at`).

### 4.2 Signing Secret Generation, Encryption and Storage
- Member A generates a cryptographically strong random secret (32 bytes hex/base64) when a webhook endpoint is created.
- The secret is displayed **once** to the merchant in the web UI.
- The secret is stored AES-256-GCM encrypted in `merchant_webhook_endpoints` using a key from environment variable `SWIFTDROP_WEBHOOK_SECRET_KEY`.
- Fields stored: `secret_ciphertext`, `secret_iv`, `secret_key_version`, and `secret_hint` (last 4 characters for display, e.g. `...3a9f`).
- Member B never has to decrypt directly from database entities; Member A's `WebhookSubscriptionQuery.findActiveFor(...)` supplies the decrypted secret in `WebhookSubscriptionView.signingSecret()`.

### 4.3 Webhook Soft Delete
- Deleting a webhook endpoint sets `deleted_at = NOW()` instead of removing the row.
- `WebhookSubscriptionQuery.findActiveFor` ignores soft-deleted endpoints (`deleted_at IS NULL AND enabled = TRUE`).
- Preserves referential integrity for Member B's `webhook_deliveries` and audit tables.

### 4.4 Flyway Migration Version Ranges
The repository uses timestamp-prefixed SQL migrations in `db/migration/<module>/`.
To avoid collisions:
- **Member A Range:** Timestamps `202611041000` to `202611041099`
  - `V202611041000__merchant_create_merchants.sql`
  - `V202611041010__merchant_create_merchant_status_history.sql`
  - `V202611041020__merchant_create_merchant_webhook_endpoints.sql`
  - `V202611041030__merchant_create_merchant_webhook_endpoint_events.sql`
- **Member B Range:** Timestamps `202611042000` and above (for machine API keys, webhook deliveries, retry records).

### 4.5 Identity Module Integration
- Merchant registration registers a user with the `MERCHANT` role.
- `com.swiftdrop.identity.api.UserDirectory.createMerchantUser(fullName, email, rawPassword)` is already present in `identity.api`. No identity API change is needed.

### 4.6 Authorization and Access
- A merchant with `PENDING`, `REJECTED`, or `SUSPENDED` status can authenticate and view their status screen.
- Webhook endpoint CRUD and machine API order creation require `APPROVED` status.

