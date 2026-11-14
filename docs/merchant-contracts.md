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
*Implemented by Member B's SSRF-safe `WebhookDestinationValidator` (SD-409), registered unconditionally in
`merchant.infrastructure.webhook.WebhookClientConfiguration`. It is the only `WebhookUrlValidator` bean; the same
instance checks every outbound webhook request. The earlier fallback bean was removed (F-02): a second bean made
startup fail, and `@ConditionalOnMissingBean` is not reliable on component-scanned classes.*

### 3.2 `WebhookDeliveryQuery` (Owner: Member B)
Used by Member A to display the webhook delivery history screen in the merchant portal.
*Implemented by `merchant.application.WebhookDeliveryHistoryService` (SD-412); the portal can also call the
REST endpoints in §4.10, which add the status filter and the per-series detail.*

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

- Merchant review endpoints (list, detail, approval decisions) require the `ADMIN` role. `DISPATCHER_ADMIN`
  was replaced by separate `ADMIN` and `DISPATCHER` roles; dispatchers do not review merchants.

### 4.7 Parcel Lifecycle of ADR 0009 (hub network, attempts, returns, cancellation)
- Webhook endpoints may subscribe to every `ParcelStatus`: `CREATED`, `PICKED_UP`, `AT_HUB`, `IN_TRANSIT`,
  `OUT_FOR_DELIVERY`, `DELIVERY_ATTEMPT_FAILED`, `DELIVERED`, `RETURNING`, `RETURNED`, `CANCELLED`, `FAILED`
  (migration `V202611131200`). Existing endpoints keep their subscriptions; new statuses are opt-in.
- The `parcel.status-changed` event keeps `schemaVersion` 1 and gains two optional fields: `hubCode`
  (`AT_HUB`, `IN_TRANSIT`) and `attempt` (`OUT_FOR_DELIVERY`, `DELIVERY_ATTEMPT_FAILED`).
  `failureReasonCode` is now also set for `DELIVERY_ATTEMPT_FAILED`. Webhook payloads pass these through;
  receivers must ignore unknown fields (ADR 0004 §2) and must not assume a fixed status list.
- The merchant order API returns the parcel status by name, so the new statuses need no API change.
### 4.8 Parcel Event Consumer and Webhook Delivery Records (SD-410)
- `merchant.infrastructure.messaging.MerchantEventListener` consumes `merchant.parcel-status-changed`;
  `merchant.application.MerchantEventProcessor` applies each merchant parcel's events in sequence order
  (ADR 0004 §4.3). Customer parcels are ignored. Switch: `SWIFTDROP_MERCHANT_EVENTS_CONSUMER_ENABLED`
  (default `true`); tuning `swiftdrop.merchant.events.*` (ADR 0004 §9 defaults, bounds checked at startup).
- Tables (migration `V202611141000`): `merchant_event_states` (last applied sequence, `stuck`),
  `merchant_processed_events`, `merchant_pending_events`, `merchant_event_anomalies`,
  `merchant_webhook_deliveries` (unique per event and endpoint). They replace the `webhook_deliveries` and
  consumer-state names in the implementation contract §5.5/§5.7.
- **Delivery record status vocabulary** (`WebhookDeliveryStatus`, also a database check): `PENDING`,
  `IN_PROGRESS`, `SUCCEEDED`, `FAILED`, `SECRET_UNAVAILABLE`. The consumer only creates `PENDING` records;
  the sender (SD-411) uses exactly these values. `WebhookDeliveryView.deliveryStatus` carries them.
- **Which endpoints:** every enabled, not deleted endpoint of the parcel's merchant subscribed to the event's
  new status, at the time the event is processed.
- **Approval rule:** merchant approval is evaluated **when the consumer processes the event**, not when the
  parcel event was generated. An event of a merchant that is not `APPROVED` at that moment advances the
  sequence but creates no delivery record; it is not sent later.
- **Payload rule (for SD-411):** `payload` is the event envelope as UTF-8 text — the message body exactly as
  received, or for events rebuilt during reconciliation, the envelope serialized by `MerchantEventJson`.
  The sender reads it as UTF-8, signs **exactly those bytes** (`"<timestamp>.<body>"`, ADR 0004 §7.2) and sends
  **the same bytes** as the request body; it never parses and re-serializes the JSON.
