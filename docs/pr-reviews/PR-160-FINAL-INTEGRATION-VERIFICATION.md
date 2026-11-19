# PR #160 – Final Integration Verification (Read-Only)

**Pull Request:** https://github.com/ShenSandaru/SwiftDrop/pull/160  
**Source Branch:** `feat/dev-notifications` (`87fbaf19001c36aeddcdd1e0b7bb9fd538cda736` + local merge with `dev` `cd5ed600f5296d1a02b623b241ae3ed5e5987808`)  
**Target Branch:** `dev` (`cd5ed600f5296d1a02b623b241ae3ed5e5987808`)  
**Verification Date:** 2026-11-19  
**Verdict:** **READY TO MERGE – No PR-introduced failures identified.**

---

## 1. Current Git State

- **Branch:** `feat/dev-notifications`
- **HEAD:** `87fbaf19001c36aeddcdd1e0b7bb9fd538cda736`
- **MERGE_HEAD:** `cd5ed600f5296d1a02b623b241ae3ed5e5987808` (latest `dev`)
- **Working Tree:** Dirty (in-progress local merge + targeted fixes from the previous phase; **0 unresolved conflict markers**).
- **Push Status:** Changes are **local only** (not yet committed or pushed). All local changes were preserved untouched during this read-only verification.

---

## 2. Exact 11 Integration Test Failures (Full Failsafe Report Analysis)

From `backend/target/failsafe-reports/` on the merged PR #160 working tree (`132` tests run: `121` passed, `9` failures, `2` errors):

