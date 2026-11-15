# PR #139 Integration Review Report

**PR:** [#139 — Feat/customer book delivery](https://github.com/ShenSandaru/SwiftDrop/pull/139)  
**Author:** `AsiriJayakodi` (Asiri Indrajith)  
**Source Branch:** `feat/customer_bookDelivery` (`850edeeaf0e54db189bfa005ee152392be46e4d3`)  
**Target Branch:** `dev` (`origin/dev` at `eb8d1fd12d3e2f3a32936f1f26849d2dd39eac8d`)  
**Audit Date:** 2026-11-14  
**Audit Mode:** Read-Only  

---

## 1. PR Metadata and Git State

- **Current local branch:** `feat/customer_bookDelivery`
- **Current local HEAD:** `850edeeaf0e54db189bfa005ee152392be46e4d3` (matches PR #139 `headRefOid`)
- **Latest `origin/dev`:** `eb8d1fd12d3e2f3a32936f1f26849d2dd39eac8d` (`Merge pull request #136 from ShenSandaru/feat/admin_pricing/zones`)
- **PR Status:** `OPEN`
- **GitHub Mergeable / MergeStateStatus:** `UNKNOWN` / `UNKNOWN`
- **Working-tree state at audit time:** Contains uncommitted local changes and two `UU` unmerged-path markers (`District.java`, `MigrationConventionTest.java`) plus untracked `docs/pr-reviews/PR-136-*.md` files from prior local work; no files were modified during this audit.
- **GitHub Actions CI:**
  - `frontend`: **SUCCESS** (1m 31s)
  - `backend`: **FAILURE** (1m 18s)

---

## 2. PR Scope Summary

PR #139 implements the end-to-end **Customer Book Delivery** experience, connecting the Customer Portal booking flow to admin-managed Districts, Hub Network, Delivery Zones, and Pricing Configuration:

- **Backend (`delivery` & `pricing`):**
  - Adds `GET /api/v1/delivery-zones` (`DeliveryZoneController`) backed by `HubNetwork.servedZoneIds()` and `ZoneDirectory.findActiveZones()` so customers only see active zones served by an active hub.
  - Updates `District.java` (`latitude`, `longitude`) from `Double` to `BigDecimal` with `@Column(precision = 9, scale = 6)` and converts to `Double` in `DistrictResponse.java`.
  - Fixes a syntax error in `MigrationConventionTest.java` (removes orphaned `"identity/V202611131900__identity_add_phone_number_to_users.sql"),` line).
- **Frontend (`frontend/`):**
  - Adds `listAvailableZones()` in `frontend/lib/delivery.ts`.
  - Adds searchable `DistrictCombobox` and `ZoneCombobox` with cascading district-to-zone filtering in `frontend/components/delivery/address-fields.tsx`.
  - Rewrites `frontend/app/(customer)/customer/new/page.tsx` into a 3-step booking flow (Pickup Origin, Delivery Destination, Parcel Size & Weight) with a sticky fare summary sidebar, live `PricingConfig` surcharges, and debounced `POST /api/v1/price-quotes`.

---

## 3. Changed-File Analysis

`git diff origin/dev...850edeeaf0e54db189bfa005ee152392be46e4d3 --stat` shows **8 files** changed (`+1,033 / -193`):

| Module / Area | File | Purpose |
|---|---|---|
| `delivery` | `backend/src/main/java/com/swiftdrop/delivery/application/HubNetwork.java` | Adds `servedZoneIds()` returning active hub `zoneId`s |
| `delivery` | `backend/src/main/java/com/swiftdrop/delivery/web/DeliveryZoneController.java` | New `GET /api/v1/delivery-zones` endpoint |
| `pricing` | `backend/src/main/java/com/swiftdrop/pricing/domain/District.java` | Changes `latitude`/`longitude` to `BigDecimal` (`precision = 9, scale = 6`) |
| `pricing` | `backend/src/main/java/com/swiftdrop/pricing/web/DistrictResponse.java` | Converts `BigDecimal` coordinates via `doubleValue()` |
| `test` | `backend/src/test/java/com/swiftdrop/MigrationConventionTest.java` | Removes orphaned migration tuple string |
| `frontend` | `frontend/lib/delivery.ts` | Adds `listAvailableZones()` |
| `frontend` | `frontend/components/delivery/address-fields.tsx` | Adds `DistrictCombobox`, `ZoneCombobox`, cascading selectors |
| `frontend` | `frontend/app/(customer)/customer/new/page.tsx` | 3-step customer booking UI with live pricing & quote sidebar |

---

## 4. Backend Review

- **Module boundaries:** `DeliveryZoneController` (`delivery`) depends only on `HubNetwork` (`delivery`) and `ZoneDirectory` (`pricing.api`), respecting the modular-monolith boundary.
- **Entities & Hibernate 7 compatibility:** Changing `District.latitude` and `District.longitude` to `BigDecimal` with `@Column(precision = 9, scale = 6)` resolves the Hibernate 7 `IllegalArgumentException` and `SchemaManagementException` against `NUMERIC(9,6)`.
- **Missing test coverage:** `DeliveryZoneController` and `HubNetwork.servedZoneIds()` have no dedicated unit or API tests in this PR.

---

## 5. Frontend Review

- **UI & Design System:** `customer/new/page.tsx` and `address-fields.tsx` use shadcn `Card`, `Input`, `Label`, `Button`, and `Alert` components aligned with the existing Island Teal theme.
- **Resilience:** `customer/new/page.tsx` loads `listAvailableZones()` with a fallback to `listZones()`, filters districts to those with active hub-served zones, and debounces `getQuote()`.
- **CI:** The `frontend` GitHub Actions job passed (`1m 31s`).

---

## 6. Database and Migration Review

- **No Flyway migrations added or modified** in PR #139.
- `MigrationConventionTest.java` removes an orphaned string literal that was present on `origin/dev`.

---

## 7. Security Review

- `DeliveryZoneController` exposes `GET /api/v1/delivery-zones` returning `List<ServiceZone>` (publicly/customer-readable active zone metadata, consistent with `GET /api/v1/zones`). No sensitive data or IDOR risk identified.

---

## 8. Compatibility with Latest `dev`

- `origin/dev` (`eb8d1fd`) has PR #136 merged, which introduced `DistrictRepository` and `PricingConfigRepository` dependencies into `pricing` controllers/services without updating the 26 database-free `@SpringBootTest` API/context tests.
- Consequently, PR #139's `backend` CI job fails on `ApplicationContext` startup in those `@SpringBootTest` classes (e.g., `TrackingStreamApiTest`).

---

## 9. CI and Test Coverage Assessment

- **Frontend CI:** **PASS** (`1m 31s`).
- **Backend CI:** **FAIL** (`1m 18s`) — `ApplicationContext` load failures in database-free `@SpringBootTest` tests due to missing `@MockitoBean` declarations on `origin/dev` (and PR #139).
- **New endpoint coverage:** `GET /api/v1/delivery-zones` (`DeliveryZoneController`) has no unit or MockMvc API test.

---

## 10. Documentation Impact

- No ADR or `README.md` changes required; `docs/project-status.md` can be updated after integration.

---

## 11. Findings Classified by Priority

- **P1 – Backend CI Failure (`ApplicationContext` startup):**
  - *Location:* Database-free `@SpringBootTest` classes on `origin/dev` and PR #139 (e.g., `TrackingStreamApiTest`).
  - *Cause:* Missing `@MockitoBean` declarations for `DistrictRepository` and `PricingConfigRepository` (and `UserAddressRepository` / `Merchant*Repository` / `NotificationRecordRepository` in `DistrictApiTest` and `PricingRuleApiTest`).
- **P2 – Missing Test Coverage for New Endpoint:**
  - *Location:* `backend/src/main/java/com/swiftdrop/delivery/web/DeliveryZoneController.java` and `HubNetwork.servedZoneIds()`.
  - *Recommendation:* Add unit/API tests verifying that `GET /api/v1/delivery-zones` filters out inactive zones and zones without an active hub.

---

## 12. Required Fixes Before Integration

1. Ensure `dev` (or PR #139 rebased onto updated `dev`) includes the `@MockitoBean` fixes for all database-free `@SpringBootTest` classes so `backend` CI passes.
2. Add unit/MockMvc test coverage for `DeliveryZoneController` / `HubNetwork.servedZoneIds()`.

---

## 13. Recommended Integration Strategy

1. Land the `dev` `@SpringBootTest` `@MockitoBean` fixes first (or apply them on `feat/customer_bookDelivery`), then rebase/merge `dev` into `feat/customer_bookDelivery`.
2. Re-run `.\mvnw.cmd verify -Pintegration` and confirm both `backend` and `frontend` CI checks pass.
3. Merge PR #139 into `dev`.

---

## 14. Final Merge-Readiness Verdict

**Verdict: READY AFTER FIXES**

The PR's own feature implementation (`DeliveryZoneController`, `District` `BigDecimal` coordinate mapping, `MigrationConventionTest` syntax fix, and the 3-step Customer Book Delivery frontend) is clean and passes `frontend` CI, but `backend` CI currently fails due to missing `@MockitoBean` declarations in database-free `@SpringBootTest` tests inherited from `dev`, and `DeliveryZoneController` lacks dedicated tests.
