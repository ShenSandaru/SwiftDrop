# PR #139 – Fix and Verification Report

**PR:** [#139 — Feat/customer book delivery](https://github.com/ShenSandaru/SwiftDrop/pull/139)  
**Branch:** `feat/customer_bookDelivery` (`850edeeaf0e54db189bfa005ee152392be46e4d3`)  
**Date:** 2026-11-14  
**External Backup:** `D:\Programming\SwiftDrop\backup-pr136-20261114-1739`  

---

## 1. Conflict Resolution Details

Both unmerged (`UU`) paths were resolved and staged (`git add`):
1. `backend/src/main/java/com/swiftdrop/pricing/domain/District.java`: Kept PR #139's `BigDecimal latitude` / `BigDecimal longitude` with `@Column(precision = 9, scale = 6)`.
2. `backend/src/test/java/com/swiftdrop/MigrationConventionTest.java`: Kept PR #139's clean file and added the two PR #136 migrations (`202611131600__pricing_add_coordinates_to_districts.sql` and `202611131700__pricing_create_pricing_config.sql`) in exact chronological order.

---

## 2. PR #136 Changes Preserved

- All **28** staged-modified backend test files (including the `@MockitoBean` declarations for `DistrictRepository`, `PricingConfigRepository`, `UserAddressRepository`, `Merchant*Repository`, and `NotificationRecordRepository`, plus `DatabaseMigrationIT`, `ZonePairFeeCalculatorTest`, and `PriceQuoteApiTest`) remain intact.
- All **3** PR #136 reports (`PR-136-REVIEW-REPORT.md`, `PR-136-FIX-AND-VERIFICATION-REPORT.md`, `PR-136-ADDITIONAL-FIX-REPORT.md`) remain intact.
- `docs/pr-reviews/PR-139-REVIEW-REPORT.md` and `docs/pr-reviews/PR-136-RECOVERY-INSPECTION-REPORT.md` remain intact.

---

## 3. Changes Made for PR #139 & New Tests Added

1. `backend/src/test/java/com/swiftdrop/delivery/application/HubNetworkTest.java`:
   - Added `servedZoneIdsReturnsOnlyActiveHubZonesWithoutDuplicates` and `servedZoneIdsReturnsEmptySetWhenNoHubsExist` (5 tests total, all passing).
2. `backend/src/test/java/com/swiftdrop/delivery/web/DeliveryZoneControllerTest.java` (new):
   - Added `returnsOnlyActiveZonesServedByActiveHubs` and `returnsEmptyListWhenNoActiveZonesAreServed` (2 tests total, all passing).
3. `backend/src/test/java/com/swiftdrop/MigrationConventionTest.java`:
   - Added `202611131600` and `202611131700` migration tuples in chronological order.

---

## 4. Customer Booking Flow Review Findings

- `GET /api/v1/delivery-zones` (`DeliveryZoneController`) filters `ZoneDirectory.findActiveZones()` by `HubNetwork.servedZoneIds()` so only active zones served by an active hub are offered.
- `frontend/lib/delivery.ts` (`listAvailableZones()`), `frontend/components/delivery/address-fields.tsx` (`DistrictCombobox`, `ZoneCombobox` with cascading district-to-zone filtering), and `frontend/app/(customer)/customer/new/page.tsx` (3-step booking flow, live `PricingConfig` surcharges, debounced `getQuote()`, and `bookParcel()`) compile, lint, and build cleanly.

---

## 5. Verification Results

### Backend (`cd backend; .\mvnw.cmd verify -Pintegration`)
- **Build Status:** `BUILD SUCCESS` (`08:12 min`)
- **Unit Tests (Surefire):** All passed (`0` failures, `0` errors)
- **Integration Tests (Failsafe + Testcontainers):** `Tests run: 181, Failures: 0, Errors: 0, Skipped: 0` (including `DatabaseMigrationIT` with all 31 Flyway migrations and Hibernate `validate` against `District.java` `BigDecimal` coordinates)

### Frontend (`cd frontend`)
- `npm run lint`: **PASS** (exit `0`)
- `npx tsc --noEmit`: **PASS** (exit `0`)
- `npm run build`: **PASS** (`next build` succeeded, 30/30 static/dynamic routes generated)

---

## 6. Remaining Issues & Recommendation

- **Remaining Issues:** None in backend unit/integration tests or frontend lint/typecheck/build.
- **Git State:** Changes are in the working tree/index (not committed or pushed).
- **Recommendation:** **READY FOR BRANCH SYNCHRONIZATION / INTEGRATION** — once you approve committing/pushing the resolved test fixes and new `DeliveryZoneController` / `HubNetwork` tests, PR #139 is ready to merge into `dev`.
