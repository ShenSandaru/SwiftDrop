# PR #131 Integration Report — Phase 2: dev Integration into feat/dev_merchant

**Date:** 2026-11-14  
**Source Branch:** `feat/dev_merchant`  
**Base / Target Branch:** `dev` (`origin/dev` at `148ad0118546098fef5cbac8148f9ba0782d7797`)  
**Merge Commit:** `8db25d3` (`chore: merge branch 'dev' into feat/dev_merchant resolving migration collision`)  
**Status:** Successfully Integrated & Verified (Local Only — Not Pushed)

---

## 1. Executive Summary

Phase 2 of PR #131 integration has completed cleanly. The latest `origin/dev` branch (incorporating PR #123 user profiles and PR #132 merchant notifications) was merged into `feat/dev_merchant`.

All conflicts, the Flyway migration version collision (`202611141000`), the Spring bean name collision on `MerchantStatusEventListener`, and test mock slice dependencies were resolved. Both the backend and frontend builds and verification suites pass with zero errors.

---

## 2. Integrated Changes & Collision Resolutions

### 2.1 Flyway Migration Collision Resolution
- **Collision:**
  - `origin/dev` introduced: `V202611141000__notification_allow_non_parcel_notifications.sql` (PR #132).
  - `feat/dev_merchant` introduced: `V202611141000__merchant_create_event_consumer_tables.sql` (PR #131).
- **Resolution:**
  - The merchant event consumer migration was renumbered to `V202611141100__merchant_create_event_consumer_tables.sql`.
  - Renamed file: `backend/src/main/resources/db/migration/merchant/V202611141100__merchant_create_event_consumer_tables.sql`.
  - The SQL statements in both migrations remain 100% intact and preserved.
  - Chronological ordering is preserved:
    1. `V202611141000__notification_allow_non_parcel_notifications.sql`
    2. `V202611141100__merchant_create_event_consumer_tables.sql`
    3. `V202611151000__merchant_create_webhook_tables.sql`
    4. `V202611161000__merchant_create_api_keys.sql`
  - Reconciled documentation reference in `docs/merchant-contracts.md` §4.8 to reference `V202611141100`.
  - Reconciled `MigrationConventionTest.java` (expected migrations size 28 in chronological order).
  - Reconciled `DatabaseMigrationIT.java` (`hasSize(28)`).

### 2.2 Spring Component Bean Name Collision Resolution
- **Collision:**
  - Both `com.swiftdrop.merchant.application.MerchantStatusEventListener` and `com.swiftdrop.notification.application.MerchantStatusEventListener` defaulted to bean name `merchantStatusEventListener`, resulting in Spring `ConflictingBeanDefinitionException`.
- **Resolution:**
  - Annotated the notification listener bean explicitly: `@Component("notificationMerchantStatusEventListener")`.
  - Confirmed `MerchantStatusEventListenerTest` passes cleanly.

### 2.3 Spring Mockito Test Slice Resolution
- **Issue:**
  - `AddressService` from the identity module (PR #123) requires `UserAddressRepository`. Merchant web slice tests mock dependencies individually.
- **Resolution:**
  - Added `com.swiftdrop.identity.domain.UserAddressRepository.class` to `@MockitoBean(types = {...})` in:
    - `MerchantApiKeyApiTest.java`
    - `MerchantOrderApiTest.java`
    - `WebhookDeliveryApiTest.java`
    - `MerchantWebhookEndpointControllerTest.java`

---

## 3. Verification Results

### 3.1 Backend
- **Test execution:**
  `mvnw test -Dtest="UserProfileApiTest,MerchantApiKeyApiTest,MerchantOrderApiTest,WebhookDeliveryApiTest,MerchantWebhookEndpointControllerTest,MerchantApprovalServiceTest,WebhookHttpClientTest,WebhookSenderTest,MigrationConventionTest,ModuleBoundaryTest,MerchantStatusEventListenerTest,NotificationEntityUnitTest"`
- **Result:**
  - `Tests run: 165, Failures: 0, Errors: 0, Skipped: 0`
  - `BUILD SUCCESS`

### 3.2 Frontend
- **Lint:** `npm run lint` -> Passed (Exit code 0, no errors)
- **TypeScript & Build:** `npm run build` -> Passed (Exit code 0, 30 static/dynamic routes compiled cleanly)

### 3.3 Git Diff & Marker Check
- `git diff --check` -> Clean, 0 unresolved conflict markers.

---

## 4. Current Git State

- **Branch:** `feat/dev_merchant`
- **Head SHA:** `8db25d3` (`chore: merge branch 'dev' into feat/dev_merchant resolving migration collision`)
- **Upstream:** `origin/feat/dev_merchant` (ahead 19 commits due to remote sync + dev integration)
- **Working Tree:** Clean (no uncommitted or untracked changes)

---

## 5. Next Steps (Pending Approval)

1. Push local `feat/dev_merchant` branch to remote `origin/feat/dev_merchant`:
   `git push origin feat/dev_merchant`
2. Once pushed, verify GitHub PR #131 checks run cleanly on GitHub Actions.
3. Review and approve PR #131 for final merge into `dev`.
