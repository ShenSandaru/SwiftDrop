# PR #115 Review Report

- **PR:** [#115 — `test(delivery): update delivery integration tests for dispatch assignment history (ADR 0008)`](https://github.com/ShenSandaru/SwiftDrop/pull/115)
- **Status:** `MERGED` (into `dev` from `dhananjaya-hbc:fix/delivery-tests-assignments`)
- **Scope:** Test setup only (4 test files modified, **no production code changes**)

---

## 1. Summary

After **ADR 0008** (dispatch assignment history) was merged into `dev`, 14 delivery and tracking integration tests failed under `./mvnw -Pintegration verify`. PR #115 updates the test setup for **13** of those tests so they satisfy the new constraints.

---

## 2. Root Cause of Test Failures

Under ADR 0008:
1. A courier can change a parcel's status **only** when an active `parcel_assignments` record exists.
2. Every assignment must reference a **real dispatcher user** (`parcel_assignments.dispatcher_id -> users` foreign key).

The older tests predated these rules:

| Old Test Setup | Error |
|---|---|
| Assigned courier via `UPDATE parcels SET assigned_courier_id = ...` (no `parcel_assignments` row) | `"The parcel has no active courier task"` |
| Called `assignCourier(parcel, courier, UUID.randomUUID())` with a fabricated dispatcher UUID | Foreign key violation on `dispatcher_id` |

---

## 3. Files Changed

| File | Change | Tests Fixed |
|---|---|---|
| `backend/src/test/java/com/swiftdrop/delivery/application/ParcelStatusChangeIT.java` | Assigns via `ParcelAssignment.assignCourier` with a real dispatcher instead of raw SQL | 5 |
| `backend/src/test/java/com/swiftdrop/delivery/application/CourierFlowIT.java` | Creates a `DISPATCHER_ADMIN` user and uses its ID | 4 |
| `backend/src/test/java/com/swiftdrop/delivery/ParcelAuthorizationMatrixIT.java` | Uses the existing admin principal's user as the dispatcher | 3 (all 61 rows) |
| `backend/src/test/java/com/swiftdrop/tracking/LiveTrackingIT.java` | Creates a dispatcher user and uses its ID | 1 |

---

## 4. Verification

- **73 / 73** tests passed in `ParcelStatusChangeIT`, `CourierFlowIT`, `ParcelAuthorizationMatrixIT`, and `LiveTrackingIT` (Testcontainers PostgreSQL 18).
- Note: Run `./mvnw -Pintegration verify` (requires Docker) before merging changes to `delivery`, as `./mvnw clean verify` does not run integration tests.

---

## 5. Open Decision Noted (1 Remaining Failure)

`DeliveryQueriesIT.assignmentWorksWhileCreatedAndIsRefusedAfterPickup` still fails due to a contradiction between ADR 0008 and `ParcelAssignmentService.assignCourier`:
- **ADR 0008 §4 & Dispatcher UI ("Reassign pickup" button):** Expects pickup reassignment while `CREATED` to be **allowed** (closing the prior row as `REASSIGNED`).
- **`ParcelAssignmentService.assignCourier`:** Currently **refuses** reassignment with `"The parcel is already assigned to another courier"` (409), and 3 other tests expect that refusal.
