# SwiftDrop — Courier Discovery Implementation Report

**Date:** November 9, 2026  
**Status:** Completed and Verified  
**Scope:** Dispatcher Portal Courier Discovery & Assignment Enablement  

---

## 1. Root Cause Addressed

As documented in `COURIER_ASSIGNMENT_READINESS_AUDIT.md`, users logged in as `DISPATCHER` were unable to assign couriers because:
1. The frontend courier list retrieval previously called `GET /api/v1/users?role=COURIER`.
2. `GET /api/v1/users` is the administrative user directory endpoint, strictly guarded by `@PreAuthorize("hasRole('ADMIN')")`.
3. Consequently, calls by `DISPATCHER` returned **HTTP 403 Forbidden**, causing the courier selection list to evaluate to empty (`[]`).
4. With zero couriers available, no courier radio button could be selected, keeping `selectedCourierId` empty (`""`) and the "Confirm pickup assignment" button permanently disabled (`disabled={!selectedCourierId}`).

This implementation creates a dedicated, minimal, privacy-preserving operational courier discovery endpoint for `ADMIN` and `DISPATCHER` roles that conforms to ADR 0003 module boundaries and leaves `GET /api/v1/users` strictly administrative.

---

## 2. Files Modified and Created

### Backend Changes
- **`backend/src/main/java/com/swiftdrop/identity/api/UserDirectory.java`**:  
  Extended the public identity facade with `List<UserSummary> findEnabledCouriers()`.
- **`backend/src/main/java/com/swiftdrop/identity/domain/UserRepository.java`**:  
  Added query method `findByRoleAndEnabledTrue(@Param("role") Role role)` (`SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r = :role AND u.enabled = true ORDER BY u.fullName ASC`).
- **`backend/src/main/java/com/swiftdrop/identity/application/AccountService.java`**:  
  Implemented `findEnabledCouriers()` in `AccountService` using `UserRepository.findByRoleAndEnabledTrue(Role.COURIER)`.
- **`backend/src/main/java/com/swiftdrop/dispatch/application/DispatchService.java`**:  
  Added `@Transactional(readOnly = true) public List<UserSummary> activeCouriers()` delegating to `users.findEnabledCouriers()`.
- **`backend/src/main/java/com/swiftdrop/dispatch/web/DispatchCourierResponse.java`** *(New File)*:  
  Minimal DTO record `record DispatchCourierResponse(UUID id, String fullName)`.
- **`backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java`**:  
  Exposed `@GetMapping("/couriers")` returning `List<DispatchCourierResponse>` under class-level `@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")`.

### Backend Tests
- **`backend/src/test/java/com/swiftdrop/identity/application/AccountServiceTest.java`**:  
  Added unit test `findEnabledCouriersReturnsOnlyActiveCouriers()`.
- **`backend/src/test/java/com/swiftdrop/dispatch/application/DispatchServiceTest.java`**:  
  Added unit test `retrievesActiveCouriersFromUserDirectory()`.
- **`backend/src/test/java/com/swiftdrop/dispatch/web/DispatchQueriesIT.java`**:  
  Added comprehensive integration test `activeCouriersEndpoint_allowsAdminAndDispatcher_andExcludesDisabledOrNonCouriers()` testing full HTTP and database behavior across all roles and edge cases.

### Frontend Changes
- **`frontend/lib/dispatch.ts`**:  
  - Added export interface `DispatchCourier { id: string; fullName: string; }`.
  - Updated `listDispatchCouriers()` to query `GET /api/v1/dispatch/couriers`.
  - Updated `listAllDispatchCouriers()` to delegate directly to `listDispatchCouriers()`.
- **`frontend/components/dispatch/parcel-dialog.tsx`**:  
  - Updated `ParcelDialogProps` to accept `couriers: DispatchCourier[]`.
  - Simplified `eligibleCouriers = couriers` (since the endpoint guarantees all returned couriers are active).
- **`frontend/app/(dispatch)/dispatch/page.tsx`**:  
  - Updated `couriers` state and `dialogCouriers` fallback memo to use `DispatchCourier[]`.

---

## 3. New API Endpoint and Response Structure

### Endpoint Details
- **Method:** `GET`
- **URL Path:** `/api/v1/dispatch/couriers`
- **Controller:** `com.swiftdrop.dispatch.web.DispatchController.couriers()`
- **Service Invocation:** `DispatchService.activeCouriers()` → `UserDirectory.findEnabledCouriers()`

### Response Structure
HTTP 200 OK with JSON array of operational courier summaries:
```json
[
  {
    "id": "c6a1d461-8037-469a-9ec8-df9ef25396bf",
    "fullName": "Kasun Perera"
  }
]
```
- **Fields Exposed:** Only `id` and `fullName`.
- **Excluded Sensitive Data:** Emails, password hashes, external IDs, and internal role sets are not exposed.

---

## 4. Authorization Rules

