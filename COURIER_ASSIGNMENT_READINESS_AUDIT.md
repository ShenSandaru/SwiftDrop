# SwiftDrop — Courier Assignment Feature: Read-Only Implementation & API Integration Audit

**Date:** November 9, 2026  
**Audit Scope:** Dispatcher Portal (`/dispatch`) — "Assign pickup courier" dialog & backend assignment pipeline  
**Mode:** READ-ONLY AUDIT (Zero file modifications, zero schema changes, zero database writes)

---

## 1. Executive Summary

An exhaustive audit of the frontend components, API clients, backend controllers, service boundaries, domain aggregates, and authorization matrix was conducted to determine why courier assignment cannot be completed from the Dispatcher Portal (`/dispatch`).

**Key Finding:**  
The end-to-end courier assignment machinery is **fully implemented, structurally integrated, and covered by backend integration tests**. The failure to assign a courier is caused by an **upstream authorization and API discovery boundary mismatch**:

1. When a user logged in as a **DISPATCHER** opens the Dispatcher Portal, the dashboard attempts to populate the courier list by calling `GET /api/v1/users?role=COURIER` (`listAllDispatchCouriers()`).
2. The backend controller method `UserController.listUsers` explicitly enforces `@PreAuthorize("hasRole('ADMIN')")`.
3. Consequently, the backend rejects the Dispatcher's request with **HTTP 403 Forbidden**.
4. Because no couriers can be loaded and a clean/fresh database has zero existing assignment records or active workloads to extract courier IDs from, the dialog's courier selection list is empty (`[]`).
5. The dialog displays *"No enabled couriers found. Create or enable courier accounts first"*, renders no radio selection controls, and leaves `selectedCourierId` empty (`""`).
6. The "Confirm pickup assignment" button enforces `disabled={!canAssign || !selectedCourierId || couriersLoading || isSubmitting}`. Because `selectedCourierId` is empty, the button is **permanently disabled**, preventing any assignment request from being submitted.

---

## 2. Frontend Implementation Audit

Inspection of `frontend/components/dispatch/parcel-dialog.tsx` and `frontend/app/(dispatch)/dispatch/page.tsx`:

| Checkpoint | Status | Evidence & Details |
|---|---|---|
| **Courier selection UI implemented?** | **YES** | Lines 304–363 in `parcel-dialog.tsx` render the Courier Selection section. |
| **Actual courier selection control?** | **YES** | Lines 339–346 render standard `<input type="radio" name="courier" value={courier.id} />` controls. |
| **Loads available courier accounts?** | **YES (Attempted)** | Line 241 in `page.tsx` calls `listAllDispatchCouriers()`. |
| **Real API data or mock/static data?** | **REAL API** | Populated via `listAllDispatchCouriers()` (`/api/v1/users?role=COURIER`) with dynamic fallback to active assignment workloads. No static mock lists exist. |
| **Assign/Confirm button present?** | **YES** | Lines 443–451 in `parcel-dialog.tsx`: `<Button onClick={submitAssignment}>Confirm {assignmentLabel} assignment</Button>`. |
| **Button connected to event handler?** | **YES** | Bound to `onClick={submitAssignment}`. |
| **Calls API method?** | **YES** | `submitAssignment()` awaits `assignDispatchCourier(detail.id, selectedCourierId, assignmentType)`. |
| **Loading state implemented?** | **YES** | `isSubmitting` displays `<Loader2 className="animate-spin" />` and disables the button. `couriersLoading` displays a spinner with *"Loading courier availability…"*. |
| **API errors displayed correctly?** | **YES** | Catches exceptions and renders `submitError` via `<p role="alert">{submitError}</p>`. |
| **Selected courier ID passed correctly?** | **YES** | `selectedCourierId` is passed as the second argument to `assignDispatchCourier`. |
| **Parcel ID passed correctly?** | **YES** | `detail.id` is passed as the first argument to `assignDispatchCourier`. |

### UI Disabling Mechanism

In `parcel-dialog.tsx` (lines 443–451):
```tsx
<Button
  type="button"
  onClick={submitAssignment}
  disabled={!canAssign || !selectedCourierId || couriersLoading || isSubmitting}
>
  {isSubmitting && <Loader2 className="animate-spin" aria-hidden="true" />}
  Confirm {assignmentLabel} assignment
</Button>
```
When `eligibleCouriers.length === 0`:
- The dialog renders:
  ```tsx
  <p className="rounded-lg border border-dashed border-border p-4 text-sm text-muted-foreground">
    No enabled couriers found. Create or enable courier accounts first.
  </p>
  ```
