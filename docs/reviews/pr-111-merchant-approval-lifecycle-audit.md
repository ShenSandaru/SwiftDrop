# PR #111 – Merchant Approval Lifecycle & Webhook Management: Comprehensive Audit

- **Pull Request:** #111
- **Branch:** `approval_lifecycle`
- **Target Branch:** `dev`
- **PR Author:** `ThulaniThehara`
- **Governing Architecture & ADRs:** [ADR 0003](file:///d:/Programming/SwiftDrop/SwiftDrop/docs/adr/0003-modular-monolith-architecture.md), [ADR 0004 §7.3 & §7.4](file:///d:/Programming/SwiftDrop/SwiftDrop/docs/adr/ADR-0004-merchant-module-design.md), [ADR 0008](file:///d:/Programming/SwiftDrop/SwiftDrop/docs/adr/ADR-0008-admin-and-dispatcher-role-separation.md).
- **Audit Mode:** READ-ONLY (No modifications, rebases, commits, or discarded changes).

---

## A. Executive Summary

PR #111 implements **Phase 2 & Phase 3 (Merchant Approval Lifecycle & Status Transitions)** and **Phase 4 (Webhook Endpoint Subscription CRUD & Secret Encryption at Rest)** for the Merchant module.

The core business logic and cryptographic design (AES-256-GCM authenticated secret cipher, state machine transitions, status history audit trail, ownership-scoped webhook management) are fundamentally sound and well-structured. However, **PR #111 CANNOT be merged into `dev` in its current form** due to four major integration and security blockers:
1. **Broken Role Authorization**: Approval endpoints use the obsolete `DISPATCHER_ADMIN` role (which was eliminated in `dev` via ADR 0008). In production, `ADMIN` users will receive HTTP 403 Forbidden on all review endpoints.
2. **SSRF Validator Shadowing Risk**: PR #111 introduces `DefaultWebhookUrlValidator` with a naive conditional bean check (`@ConditionalOnMissingBean(name = "memberBWebhookUrlValidator")`), whereas PR #108's SSRF-safe validator bean is named `webhookUrlValidator`. If both branches are merged, a Spring `NoUniqueBeanDefinitionException` will crash application startup, or registration will bypass SSRF DNS validation.
3. **Endpoint Cap Concurrency Leak**: The 5-endpoint limit check relies on a non-locking `countByMerchantIdAndDeletedAtIsNull` check before insert, allowing concurrent requests to violate the quota.
4. **Merge Conflicts**: In-progress merge conflicts exist in `.env.example`, `application.properties`, and `MerchantControllerTest.java` (which currently prevents the backend test suite from compiling).

---

## B. Change Inventory

### 1. Commits Introduced by PR #111
- `45fc266`: *"approval cycle when registering a merchnat"*
- `0b8af22`: *"created Webhook endpoint configuration CRUD"*

### 2. New Files (16 files)
- `backend/src/main/java/com/swiftdrop/merchant/application/DefaultWebhookUrlValidator.java`
- `backend/src/main/java/com/swiftdrop/merchant/application/MerchantApprovalService.java`
- `backend/src/main/java/com/swiftdrop/merchant/application/MerchantStatusEventListener.java`
- `backend/src/main/java/com/swiftdrop/merchant/application/MerchantWebhookService.java`
- `backend/src/main/java/com/swiftdrop/merchant/application/WebhookSubscriptionQueryImpl.java`
- `backend/src/main/java/com/swiftdrop/merchant/domain/IllegalStateTransitionException.java`
- `backend/src/main/java/com/swiftdrop/merchant/domain/WebhookEndpointLimitExceededException.java`
- `backend/src/main/java/com/swiftdrop/merchant/domain/WebhookEndpointNotFoundException.java`
- `backend/src/main/java/com/swiftdrop/merchant/security/SecretCipher.java`
- `backend/src/main/java/com/swiftdrop/merchant/web/ApprovalDecisionRequest.java`
- `backend/src/main/java/com/swiftdrop/merchant/web/CreateWebhookEndpointRequest.java`
- `backend/src/main/java/com/swiftdrop/merchant/web/CreateWebhookEndpointResponse.java`
- `backend/src/main/java/com/swiftdrop/merchant/web/MerchantDetailResponse.java`
- `backend/src/main/java/com/swiftdrop/merchant/web/MerchantProfileUpdateRequest.java`
- `backend/src/main/java/com/swiftdrop/merchant/web/MerchantWebhookEndpointController.java`
- `backend/src/main/java/com/swiftdrop/merchant/web/RotateSecretResponse.java`
- `backend/src/main/java/com/swiftdrop/merchant/web/UpdateWebhookEndpointRequest.java`
- `backend/src/main/java/com/swiftdrop/merchant/web/WebhookEndpointResponse.java`
- Unit/Mock test files: `DefaultWebhookUrlValidatorTest.java`, `MerchantApprovalServiceTest.java`, `MerchantWebhookServiceTest.java`, `WebhookSubscriptionQueryImplTest.java`, `SecretCipherTest.java`, `MerchantWebhookEndpointControllerTest.java`.

### 3. Modified Files (7 files)
- `.env.example`
- `backend/src/main/resources/application.properties`
- `backend/src/main/java/com/swiftdrop/merchant/api/MerchantStatus.java`
- `backend/src/main/java/com/swiftdrop/merchant/domain/Merchant.java`
- `backend/src/main/java/com/swiftdrop/merchant/domain/MerchantWebhookEndpoint.java`
- `backend/src/main/java/com/swiftdrop/merchant/domain/MerchantWebhookEndpointRepository.java`
- `backend/src/main/java/com/swiftdrop/merchant/web/MerchantController.java`
- `backend/src/main/java/com/swiftdrop/merchant/web/MerchantExceptionHandler.java`
- `backend/src/test/java/com/swiftdrop/merchant/web/MerchantControllerTest.java`

### 4. Database Schema Changes
- None in this PR. All underlying database tables (`merchants`, `merchant_status_history`, `merchant_webhook_endpoints`, `merchant_webhook_endpoint_events`) were already established in baseline migration files (`V202611041000` through `V202611041030`).

### 5. New API Endpoints
- `PATCH /api/v1/merchants/me`: Self-service profile update (resubmits `REJECTED` $\to$ `PENDING`).
- `GET /api/v1/merchants/{id}`: Full detail and status audit history.
- `POST /api/v1/merchants/{id}/approval-decisions`: Admin decisions (`APPROVE`, `REJECT`, `SUSPEND`).
- `POST /api/v1/merchants/me/webhook-endpoints`: Register endpoint.
- `GET /api/v1/merchants/me/webhook-endpoints`: List active endpoints.
- `GET /api/v1/merchants/me/webhook-endpoints/{id}`: Get endpoint details (no plaintext secret).
- `PATCH /api/v1/merchants/me/webhook-endpoints/{id}`: Update endpoint URL, description, events, enabled.
- `DELETE /api/v1/merchants/me/webhook-endpoints/{id}`: Soft-delete endpoint.
- `POST /api/v1/merchants/me/webhook-endpoints/{id}/secret-rotations`: Rotate signing secret.

---

## C. Audit Findings

### Finding F-01: Deprecated `DISPATCHER_ADMIN` Role Usage Blocks Admin Decisions
- **Severity:** CRITICAL
- **Affected Location:** [`MerchantController.java#L111`](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantController.java#L111), [`MerchantController.java#L120`](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantController.java#L120)
- **Evidence:**
  ```java
  @GetMapping("/{id}")
  @PreAuthorize("hasRole('DISPATCHER_ADMIN')")
  public MerchantDetailResponse getMerchantDetail(@PathVariable UUID id) { ... }

  @PostMapping("/{id}/approval-decisions")
  @PreAuthorize("hasRole('DISPATCHER_ADMIN')")
  public MerchantStatusResponse decideApproval(...) { ... }
  ```
- **Why it is a problem:**
  In `dev`, [ADR 0008](file:///d:/Programming/SwiftDrop/SwiftDrop/docs/adr/ADR-0008-admin-and-dispatcher-role-separation.md) and Flyway migration `V202611101200` permanently eradicated the `DISPATCHER_ADMIN` role from the `roles` table, splitting it into `ADMIN` and `DISPATCHER`.
- **Impact:**
  Any authenticated administrator possessing the `ADMIN` role will be denied access with **HTTP 403 Forbidden**. Merchant applications cannot be reviewed, inspected, approved, or suspended.
- **Recommended Correction:**
  Change `@PreAuthorize("hasRole('DISPATCHER_ADMIN')")` to `@PreAuthorize("hasRole('ADMIN')")` on both methods.

---

### Finding F-02: Validator Bean Collision / SSRF Protection Bypass
- **Severity:** HIGH
- **Affected Location:** [`DefaultWebhookUrlValidator.java#L22-L24`](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/application/DefaultWebhookUrlValidator.java#L22-L24)
- **Evidence:**
  ```java
  @Component
  @ConditionalOnMissingBean(name = "memberBWebhookUrlValidator")
  public class DefaultWebhookUrlValidator implements WebhookUrlValidator
  ```
- **Why it is a problem:**
  In PR #108 (SD-409, SSRF-Safe Client), the bean declared in `WebhookClientConfiguration` is:
  ```java
  @Bean
  WebhookDestinationValidator webhookUrlValidator(...)
  ```
  The bean name is `webhookUrlValidator`, **NOT** `memberBWebhookUrlValidator`. Because the bean name does not match, Spring's `@ConditionalOnMissingBean(name = "memberBWebhookUrlValidator")` evaluates to true!
- **Impact:**
  1. If PR #108 and PR #111 are combined, Spring Boot will encounter two beans implementing `WebhookUrlValidator` (`defaultWebhookUrlValidator` and `webhookUrlValidator`), triggering a fatal startup failure: `NoUniqueBeanDefinitionException`.
  2. If `DefaultWebhookUrlValidator` were injected, it only checks that the string begins with `https://` without checking IP ranges or DNS resolution, allowing merchants to register internal network IPs (e.g., `https://127.0.0.1`, `https://169.254.169.254`, `https://10.0.0.1`) at registration time.
- **Recommended Correction:**
  Either delete `DefaultWebhookUrlValidator.java` once PR #108 is merged, or change the condition to `@ConditionalOnMissingBean(WebhookUrlValidator.class)` so that `WebhookDestinationValidator` automatically takes precedence.

---

### Finding F-03: Concurrency Race Condition in Active Endpoint Limit Enforcement
- **Severity:** MEDIUM
- **Affected Location:** [`MerchantWebhookService.java#L74-L77`](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/application/MerchantWebhookService.java#L74-L77)
- **Evidence:**
  ```java
  long activeCount = endpointRepository.countByMerchantIdAndDeletedAtIsNull(merchant.getId());
  if (activeCount >= MAX_ENDPOINTS_PER_MERCHANT) {
      throw new WebhookEndpointLimitExceededException(MAX_ENDPOINTS_PER_MERCHANT);
  }
  // ...
  MerchantWebhookEndpoint saved = endpointRepository.save(endpoint);
  ```
- **Why it is a problem:**
  The check-then-act sequence executes without database-level concurrency control (such as a pessimistic write lock or an optimistic version check on the parent `Merchant` aggregate).
- **Impact:**
  Two concurrent endpoint creation requests for the same merchant at count = 4 will both read count = 4, pass the check, and insert endpoints, exceeding the maximum cap.
- **Recommended Correction:**
  Trigger an optimistic lock check on the `Merchant` entity (`merchant.bumpVersion()` or `save(merchant)`) or lock the merchant row during endpoint creation to guarantee serial quota evaluation.

---

### Finding F-04: Hardcoded Fallback Secret Key in `application.properties`
- **Severity:** LOW
- **Affected Location:** [`application.properties#L62`](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/resources/application.properties#L62)
- **Evidence:**
  ```properties
  swiftdrop.merchant.webhook.secret-key=${SWIFTDROP_WEBHOOK_SECRET_KEY:swiftdrop-local-dev-webhook-secret-key-32bytes!}
  ```
- **Why it is a problem:**
  Having a default fallback key in `application.properties` means that if a staging/production deployment fails to inject `SWIFTDROP_WEBHOOK_SECRET_KEY`, the application will boot and silently encrypt webhook secrets with the public repository fallback key.
- **Impact:**
  Potential exposure of webhook signing secrets if production runs without explicitly declared environment variables.
- **Recommended Correction:**
  In production profiles or container environments, ensure `SWIFTDROP_WEBHOOK_SECRET_KEY` is strictly required without a hardcoded default, or add a startup validation check in `SecretCipher` warning or halting when the fallback key is detected in non-test profiles.

---

## D. Merge Conflict Analysis

There are **3 files with active Git merge conflicts** between `approval_lifecycle` and `dev`:

### 1. `.env.example`
- **What PR #111 introduced:**
  ```ini
  # Merchant module (optional). Master AES-256-GCM key for encrypting webhook signing secrets at rest.
  # SWIFTDROP_WEBHOOK_SECRET_KEY=change-me-to-a-32-byte-secret-key-12345678
  ```
- **What `dev` introduced:**
  ```ini
  # Delivery attempts before a parcel goes back to the sender (ADR 0009), 1 to 5.
  # SWIFTDROP_MAX_DELIVERY_ATTEMPTS=3
  ```
- **Resolution:** Retain both settings consecutively.
- **Risk if misresolved:** Loss of ADR 0009 configuration or missing webhook encryption documentation.

### 2. `backend/src/main/resources/application.properties`
- **What PR #111 introduced:**
  ```properties
  # Merchant module. Master AES-256-GCM encryption key for webhook signing secrets at rest (docs/merchant-contracts.md).
  swiftdrop.merchant.webhook.secret-key=${SWIFTDROP_WEBHOOK_SECRET_KEY:swiftdrop-local-dev-webhook-secret-key-32bytes!}
  ```
- **What `dev` introduced:**
  ```properties
  # Outbox relay (delivery module, ADR 0004 §3, §9).
  swiftdrop.outbox.relay.enabled=${SWIFTDROP_OUTBOX_RELAY_ENABLED:true}
  ```
- **Resolution:** Keep both properties consecutively.
- **Risk if misresolved:** Delivery outbox relay disabled or merchant webhook secret cipher failing to boot.

### 3. `backend/src/test/java/com/swiftdrop/merchant/web/MerchantControllerTest.java`
- **What PR #111 introduced:**
  Tests for `PATCH /api/v1/merchants/me`, `GET /api/v1/merchants/{id}`, and `POST /api/v1/merchants/{id}/approval-decisions`, all expecting `TestPrincipal.dispatcherAdmin()`.
- **What `dev` introduced:**
  Role separation (ADR 0008), replacing `dispatcherAdmin()` with `admin()` and adding negative assertions for `TestPrincipal.dispatcher()`.
- **Resolution:**
  - Keep PR #111's new test cases for profile update, detail lookup, and approval decisions.
  - Update all `TestPrincipal.dispatcherAdmin()` calls to `TestPrincipal.admin()`.
  - Retain `dev`'s negative checks asserting that `TestPrincipal.dispatcher()` receives 403 Forbidden.
- **Risk if misresolved:** Test suite fails to compile, or tests mask role authorization bypasses.

---

## E. Security & Architecture Assessment

| Area | Status | Assessment |
| :--- | :---: | :--- |
| **State Machine Transitions** | **PASS** | Valid transitions enforced strictly: `PENDING` $\to$ `APPROVED`/`REJECTED`; `APPROVED` $\to$ `SUSPENDED`; `SUSPENDED` $\to$ `APPROVED`; `REJECTED` $\to$ `PENDING`. Invalid jumps throw `IllegalStateTransitionException` (HTTP 409). |
| **Audit Log Immutability** | **PASS** | Every decision and resubmission persists an immutable row to `merchant_status_history` recording `from_status`, `to_status`, `actor_user_id`, `reason`, and timestamp. |
| **Tenant Isolation** | **PASS** | All `/api/v1/merchants/me/webhook-endpoints/**` operations look up the merchant record exclusively using `MerchantAccessSecurity.extractUserId(authentication)`. Endpoint IDs are always queried with `merchantId = merchant.getId()`. Cross-merchant tampering is impossible. |
| **Signing Secret Protection** | **PASS** | Plaintext secret is generated with `SecureRandom` (32 bytes url-safe base64). Encrypted via `SecretCipher` (AES-256-GCM with unique 12-byte IV). Returned in plain text **only once** on creation/rotation. List and detail responses omit secret and show only a 4-character non-sensitive hint. |
| **SSRF Protection** | **FLAGGED** | PR #111's fallback validator checks only string syntax. Full SSRF and DNS rebinding defense is provided by PR #108 (`WebhookDestinationValidator`), but the integration requires fixing the bean condition name to avoid collision. |
| **Module Encapsulation** | **PASS** | Merchant entities and services remain cleanly contained within `com.swiftdrop.merchant`. Events are dispatched via Spring `ApplicationEventPublisher` (`MerchantStatusChanged`). |

---

## F. Existing Module Regression Assessment

- **Identity & Authentication:** No regression. PR #111 does not alter `SecurityConfiguration.java` or token filters.
- **Delivery Module:** No regression. Parcel workflows, proof photos, outbox events, and delivery attempts are completely untouched.
- **Dispatch Module:** No regression. Courier discovery, workloads, and dispatch hubs operate independently.
- **Pricing Module:** No regression. Pricing rules and zones are unaffected.

---

## G. Test Evidence

### Test Execution Summary
- **Command:** `.\backend\mvnw.cmd -f backend/pom.xml test -Dtest="DefaultWebhookUrlValidatorTest,MerchantApprovalServiceTest,MerchantWebhookServiceTest,WebhookSubscriptionQueryImplTest,SecretCipherTest,MerchantWebhookEndpointControllerTest"`
- **Result:** **COMPILATION FAILURE** (Exit code `1`).
- **Cause:** Unresolved merge markers inside `MerchantControllerTest.java` halted javac test compilation before test execution could begin:
  ```
  [ERROR] MerchantControllerTest.java:[223,1] illegal start of type (merge conflict markers)
  ```
- **Inspected Unit & Webhook Controller Tests:**
  1. `MerchantApprovalServiceTest`: Thoroughly covers approve, reject, suspend, illegal transitions, reason requirements, profile updates, and rejection-to-pending resubmission.
  2. `MerchantWebhookServiceTest`: Thoroughly covers endpoint creation, secret generation, 5-endpoint cap, tenant-isolated lookups, soft deletion, and secret rotation.
  3. `SecretCipherTest`: Confirms AES-256-GCM round-trip encryption/decryption, distinct IV generation per call, and rejection of invalid key versions.
  4. `MerchantWebhookEndpointControllerTest`: Verifies HTTP status codes, security role checks, and secret masking in JSON responses.

---

## H. Required Fixes Before Approval

1. [ ] **Update Authorization Annotations in `MerchantController.java`**:
   - Replace `@PreAuthorize("hasRole('DISPATCHER_ADMIN')")` with `@PreAuthorize("hasRole('ADMIN')")` on `getMerchantDetail` and `decideApproval`.
2. [ ] **Resolve Webhook URL Validator Bean Conflict**:
   - Update `DefaultWebhookUrlValidator.java` annotation from `@ConditionalOnMissingBean(name = "memberBWebhookUrlValidator")` to `@ConditionalOnMissingBean(WebhookUrlValidator.class)` (or remove the fallback class upon integrating PR #108).
3. [ ] **Resolve Git Merge Conflicts**:
   - Cleanly resolve `.env.example`, `application.properties`, and `MerchantControllerTest.java` preserving all incoming changes and updating tests to use `TestPrincipal.admin()`.
4. [ ] **Address Concurrency on Endpoint Quota**:
   - Ensure concurrent endpoint creation cannot exceed the 5-endpoint cap.
5. [ ] **Execute Clean Maven Test Suite**:
   - Recompile and execute the complete merchant test suite and regression tests to verify 100% pass rate.

---

## I. Final Verdict

### **Verdict: NOT READY – MAJOR ISSUES**

**Justification:**
While the domain and cryptographic implementation within the Merchant module is high quality, PR #111 cannot be merged into `dev` because:
1. It relies on the obsolete `DISPATCHER_ADMIN` role, completely breaking admin approval in production.
2. It introduces a validator bean naming mismatch that conflicts with the SSRF-safe client from PR #108.
3. It has active unmerged Git conflicts that break the backend build.

These issues are straightforward to rectify in a dedicated integration task before PR merge.

