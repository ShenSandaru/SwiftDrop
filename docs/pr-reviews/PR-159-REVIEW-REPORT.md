# PR #159 Review Report

- **PR:** [#159 — `feat(dispatch): add walk-in intake, unbooked walk-in booking, order history, and dispatcher portal UI updates`](https://github.com/ShenSandaru/SwiftDrop/pull/159)
- **Branch:** `feat/dispatch-intake-workflow` → `dev`
- **Status:** `OPEN`
- **Scope:** Full-stack feature (47 files changed across `delivery`, `dispatch`, `identity`, Flyway migrations, tests, and `frontend`)

---

## 1. Summary of Changes

### Walk-in Hub Intake (`CREATED` → `AT_HUB`)
- Added `Parcel.receiveAtHub(...)`, `ParcelHubService.receiveAtHub(...)`, and `POST /api/v1/dispatch/parcels/{id}/hub-intake` so dispatchers/admins can receive pre-booked customer or merchant drop-off parcels directly at the origin hub, skipping courier pickup.
- Added an **Incoming Requests (Customer & Merchant)** queue on `/dispatch` with **Accept & Assign** and **Receive at Hub** actions.

### Unbooked Walk-in Parcel Booking
- Added Flyway migration `V202611171300__delivery_allow_walk_in_customer_parcels.sql` allowing `source = 'CUSTOMER'` parcels without a registered `customer_id`.
- Added `Parcel.forWalkInCustomer(...)`, `ParcelCreation.createWalkInParcel(...)`, `DispatchService.createWalkInParcel(...)`, and `POST /api/v1/dispatch/parcels/walk-in` to book and immediately receive a walk-in parcel at the hub in one transaction.
- Extracted the Customer Book Delivery form into a shared `BookDeliveryForm` component (`frontend/components/delivery/book-delivery-form.tsx`) and wired **Create Walk-in Parcel** into `/dispatch`.

### Order History, Dispatcher UI & Staff Hub Assignment
- Added terminal filtering on `GET /api/v1/dispatch/parcels` and a dedicated **Order History** tab on `/dispatch` with Search, Status, and Source filters.
- Standardized `/dispatch` navigation with an Admin-style underline tab bar above the Dispatch Control Tower card, unified badge styling, concise Dispatch Stages overview cards, Hub Sorting terminology, and `ThemedSelect` filter dropdowns.
- Wrapped `/profile` in role-based `PortalShell` navigation (matching Dispatcher tabs and removing the redundant back link).
- Added Flyway migration `V202611171400__identity_add_hub_id_to_users.sql` for staff hub assignment support and integrated `origin/dev` (including ADR 0011/0012 hub-scoped staff).

---

## 2. Files Changed Overview

| Area | Key Files |
|---|---|
| **Delivery (`api`, `application`, `domain`)** | `DispatchAction.java`, `NewWalkInParcel.java`, `ParcelCreation.java`, `ParcelHubOperations.java`, `ParcelQueries.java`, `MerchantParcelCreation.java`, `ParcelBookingService.java`, `ParcelHubService.java`, `ParcelQueryService.java`, `Parcel.java`, `ParcelLifecycle.java`, `ParcelRepository.java` |
| **Dispatch (`application`, `web`)** | `DispatchService.java`, `DispatchController.java`, `CreateWalkInParcelRequest.java`, `WalkInAddressRequest.java`, `WalkInParcelResponse.java` |
| **Identity (`api`, `application`, `domain`, `web`)** | `UserDirectory.java`, `UserSummary.java`, `AccountService.java`, `User.java`, `UserRepository.java`, `CreateStaffUserRequest.java`, `StaffUserController.java`, `UserResponse.java` |
| **Flyway Migrations** | `V202611171300__delivery_allow_walk_in_customer_parcels.sql`, `V202611171400__identity_add_hub_id_to_users.sql` |
| **Backend Tests** | `MigrationConventionTest.java`, `MerchantParcelCreationTest.java`, `ParcelHubFlowTest.java`, `DispatchServiceTest.java`, `DispatchQueriesIT.java`, `AccountServiceTest.java`, `AdminUserManagementApiTest.java`, `NotificationReconciliationServiceTest.java` |
| **Frontend** | `frontend/app/(dispatch)/dispatch/page.tsx`, `frontend/components/delivery/book-delivery-form.tsx`, `frontend/app/(customer)/customer/new/page.tsx`, `frontend/app/(admin)/admin/users/new/page.tsx`, `frontend/app/(admin)/admin/users/page.tsx`, `frontend/app/profile/page.tsx`, `frontend/components/portal/portal-shell.tsx`, `frontend/components/dispatch/hub-board.tsx`, `frontend/components/delivery/status-badge.tsx`, `frontend/components/ui/select.tsx`, `frontend/lib/api.ts`, `frontend/lib/dispatch.ts` |

---

## 3. Verification

### Backend (Reported)
- Targeted unit tests — **79 / 79 passed**:
  - `MerchantParcelCreationTest` (6)
  - `ParcelBookingServiceTest` (7)
  - `ParcelHubServiceTest` (7)
  - `ParcelQueryServiceTest` (17)
  - `ParcelTest` (28)
  - `DispatchServiceTest` (14)
- `MigrationConventionTest` — **2 passed, 0 failures**.

### Frontend (Reported)
- `npm --prefix frontend run lint` — **passed** (0 errors).
- `npm --prefix frontend run build` — **passed** (TypeScript check + 30/30 pages built).

---

## 4. Review Notes

1. **Integration Tests:** `DispatchQueriesIT` was updated in this PR; ensure `./mvnw -Pintegration verify` (with Docker) is run before merging to `dev` to verify the new Flyway migrations and walk-in/hub-intake flows against PostgreSQL.
2. **Module Boundaries:** New cross-module calls between `dispatch` and `delivery` go through `delivery/api` (`ParcelCreation`, `ParcelHubOperations`, `ParcelQueries`, `NewWalkInParcel`), preserving modular monolith encapsulation.