- No radio buttons are generated.
- `selectedCourierId` remains `""`.
- The button condition `!selectedCourierId` evaluates to `true`, keeping the button completely disabled.

---

## 3. Frontend API Integration Audit

Trace of frontend-to-backend communication:

### 1. Courier Account Retrieval
- **Frontend Method:** `listDispatchCouriers(page, size)` and `listAllDispatchCouriers()` in `frontend/lib/dispatch.ts`.
- **HTTP Endpoint:** `GET /api/v1/users?page=0&size=100&role=COURIER`.
- **Backend Handler:** `UserController.listUsers` in `backend/src/main/java/com/swiftdrop/identity/web/UserController.java`.
- **Contract Compatibility:** Response format (`PageResponse<UserResponse>`) matches expected frontend type.
- **Integration Status:** **Fails with HTTP 403 Forbidden** when invoked by a Dispatcher.

### 2. Courier Workload / Availability Retrieval
- **Frontend Method:** `getCourierWorkloads()` in `frontend/lib/dispatch.ts`.
- **HTTP Endpoint:** `GET /api/v1/dispatch/courier-workloads`.
- **Backend Handler:** `DispatchController.courierWorkloads` in `backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java`.
- **Contract Compatibility:** Returns `Map<UUID, Long>` which matches `Record<string, number>`.
- **Integration Status:** **Functional (HTTP 200 OK)** for both Dispatchers and Admins. Returns an empty map `{}` when no parcels are in transit.

### 3. Courier Assignment Submission
- **Frontend Method:** `assignDispatchCourier(parcelId, courierId, type)` in `frontend/lib/dispatch.ts`.
- **HTTP Endpoint:** `POST /api/v1/dispatch/parcel-assignments`.
- **Backend Handler:** `DispatchController.assign` in `backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java`.
- **Request Payload:**
  ```json
  {
    "parcelId": "024189f5-49a9-4044-9ce4-819dd7d87199",
    "courierId": "c6a1d461-8037-469a-9ec8-df9ef25396bf",
    "type": "PICKUP"
  }
  ```
- **Backend DTO:** `DispatchAssignmentRequest(UUID parcelId, UUID courierId, ParcelAssignmentType type)`.
- **Expected Response:** HTTP 201 Created with `ParcelAssignmentSummary` (`id`, `parcelId`, `courierId`, `type`, `status`, `assignedAt`, `completedAt`, `dispatcherId`).
- **Contract Compatibility:** **100% Exact Match**.

---

## 4. Backend Implementation Audit

Inspection of backend dispatch and delivery modules:

### 1. Endpoint & Controller
- **Location:** `com.swiftdrop.dispatch.web.DispatchController`
- **Method:** `assign(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DispatchAssignmentRequest request)`
- **Annotation:** `@PostMapping("/parcel-assignments")`, `@ResponseStatus(HttpStatus.CREATED)`
- **Security:** Class-level `@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")` permits `DISPATCHER`.
- **Caller Extraction:** Extracts `UUID dispatcherId = UUID.fromString(jwt.getSubject())`.

### 2. Application Service Validation (`DispatchService.assign`)
- **Location:** `com.swiftdrop.dispatch.application.DispatchService`
- **Checks:**
  - Validates non-null UUIDs for `parcelId`, `courierId`, `dispatcherId`, and `type`.
  - Verifies courier exists via `users.findUser(courierId)`: throws `DispatchCourierNotFoundException` (mapped to 404) if not found.
  - Verifies user has role `COURIER`: throws `DispatchCourierNotFoundException` if not a courier.
  - Verifies `courier.enabled() == true`: throws `DispatchCourierUnavailableException` (mapped to 409 Conflict) if disabled.

### 3. Domain Aggregate & State Machine (`ParcelAssignmentService.assignCourier`)
- **Location:** `com.swiftdrop.delivery.application.ParcelAssignmentService`
- **Parcel Status Validation:**
  ```java
  if (parcel.getStatus() != ParcelStatus.CREATED) {
      throw new ParcelConflictException("A courier can only be assigned before pickup");
  }
  ```
  Enforces that pickup assignments can only occur while the parcel is in `CREATED` status.