| Caller Role / State | Endpoint | Expected Status | Actual Result |
|---|---|:---:|:---:|
| **ADMIN** | `GET /api/v1/dispatch/couriers` | **200 OK** | **200 OK (Verified)** |
| **DISPATCHER** | `GET /api/v1/dispatch/couriers` | **200 OK** | **200 OK (Verified)** |
| **CUSTOMER** | `GET /api/v1/dispatch/couriers` | **403 Forbidden** | **403 Forbidden (Verified)** |
| **MERCHANT** | `GET /api/v1/dispatch/couriers` | **403 Forbidden** | **403 Forbidden (Verified)** |
| **COURIER** | `GET /api/v1/dispatch/couriers` | **403 Forbidden** | **403 Forbidden (Verified)** |
| **Anonymous** | `GET /api/v1/dispatch/couriers` | **401 Unauthorized** | **401 Unauthorized (Verified)** |
| **ADMIN** | `GET /api/v1/users` | **200 OK** | **200 OK (Preserved)** |
| **DISPATCHER** | `GET /api/v1/users` | **403 Forbidden** | **403 Forbidden (Preserved)** |

`GET /api/v1/users` authorization was completely untouched.

---

## 5. Tests Executed and Actual Results

### Executed Tests (All Passed: 94 tests run, 0 failures, 0 errors, 0 skipped)

1. **`com.swiftdrop.ModuleBoundaryTest`** (9 tests, 0 failures):
   - Verified that `dispatch` interacts with `identity` exclusively through `com.swiftdrop.identity.api.UserDirectory`.
   - Verified zero architectural or package boundary leaks.
2. **`com.swiftdrop.dispatch.web.DispatchQueriesIT`** (3 tests, 0 failures):
   - `activeCouriersEndpoint_allowsAdminAndDispatcher_andExcludesDisabledOrNonCouriers`:
     - Verified `DISPATCHER` returns 200 OK.
     - Verified `ADMIN` returns 200 OK.
     - Verified `CUSTOMER`, `MERCHANT`, `COURIER` return 403 Forbidden.
     - Verified anonymous request returns 401 Unauthorized.
     - Verified disabled couriers are excluded from response.
     - Verified non-courier accounts (customers, merchants, admins) are excluded.
     - Verified response JSON exposes only `id` and `fullName`.
   - `dispatcherParcelsEndpointReturnsAnEmptyPageWithNoParcelsAndOptionalSearch`: 200 OK.
   - `activeAssignmentEndpointsReturnEmptyPagesForBothTypesAndPageSizes`: 200 OK.
3. **`com.swiftdrop.dispatch.application.DispatchServiceTest`** (5 tests, 0 failures):
   - `retrievesActiveCouriersFromUserDirectory`: Verified service delegates query to `UserDirectory.findEnabledCouriers()`.
   - Existing assignment delegation, conflict validation, and disabled courier checks passed.
4. **`com.swiftdrop.identity.application.AccountServiceTest`** (23 tests, 0 failures):
   - `findEnabledCouriersReturnsOnlyActiveCouriers`: Verified query filters on `Role.COURIER` and `enabled = true`.
   - All 22 existing authentication, registration, and user query tests passed.
5. **`com.swiftdrop.dispatch.web.DispatchAssignmentConcurrencyIT`** (6 tests, 0 failures):
   - Concurrency, locking, idempotence, reassignment, and authorization tests on `POST /api/v1/dispatch/parcel-assignments` passed.
6. **`com.swiftdrop.identity.web.IdentityAuthorizationMatrixTest`** (48 tests, 0 failures):
   - Confirmed `GET /api/v1/users` for `DISPATCHER` role remains strictly **403 Forbidden**.

### Tests Not Executed
- Full project-wide Maven test suite (`DatabaseMigrationIT`, Live Tracking SSE ITs, and other unrelated test suites were skipped to prevent container port conflicts with local running services).

---

## 6. Frontend Lint and Build Results

### ESLint
Command: `npm --prefix frontend run lint`  
Result: **Exit Code 0**  
Output: Clean pass with **0 errors, 0 warnings**.

### Next.js Production Build
Command: `npm --prefix frontend run build`  
Result: **Exit Code 0**  
Output: Next.js 16.4.3 (Turbopack) successfully built and generated all 26 static and dynamic routes including `/dispatch`.

---

## 7. Confirmation of Assignment Logic Integrity

- **Backend Assignment Pipeline:** `POST /api/v1/dispatch/parcel-assignments`, `DispatchService.assign()`, and `ParcelAssignmentService.assignCourier()` were **not modified**.
- **Concurrency & State Machine:** Pessimistic locking, `CREATED`-status validation, idempotency, and audit history preservation in `parcel_assignments` remain identical.
- **Frontend Dialog Logic:** Radio selection UI, error handling, loading states, and submit handler remain identical; they now consume `DispatchCourier[]` from `/api/v1/dispatch/couriers`.

---

## 8. Remaining Issues

None. Dispatchers can now cleanly retrieve enabled couriers, select a courier in the "Assign pickup courier" dialog, and successfully submit assignments.