- **Anomalies:** an event whose ID is stored for another sequence (`IDENTITY_CONFLICT`), whose sequence is
  stored with another ID (`SEQUENCE_CONFLICT`), whose merchant does not own the parcel (`OWNERSHIP_MISMATCH`)
  or whose sequence was applied by an event no longer kept (`UNKNOWN_DUPLICATE`) is not applied; it is kept
  in `merchant_event_anomalies` with an `ERROR` log line and the `swiftdrop.merchant.events.anomalies{kind}`
  counter. Invalid messages (not a supported `parcel.status-changed` event) go to the `.dlq` at once; other
  failures are tried 3 times in total, then dead-lettered.
- **Stuck parcels and recovery:** a gap that reconciliation cannot close within 5 attempts marks the parcel
  `stuck` (`ERROR` line, `swiftdrop.merchant.events.stuck` counter); its pending events are kept. After the
  cause is fixed, reset it so the reconciler tries again (nothing is deleted):
  ```sql
  UPDATE merchant_pending_events SET reconcile_attempts = 0, next_reconcile_at = now() WHERE parcel_id = '<id>';
  UPDATE merchant_event_states SET stuck = FALSE WHERE parcel_id = '<id>';
  ```

### 4.9 Webhook Sender (SD-411)
- `merchant.infrastructure.webhook.WebhookSender` polls every 5 s, claims up to 20 due records
  (`PENDING` and due, or `IN_PROGRESS` with an expired lease) with `FOR UPDATE SKIP LOCKED` and a fresh
  `lease_token` valid for 2 minutes, and sends them without holding a transaction. The outcome is stored
  only while the record is still `IN_PROGRESS` with that token and an unexpired lease; a sender that lost
  its lease writes nothing. A crash mid-send means the record is sent again after the lease (receivers
  deduplicate by event ID). Switch: `SWIFTDROP_MERCHANT_WEBHOOK_SENDER_ENABLED` (default `true`); tuning
  `swiftdrop.merchant.webhooks.*` (ADR 0004 §9 defaults).
- **Request:** `POST` to the endpoint URL through the SSRF-safe client (ADR 0004 §7.3), body = the record's
  payload as UTF-8 bytes, `Content-Type: application/json`, headers `X-SwiftDrop-Event-Id`,
  `X-SwiftDrop-Event-Type: parcel.status-changed`, `X-SwiftDrop-Timestamp` (Unix seconds, fresh per attempt)
  and `X-SwiftDrop-Signature: v1=<lowercase hex HMAC-SHA256 of "<timestamp>." + raw body>`. The HMAC key is
  the UTF-8 bytes of the signing secret **exactly as shown to the merchant**, including `whsec_`.
  `WebhookSignature.verify` is the reference verifier (constant time, 5 minute tolerance).
- **Send-time checks, before any request:** the endpoint must exist, be enabled, not deleted and belong to the
  record's merchant (else `ENDPOINT_INACTIVE`); the merchant must exist (`MERCHANT_NOT_FOUND`) and be
  `APPROVED` (`MERCHANT_NOT_APPROVED`). These end the record as `FAILED`; Stage 9 redelivery can resend.

| Outcome | Attempt row | Counts as attempt | Record |
|---|---|---|---|
| 2xx | yes | yes | `SUCCEEDED` |
| timeout, network error, 5xx, 408, 429 | yes | yes | `PENDING` again after the backoff, `FAILED` after the 8th attempt |
| 3xx, other 4xx, refused destination | yes | yes | `FAILED` |
| endpoint inactive, merchant not approved or missing | yes | yes | `FAILED` |
| secret cannot be decrypted | no | no | `SECRET_UNAVAILABLE` |

- **Backoff:** 30 s × 2^(attempt − 1), ±20 % jitter, capped at 1 hour. `Retry-After` of a 429 or 503 (seconds
  or HTTP-date) is honored when longer than the backoff, never beyond 1 hour; invalid or past values are ignored.