- **Idempotence & Reassignment:**
  - If the parcel is already assigned to the same courier, returns the active assignment idempotently without duplicate records.
  - If assigned to a different courier, marks the existing record as `REASSIGNED` (`current.reassign(now)`) and creates a new active assignment.
- **Aggregate Mutator:** Calls `parcel.assignPickupCourier(courierUserId, now)`, updating `assignedCourierId`, `assignedAt`, and `updatedAt`.
- **Audit History:** Persists a new row in `parcel_assignments` table with `dispatcher_id`, `assigned_at`, `status = 'ACTIVE'`, and `assignment_type = 'PICKUP'`.

---

## 5. Authorization Audit

| Layer | Component | Endpoint / Action | Rule | Dispatcher Permitted? |
|---|---|---|---|:---:|
| **Web Security** | `SecurityConfiguration` | `/api/v1/dispatch/**` | `authenticated()` | **YES** |
| **Web Security** | `SecurityConfiguration` | `/api/v1/users` (GET) | `authenticated()` | **YES** |
| **Controller Method** | `DispatchController` | `POST /api/v1/dispatch/parcel-assignments` | `@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")` | **YES** |
| **Controller Method** | `DispatchController` | `GET /api/v1/dispatch/courier-workloads` | `@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")` | **YES** |
| **Controller Method** | `DispatchController` | `GET /api/v1/dispatch/parcels` | `@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")` | **YES** |
| **Controller Method** | `DispatchController` | `GET /api/v1/dispatch/parcels/{id}/assignment-history` | `@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")` | **YES** |
| **Controller Method** | `UserController` | `GET /api/v1/users` | `@PreAuthorize("hasRole('ADMIN')")` | **NO (403)** |

### Strict Security Test Proof
In `backend/src/test/java/com/swiftdrop/identity/web/IdentityAuthorizationMatrixTest.java:362`:
```java
"GET /api/v1/users: DISPATCHER role returns 403 Forbidden",
```
This test explicitly mandates that `GET /api/v1/users` **must return HTTP 403 Forbidden** for `DISPATCHER`. The endpoint exposes the full administrative user directory and is deliberately withheld from non-administrators.

---

## 6. Runtime Failure Investigation

A query of the live local database confirmed:
1. **Registered Users:**
   - `4d3b20cb-f1e8-4465-8708-62f2427bacf4` -> `admin@swiftdrop.local` (`ADMIN`)
   - `635a6429-8514-47a4-8e77-9c1f2572cd4c` -> `dispatcher2@gmail.com` (`DISPATCHER`)
   - `c6a1d461-8037-469a-9ec8-df9ef25396bf` -> `c1@gmail.com` (`COURIER`, `enabled=true`)
2. **Parcels:**
   - 1 parcel: `024189f5-49a9-4044-9ce4-819dd7d87199` (`SDNTQ67FQDFA`), `status = 'CREATED'`, `assigned_courier_id = NULL`.
3. **Parcel Assignments:**
   - 0 active assignment records.

### Complete Failure Chain

```mermaid
sequenceDiagram
    autonumber
    actor D as Dispatcher (dispatcher2@gmail.com)
    participant UI as Dispatch Dashboard (/dispatch)
    participant API as Frontend API Client (lib/dispatch.ts)
    participant UC as UserController (identity)
    participant DC as DispatchController (dispatch)

    D->>UI: Opens /dispatch
    UI->>API: listAllDispatchCouriers()
    API->>UC: GET /api/v1/users?role=COURIER
    Note over UC: @PreAuthorize("hasRole('ADMIN')")
    UC-->>API: 403 Forbidden
    API-->>UI: Promise rejected (setCouriers([]))

    UI->>API: getCourierWorkloads()
    API->>DC: GET /api/v1/dispatch/courier-workloads
    DC-->>UI: 200 OK (empty map {})

    Note over UI: dialogCouriers fallback evaluates to []
    D->>UI: Clicks "Assign pickup" on parcel SDNTQ67FQDFA
    Note over UI: ParcelDialog opens with couriers=[]
    UI-->>D: Displays "No enabled couriers found. Create or enable courier accounts first."
    Note over D,UI: Radio buttons absent; selectedCourierId is ""; Confirm button disabled
```

---

## 7. Exact Root Cause

The failure is caused by an **architectural gap in courier discovery for dispatch operations**:

1. The frontend Dispatch Dashboard relies on the administrative identity directory endpoint `GET /api/v1/users?role=COURIER` to discover couriers.
2. Under the project's security architecture (ADR 0003 and SD-102), `GET /api/v1/users` is restricted to `ADMIN` to prevent non-admins from browsing user records.
3. The backend `dispatch` module was never given its own authorized operational courier directory endpoint (e.g. `GET /api/v1/dispatch/couriers`).
4. While the dashboard has a heuristic fallback (`dialogCouriers`) that scans active workloads and existing assignments for courier IDs, on a fresh deployment or when couriers have no active assignments, the fallback discovers zero couriers.
5. As a result, the dialog renders zero couriers, no courier can be selected, and the confirmation button remains disabled.

---

## 8. Files and Methods Involved

### Frontend
- `frontend/app/(dispatch)/dispatch/page.tsx`:
  - `listAllDispatchCouriers()` call in `useEffect` (line 241)
  - `dialogCouriers` memoized computation (lines 284–316)
  - `ParcelDialog` invocation (lines 704–720)
- `frontend/components/dispatch/parcel-dialog.tsx`:
  - `ParcelDialog` component (lines 55–456)
  - `submitAssignment()` handler (lines 191–206)
  - Courier selection radio list render (lines 316–351)
  - Confirm button `disabled` guard (lines 443–451)
- `frontend/lib/dispatch.ts`:
  - `listDispatchCouriers()` (line 76)
  - `listAllDispatchCouriers()` (line 81)
  - `assignDispatchCourier()` (line 126)

### Backend
- `backend/src/main/java/com/swiftdrop/identity/web/UserController.java`:
  - `listUsers()` (line 65, `@PreAuthorize("hasRole('ADMIN')")`)
- `backend/src/main/java/com/swiftdrop/identity/api/UserDirectory.java`:
  - Public identity facade interface (missing operational courier query method)
- `backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java`:
  - `assign()` (line 74, `@PostMapping("/parcel-assignments")`)
- `backend/src/main/java/com/swiftdrop/dispatch/application/DispatchService.java`:
  - `assign()` (line 61)
- `backend/src/main/java/com/swiftdrop/delivery/application/ParcelAssignmentService.java`:
  - `assignCourier()` (line 59)
- `backend/src/main/java/com/swiftdrop/delivery/domain/Parcel.java`:
  - `assignPickupCourier()` (line 196)

---

## 9. Recommended Minimal Fix

To resolve this without weakening user privacy or violating ADR 0003 / module boundaries:

### Backend (Recommended Approach)
1. **Extend `UserDirectory` Facade:**  
   Add a query method to `com.swiftdrop.identity.api.UserDirectory`:
   ```java
   List<UserSummary> findActiveCouriers();
   ```
2. **Implement in `IdentityModule`:**  
   Query all users who have the role `COURIER` and `enabled = true`, returning safe operational `UserSummary` objects (`id`, `fullName`, `email`, `roles`, `enabled`).
3. **Add Endpoint to `DispatchController`:**  
   Add an operational endpoint in `com.swiftdrop.dispatch.web.DispatchController`:
   ```java
   @GetMapping("/couriers")
   @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
   List<UserSummary> activeCouriers() {
       return dispatch.activeCouriers();
   }
   ```

### Frontend
Update `frontend/lib/dispatch.ts`:
```ts
export function listDispatchCouriers(): Promise<UserResponse[]> {
  return api.get<UserResponse[]>("/api/v1/dispatch/couriers");
}
```
This allows Dispatchers to cleanly retrieve all enabled couriers for assignment without touching the administrative `/api/v1/users` endpoint.

---

## 10. Final Classification & Readiness

### Classification
**E. Functionality is implemented and integrated, but there is a specific authorization, validation, or state-management issue.**

### Evidence
- **Backend Assignment Pipeline:** Fully implemented, verified by concurrent integration tests (`DispatchAssignmentConcurrencyIT.java`).
- **Frontend Dialog & Event Handler:** Fully implemented with proper loading, disabled states, error banners, and event wiring.
- **Blocking Defect:** Dispatchers cannot populate the courier radio list because courier discovery points to `GET /api/v1/users` (which requires `ADMIN`). With 0 couriers loaded, `selectedCourierId` remains empty, and the UI button remains permanently disabled.

### Readiness
The core assignment feature is **ready and already functional for administrators**. Once an operational courier query endpoint is exposed to the `DISPATCHER` role, courier assignment for Dispatchers will be fully operational.

