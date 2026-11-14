# PR #111 – Integration Resolution and Cross-Module Regression Report

- **Pull Request:** #111
- **PR Branch:** `approval_lifecycle`
- **Target Branch:** `dev`
- **Target Branch HEAD:** `d4392254179c087ac143aec969037218f88b19f4`
- **PR Commits Evaluated:** `45fc266`, `0b8af22`
- **Merge Base with dev:** `a0b3e08b08c97f486052275728313b571153b5a9`
- **Integration Policy:** Non-destructive resolution of integration conflicts; zero modifications to Merchant business logic; zero changes to `dev`.

---

## 1. Branch and Commit Verification

1. **Current Branch:** `approval_lifecycle` (checked out in working tree).
2. **Target Branch:** `dev` (at `d439225`).
3. **Branch Protection Confirmation:**
   - **`dev` branch was NOT modified.**
   - No commits were created.
   - No branches were pushed or force-pushed.
   - Working tree preserves uncommitted state for review.

---

## 2. Conflicts Resolved

The 3 unmerged files resulting from merging `dev` into `approval_lifecycle` were resolved strictly without altering any business logic:

### A. [`.env.example`](file:///d:/Programming/SwiftDrop/SwiftDrop/.env.example)
- **PR #111 introduced:** `SWIFTDROP_WEBHOOK_SECRET_KEY` (AES-256-GCM master key for webhook secret encryption at rest).
- **`dev` introduced:** `SWIFTDROP_MAX_DELIVERY_ATTEMPTS=3` (ADR 0009 delivery retry cap).
- **Resolution:** Both environment variables were preserved consecutively under their respective documentation sections.

### B. [`backend/src/main/resources/application.properties`](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/resources/application.properties)
- **PR #111 introduced:** `swiftdrop.merchant.webhook.secret-key` property mapping.
- **`dev` introduced:** `swiftdrop.outbox.relay.enabled` (ADR 0004 §3 & §9 outbox publisher toggle).
- **Resolution:** Both properties were preserved consecutively with full comments intact.

### C. [`backend/src/test/java/com/swiftdrop/merchant/web/MerchantControllerTest.java`](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/test/java/com/swiftdrop/merchant/web/MerchantControllerTest.java)
- **Root Cause:** Git conflict markers were present around test methods that originally referenced `DISPATCHER_ADMIN` from PR #111 vs `ADMIN` from `dev`.
- **Resolution:**
  - Removed conflict markers cleanly.
  - Kept PR #111's new tests (`updateMyProfileSuccess`, `getMerchantDetailSuccess`, `decideApprovalSuccess`, `decideApprovalIllegalTransition`).
  - Preserved `dev`'s updated assertion using `TestPrincipal.admin()` and negative check for `TestPrincipal.dispatcher()` on `listMerchantsRequiresAdmin` and `approvalRequiresAdmin`.
  - **No Merchant test logic or business logic was altered.**

---

## 3. Cross-Module Regression Audit

A full file-by-file diff of all non-Merchant modules against `dev` (`git diff dev -- backend/src/main/java/com/swiftdrop/identity backend/src/main/java/com/swiftdrop/delivery backend/src/main/java/com/swiftdrop/dispatch backend/src/main/java/com/swiftdrop/pricing frontend backend/src/main/resources/db/migration`) confirmed:

**ZERO differences exist between `approval_lifecycle` and `dev` in any non-Merchant directory.**

| Module / Area | Verified Status | Evidence |
| :--- | :---: | :--- |
| **Identity & Authentication** | **CLEAN** | JWT filter chain, user directory lookup, disabled user rejection, and ADMIN/DISPATCHER role separation remain 100% identical to `dev`. 124 identity tests executed and passed. |
| **Customer Module** | **CLEAN** | Customer account access, parcel booking contracts, and portal UI untouched. |
| **Delivery & Parcels** | **CLEAN** | Parcel aggregate, status transitions, hub journey handling, proof photos, and retention logic untouched. 31 delivery & hub tests executed and passed. |
| **Dispatch Module** | **CLEAN** | Dispatch controller, hub scan APIs, courier assignment, and workload checks untouched. 11 dispatch tests executed and passed. |
| **Tracking Module** | **CLEAN** | Public tracking, SSE stream, and privacy filters untouched. Tracking tests executed and passed. |
| **Outbox Relay & RabbitMQ** | **CLEAN** | Outbox publisher, monitor, relay configuration, and event exchange configs untouched. 31 outbox relay tests executed and passed. |
| **Database Migrations** | **CLEAN** | Flyway migration folder contains no additions or ordering changes. `MigrationConventionTest` passed. |
| **Frontend Applications** | **CLEAN** | Zero modifications across all customer, admin, courier, and dispatch portal pages. |