- **Attempts** (`merchant_webhook_attempts`, migration `V202611151000`): number, time, outcome
  (`SUCCEEDED`, `HTTP_ERROR`, `TIMEOUT`, `NETWORK_ERROR`, `REFUSED_DESTINATION`, `ENDPOINT_INACTIVE`,
  `MERCHANT_NOT_APPROVED`, `MERCHANT_NOT_FOUND`), HTTP status, duration. No bodies, headers, signatures or
  secrets. The record keeps `last_outcome` and `last_http_status` for the history view (SD-412).
- **`SECRET_UNAVAILABLE` recovery:** the sender does not pick these records up again. After the key problem
  is fixed (or the endpoint's secret rotated), re-queue them; Stage 9 adds an admin action, until then:
  ```sql
  UPDATE merchant_webhook_deliveries SET status = 'PENDING', next_attempt_at = now()
  WHERE status = 'SECRET_UNAVAILABLE' AND endpoint_id = '<id>';
  ```
- **Encryption key:** `SWIFTDROP_WEBHOOK_SECRET_KEY` has no default; the backend refuses to start without a key
  of at least 32 bytes. Changing it makes existing secrets undecryptable (their records become
  `SECRET_UNAVAILABLE`); rotate those endpoints' secrets. ADR 0004 §7.4's key ring with key ID as additional
  authenticated data is not implemented yet (single key, no AAD).

### 4.10 Webhook Delivery Management (SD-412, SD-413)
- **Merchant** (role `MERCHANT`, merchant `APPROVED`, scoped to the caller's merchant; another merchant's record
  answers `404`):
  - `GET /api/v1/merchants/me/webhook-deliveries?endpointId=&status=&page=&size=` – `PageResponse` of
    `WebhookDeliveryView`, ordered `created_at DESC, id DESC` (stable on equal timestamps).
  - `GET /api/v1/merchants/me/webhook-deliveries/{id}` – `WebhookDeliveryDetail`: summary, redeliveries used and
    remaining, `redeliverable`, and `series[]` (0 = automatic; 1–3 = manual with `requestedBy`,
    `requestedByRole`, `requestedAt`, `previousStatus`), each with its attempts (number, time, outcome, HTTP
    status, duration).
  - `POST /api/v1/merchants/me/webhook-deliveries/{id}/redeliveries` – `202` with `{deliveryId, series, status}`.
- **Admin** (role `ADMIN`; `DISPATCHER` gets `403`): `GET /api/v1/webhook-deliveries?merchantId=&endpointId=&status=`,
  `GET /api/v1/webhook-deliveries/{id}`, `POST /api/v1/webhook-deliveries/{id}/redeliveries` for every merchant.
- **Never exposed:** the payload, request headers, signatures, secrets, secret hints, lease data.
- **Redelivery rules** (ADR 0004 §7.5): only `FAILED` or `SECRET_UNAVAILABLE`; `409` while `PENDING`/`IN_PROGRESS`,
  for `SUCCEEDED`, when the endpoint is disabled, deleted or not the merchant's, or the merchant is not
  `APPROVED`. The record is locked, so concurrent requests start one series. Accepting it sets the record
  `PENDING`, due now, clears `completed_at` and any old lease (the sender's next claim issues a fresh token; an old
  token cannot store an outcome), increments `manual_redeliveries`, and starts a new series.
- **Limits:** series 0 has up to 8 automatic attempts; each of up to **3** manual redeliveries starts a new series
  of up to 8 attempts (limit and backoff restart per series); a 4th redelivery is `409`. Attempt numbers keep
  increasing across series. The same body and event ID are sent with a fresh timestamp and the current secret.
- **Audit:** `merchant_webhook_redeliveries` (migration `V202611161000`) – delivery, series, requesting user and
  role, previous status, time; `ON DELETE RESTRICT`, never updated. Attempts carry `series` and, for manual series,
  `triggered_by_user_id`.
- **Demo receiver and guide (SD-413):** `tools/webhook-receiver/WebhookReceiver.java` (single Java file; verifies,
  deduplicates, applies the highest sequence, `--fail-first N` before recording) and
  `docs/merchant-integration-guide.md`. Local demo settings `SWIFTDROP_MERCHANT_WEBHOOK_BACKOFF_BASE` and
  `SWIFTDROP_MERCHANT_WEBHOOK_MAX_ATTEMPTS` shorten retries; never for shared or production systems.
