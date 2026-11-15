# PR #149 — Complete Review, Fix & Verification Report

**PR:** [#149 — feat(staff,dispatch): hub assignment for dispatchers & couriers and hub-scoped dispatch portal](https://github.com/ShenSandaru/SwiftDrop/pull/149)  
**Author:** `vishmibulathsinhala` (Vishmi Bulathsinhala)  
**Source Branch:** `feat/staff-hub-assignment` (`b74bead35140d5fffbcd4f60b89f9a788f08097b`)  
**Target Branch:** `dev` (`origin/dev` at `887ced6`)  
**Date:** 2026-11-15  

---

## 1. PR Scope & Summary

- **Backend (`identity`, `delivery`, `dispatch`):**
  - Adds `users.hub_id` (`V202611171000__identity_add_hub_id_to_users.sql`) and `hubId` support across `AccountService`, `StaffUserController`, `User`, `UserSummary`, `CreateStaffUserRequest`, and `UserResponse`.
  - Enforces 1-dispatcher-per-hub (`409 Conflict`) and 1-hub-per-courier.
  - Scopes `/api/v1/dispatch/**` to the logged-in dispatcher's `hubId` while keeping `ADMIN` cross-hub access.
- **Frontend (`frontend/`):**
  - Adds `/admin/users/new`, updates `/admin/users` and `/dispatch`, and updates `select.tsx`, `lib/api.ts`, and `lib/dispatch.ts`.

---

## 2. Issues Found & Fixes Applied

1. **`UserDirectory` Compatibility & Committed Conflict Markers:**
   - `backend/src/main/java/com/swiftdrop/identity/api/UserDirectory.java`: Restored `findEnabledDispatchers()` and `findEnabledAdmins()` (already implemented in `AccountService.java`).
   - `backend/src/test/java/com/swiftdrop/notification/application/NotificationReconciliationServiceTest.java`: Removed committed `<<<<<<< HEAD` / `>>>>>>>` conflict markers in `StubUserDirectory` and implemented `findEnabledCouriersByHub(UUID)`, `findEnabledDispatchers()`, and `findEnabledAdmins()`.
2. **`DatabaseMigrationIT` Expected Migration Count:**
   - `backend/src/test/java/com/swiftdrop/DatabaseMigrationIT.java`: Updated `assertThat(rows).hasSize(31)` to `hasSize(33)` and updated `rows.get(29)`–`rows.get(32)` to include `202611141200` (from `dev`) and `202611171000` (from PR #149).

---

## 3. Verification Results

- **`git diff --check`:** **PASS** (exit `0`).
- **Frontend (`cd frontend; npm run lint; npx tsc --noEmit; npm run build`):** **PASS** (exit `0`, 30/30 routes generated).
- **Backend Unit Tests (`.\mvnw.cmd test`):** **PASS** (`1,444` tests run, `0` failures, `0` errors).
- **Migration Tests (`.\mvnw.cmd verify -Pintegration "-Dit.test=DatabaseMigrationIT" "-Dtest=MigrationConventionTest"`):** **BUILD SUCCESS** (`MigrationConventionTest`: `2/2`, `DatabaseMigrationIT`: `12/12`, all 33 Flyway migrations applied and Hibernate `validate` succeeded).
- **Pre-existing `dev` Baseline Integration Failures (11 total, out of scope):**
  - `NotificationEventProcessorIT` (`5` errors), `NotificationEventListenerIT` (`3` errors), `NotificationReconciliationIT` (`1` failure): `current transaction is aborted` on `update notification_records set delivery_status=? where id=?` (inherited from `dev` / PR #142).
  - `ParcelAuthorizationMatrixIT` (`2` errors): `DELETE FROM users` violates `notification_records_recipient_user_id_fkey`.

---

## 4. Verdict

**DO NOT MERGE — PARTIALLY WORKING**

- Although PR #149's `UserDirectory` compatibility, `DatabaseMigrationIT`, backend unit tests (`1,444/1,444`), and frontend build pass, the **inter-branch (middle-mile) dispatcher-to-dispatcher handoff logic is not working**.
- PR #149 is **not** being merged into `dev` until the dispatcher-to-dispatcher inter-branch workflow is fixed.