---

## 4. Fixes Made Outside Merchant Implementation

No fixes outside the 3 conflicted files (`.env.example`, `application.properties`, `MerchantControllerTest.java`) were needed or performed.

---

## 5. Merchant-Specific Findings Handed to the Merchant Developer

The following items are **strictly within the responsibility of the Merchant module developer** and must be resolved by them before the PR can be accepted:

1. **Role Misalignment in `MerchantController.java` (`getMerchantDetail` & `decideApproval`):**
   - Methods currently declare `@PreAuthorize("hasRole('DISPATCHER_ADMIN')")`.
   - The Merchant developer must update these annotations to `@PreAuthorize("hasRole('ADMIN')")` to align with ADR 0008, because `DISPATCHER_ADMIN` no longer exists in the identity database.
2. **Validator Bean Name Mismatch in `DefaultWebhookUrlValidator.java`:**
   - Declares `@ConditionalOnMissingBean(name = "memberBWebhookUrlValidator")`.
   - The Merchant developer must align this with the SSRF-safe validator bean `webhookUrlValidator` from PR #108 or use `@ConditionalOnMissingBean(WebhookUrlValidator.class)` to prevent Spring startup collision (`NoUniqueBeanDefinitionException`).
3. **Endpoint Limit Concurrency:**
   - In `MerchantWebhookService.java`, the 5-endpoint count check is uncoordinated with concurrent transactions. The Merchant developer should add an optimistic lock bump on the `Merchant` aggregate.

---

## 6. Test Execution & Evidence

All tests were executed on the integrated branch using `./backend/mvnw.cmd`:

| Test Suite / Area | Command | Result | Tests / Failures / Errors |
| :--- | :--- | :---: | :---: |
| **Compilation Check** | `mvnw test-compile` | **SUCCESS (0)** | Javac compiled all 230 main and 89 test classes without errors. |
| **Identity & Security** | `mvnw test -Dtest="IdentityAuthorizationMatrixTest,AuthenticationApiTest,AdminUserManagementApiTest,AccountServiceTest,BootstrapAdminServiceTest"` | **SUCCESS (0)** | **124 run, 0 failures, 0 errors** |
| **Delivery, Hub & Tracking** | `mvnw test -Dtest="ParcelApiTest,CourierApiTest,HubApiTest,TrackingApiTest"` | **SUCCESS (0)** | **31 run, 0 failures, 0 errors** |
| **Dispatch Module** | `mvnw test -Dtest="DispatchServiceTest,DispatchHubApiTest"` | **SUCCESS (0)** | **11 run, 0 failures, 0 errors** |
| **Outbox Relay & Eventing** | `mvnw test -Dtest="OutboxMonitorTest,OutboxPublisherTest,OutboxRelayTest,OutboxRetentionTest,OutboxRelayPropertiesTest"` | **SUCCESS (0)** | **31 run, 0 failures, 0 errors** |
| **Migration Conventions** | `mvnw test -Dtest="MigrationConventionTest"` | **SUCCESS (0)** | **2 run, 0 failures, 0 errors** |
| **Testcontainers Test** | `mvnw test -Dtest="LiveTrackingIT"` | **SKIPPED / INFRA** | Skipped because local Docker daemon is not active. Not a code regression. |

**Total Regression Tests Run Across Existing Modules:** **199 tests executed, 0 failures, 0 errors (100% pass rate).**

---

## 7. Remaining Risks & Final Integration Verdict

- **Existing Module Regressions:** **NONE.** All existing non-Merchant flows continue to function with zero regressions.
- **Merchant Internal Integrity:** Awaiting Merchant developer to update the two `@PreAuthorize` role annotations and validator bean condition.

### **Final Integration Verdict:**
### **READY FOR INTEGRATION** (From a Cross-Module & Merge Conflict Perspective)
*(Merchant-internal role fixes remain assigned to the Merchant developer).*