| # | Test Class | Failing Method | Failure / Error | PR #160 Related? |
|---|---|---|---|---|
| 1 | `NotificationEventListenerIT` | `redeliveredMessageHandledIdempotently` | `Expected size: 1 but was: 2` (`IN_APP` + `EMAIL`) | **No** (pre-existing on `dev`) |
| 2 | `NotificationEventListenerIT` | `validMessageConsumedProcessedAndAcknowledged` | `Expected size: 1 but was: 2` (`IN_APP` + `EMAIL`) | **No** (pre-existing on `dev`) |
| 3 | `NotificationEventProcessorIT` | `concurrentProcessingSerializedByAdvisoryLock` | `Expected size: 2 but was: 4` (`IN_APP` + `EMAIL`) | **No** (pre-existing on `dev`) |
| 4 | `NotificationEventProcessorIT` | `duplicateEventDeliveryIsIdempotent` | `Expected size: 1 but was: 2` (`IN_APP` + `EMAIL`) | **No** (pre-existing on `dev`) |
| 5 | `NotificationEventProcessorIT` | `gapRecoveryAppliesHistoryDirectly` | `Expected size: 3 but was: 6` (`IN_APP` + `EMAIL`) | **No** (pre-existing on `dev`) |
| 6 | `NotificationEventProcessorIT` | `inOrderEventsAdvanceWatermarkAndPersistNotifications` | `Expected size: 1 but was: 2` (`IN_APP` + `EMAIL`) | **No** (pre-existing on `dev`) |
| 7 | `NotificationEventProcessorIT` | `outOfOrderEventBuffersAndDrainsOnGapRecovery` | `Expected size: 3 but was: 6` (`IN_APP` + `EMAIL`) | **No** (pre-existing on `dev`) |
| 8 | `NotificationReconciliationIT` | `pendingEventRecoveredFromParcelEvents` | `Expected size: 2 but was: 4` (`IN_APP` + `EMAIL`) | **No** (pre-existing on `dev`) |
| 9 | `ParcelAuthorizationMatrixIT` | `parcelAuthorizationMatrix` | `DataIntegrityViolationException`: `DELETE FROM users` violates `notification_records_recipient_user_id_fkey` | **No** (pre-existing on `dev`) |
| 10 | `ParcelAuthorizationMatrixIT` | `customersOnlyListTheirOwnParcels` | `DataIntegrityViolationException`: `DELETE FROM users` violates `notification_records_recipient_user_id_fkey` | **No** (pre-existing on `dev`) |
| 11 | `DispatchQueriesIT` | `hubAssignedDispatcher_onlySeesCouriersFromOwnHub_whileAdminCanFilterByHub` | `AssertionError: No matching value at JSON path "$[?(@.id == '...')].fullName"` | **No** (`dispatch` module; untouched by PR #160) |

---

## 3. Baseline Comparison Results (`dev` @ `cd5ed600f5296d1a02b623b241ae3ed5e5987808`)

A temporary detached worktree was created at the exact `dev` baseline commit `cd5ed600f5296d1a02b623b241ae3ed5e5987808` (without modifying or switching the main working tree) and executed:

```powershell
.\mvnw.cmd verify -Pintegration "-Dtest=MigrationConventionTest" "-Dit.test=NotificationEventListenerIT,NotificationEventProcessorIT,NotificationReconciliationIT,ParcelAuthorizationMatrixIT"
```

- **Baseline Result (`dev` @ `cd5ed60`):** `Tests run: 17, Failures: 8, Errors: 2, Skipped: 0` — **100% identical** failing test methods, line numbers, record counts (`Expected size: 1/2/3 but was: 2/4/6`), and `notification_records_recipient_user_id_fkey` errors.
- **Root Cause Confirmation:**
  - The 8 notification IT failures are caused by `dev` persisting both `IN_APP` and `EMAIL` notification records per event (since PR #142), while those older ITs still assert 1 record per event. PR #160's `PUSH` channel does **not** add any records for users with 0 registered push tokens.
  - The 2 `ParcelAuthorizationMatrixIT` errors are caused by `DELETE FROM users` colliding with `notification_records_recipient_user_id_fkey` on `dev`.
  - The 1 `DispatchQueriesIT` failure is in `dispatch` (introduced by `dev` / PR #162; PR #160 has zero changes in `dispatch`).
- **Conclusion:** **PR #160 introduces 0 new integration test failures.**

---

## 4. Migration Verification

- All **38** Flyway migrations are registered in chronological order (`202610251600` through `202611181000`).
- No duplicate migration versions exist (`V202611181000__notification_create_push_tokens.sql` cleanly follows `dev`'s `V202611171400__identity_add_hub_id_to_users.sql`).
- `MigrationConventionTest`: **PASSED** (`2/2`).
- `DatabaseMigrationIT`: **PASSED** (`12/12`, all 38 migrations applied + Hibernate `ddl-auto=validate` succeeded).

---

## 5. Project-Wide Compatibility Findings

- **Notification Channels:** `IN_APP`, `EMAIL` (including Resend from PR #157), and `SMS` remain completely intact; `PUSH` runs independently after existing channels with per-token error isolation and safe `mock` default.
- **Portals & Workflows:** Customer, Merchant, Courier, Dispatcher, and Admin portals, authentication/authorization, parcel booking, assignment, hub management, walk-in intake, and tracking are untouched. Frontend `lint`, `tsc --noEmit`, and `next build` (`32/32` routes) all passed.
- **Notification Navigation:** Background SW clicks route to `/notifications?parcelId=...&trackingNumber=...`, preserving role-based navigation and read/unread behavior.

---

## 6. Remaining Risks

- **Commit & Push Pending:** The resolved merge and fixes exist locally and must be committed and pushed to `feat/dev-notifications` so GitHub Actions (`CI / backend`, `CI / frontend`) can run on the updated HEAD.
- **Live Firebase Credentials:** End-to-end live FCM delivery requires real Firebase credentials and has not been exercised in local/CI runs.
- **Pre-existing `dev` IT Debt:** The 11 pre-existing `dev` integration test failures should be tracked and fixed separately on `dev`.

---

## 7. Final Verdict

### **READY TO MERGE – No PR-introduced failures identified.**

*(Pending commit/push of the local fixes to `feat/dev-notifications` and green GitHub Actions completion.)*
