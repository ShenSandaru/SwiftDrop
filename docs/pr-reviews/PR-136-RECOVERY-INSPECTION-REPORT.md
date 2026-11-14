# PR #136 Changes: Inspection and Backup Preparation Report

**Date:** 2026-11-14  
**Current Branch:** `feat/customer_bookDelivery`  
**Current HEAD:** `850edeeaf0e54db189bfa005ee152392be46e4d3` (`feat: update customer book delivery update`)  

---

## 1. Current Branch, HEAD, and Git State

- **Branch:** `feat/customer_bookDelivery`
- **HEAD:** `850edeeaf0e54db189bfa005ee152392be46e4d3`
- **Active merge/rebase:** No `MERGE_HEAD` or `REBASE_HEAD`, but two files remain in an unmerged (`UU`) index state from an earlier merge/cherry-pick/checkout conflict.
- **Working-tree summary:**
  - `2` unmerged (`UU`) files
  - `28` staged-modified (`M `) backend test files
  - `3` staged-added (`A `) PR #136 review/verification reports
  - `1` untracked (`??`) PR #139 review report (`docs/pr-reviews/PR-139-REVIEW-REPORT.md`)

---

## 2. Exact List of Modified Files (All Belong to PR #136 Phase 2 & 3)

All **28** modified backend test files contain uncommitted fixes from PR #136 Phase 2 & Phase 3 (none are unrelated):

1. `backend/src/test/java/com/swiftdrop/DatabaseMigrationIT.java` — Updated Flyway migration assertions for PR #136.
2. `backend/src/test/java/com/swiftdrop/pricing/application/ZonePairFeeCalculatorTest.java` — Updated fallback pricing assertions (`FeeQuote` in `LKR` instead of `RouteNotPricedException`).
3. `backend/src/test/java/com/swiftdrop/pricing/web/PriceQuoteApiTest.java` — Updated fallback quote expectation (`200 OK` / `LKR` instead of `409 Conflict`) and added `DistrictRepository` / `PricingConfigRepository` mocks.
4. `backend/src/test/java/com/swiftdrop/pricing/web/DistrictApiTest.java` — Added `@MockitoBean` declarations for `UserAddressRepository`, `Merchant*Repository`, `NotificationRecordRepository`, and `PricingConfigRepository`.
5. `backend/src/test/java/com/swiftdrop/pricing/web/PricingRuleApiTest.java` — Added `@MockitoBean` declarations for `UserAddressRepository`, `Merchant*Repository`, `NotificationRecordRepository`, `DistrictRepository`, and `PricingConfigRepository`.
6. **23 other database-free `@SpringBootTest` files** — Added `DistrictRepository.class` and `PricingConfigRepository.class` to `@MockitoBean(types = { ... })`:
   - `SwiftdropBackendApplicationTests.java`
   - `AssistantApiTest.java`, `McpApiTest.java`
   - `AuthenticationApiTest.java`
   - `CourierApiTest.java`, `HubApiTest.java`, `ParcelApiTest.java`, `TrackingApiTest.java`
   - `DispatchHubApiTest.java`
   - `GoogleLoginApiTest.java`, `AdminUserManagementApiTest.java`, `GoogleExchangeApiTest.java`, `IdentityAuthorizationMatrixTest.java`, `UserProfileApiTest.java`
   - `MerchantApiKeyApiTest.java`, `MerchantControllerTest.java`, `MerchantOrderApiTest.java`, `MerchantWebhookEndpointControllerTest.java`, `WebhookDeliveryApiTest.java`
   - `NotificationControllerTest.java`
   - `ZoneApiTest.java`
   - `AuthorizationMatrixHarnessTest.java`
   - `TrackingStreamApiTest.java`

### PR #136 Reports (Staged `A `)
- `docs/pr-reviews/PR-136-REVIEW-REPORT.md` — Initial PR #136 read-only audit.
- `docs/pr-reviews/PR-136-FIX-AND-VERIFICATION-REPORT.md` — PR #136 Phase 2 fix report (`MigrationConventionTest` & `DatabaseMigrationIT`).
- `docs/pr-reviews/PR-136-ADDITIONAL-FIX-REPORT.md` — PR #136 Phase 3 fix report (`District.java` Hibernate mapping, fallback tests, 26 `@SpringBootTest` mocks).

---

## 3. Exact List of Unmerged (`UU`) Files

1. `backend/src/main/java/com/swiftdrop/pricing/domain/District.java`
   - Stage 1 (`7ac393d`): `Double latitude`, `Double longitude` with `@Column(precision = 9, scale = 6)`.
   - Stage 2 (`ca10132`, PR #139): `BigDecimal latitude`, `BigDecimal longitude` with `@Column(precision = 9, scale = 6)`.
   - Stage 3 (`0494538`, PR #136 local fix): `Double latitude`, `Double longitude` with `@Column(columnDefinition = "NUMERIC(9,6)")`.
2. `backend/src/test/java/com/swiftdrop/MigrationConventionTest.java`
   - Stage 1 (`a83fa4e`), Stage 2 (`6ca0faa`, PR #139), Stage 3 (`c14c985`, PR #136 local fix): Conflict around the `pricing/V202611131400__pricing_create_districts_and_postal_zones.sql` tuple.

---

## 4. Which Changes Belong to PR #136 vs. PR #139

- **PR #136 (uncommitted local fixes):** All 28 modified backend test files, the 3 `PR-136-*.md` reports, and Stage 3 of `District.java` / `MigrationConventionTest.java`.
- **PR #139 (committed on `850edee`):** `HubNetwork.servedZoneIds()`, `DeliveryZoneController`, `District.java` (`BigDecimal` coordinates), `DistrictResponse.java`, `MigrationConventionTest.java` (clean tuple), `frontend/lib/delivery.ts`, `frontend/components/delivery/address-fields.tsx`, and `frontend/app/(customer)/customer/new/page.tsx`.

---

## 5. Backup Location and Verification Results

- **Backup directory (outside repo):** `D:\Programming\SwiftDrop\backup-pr136-20261114-1739`
- **Contents backed up:**
  - `worktree/`: Exact copies of all **33** files (28 modified test files + 2 conflicted files + 3 PR #136 reports).
  - `index-stages/`: All **6** Git index stage blobs (`:1:`, `:2:`, `:3:` for `District.java` and `MigrationConventionTest.java`).
  - `unstaged.patch` and `staged.patch`: Full `git diff` and `git diff --cached` patches.
- **Verification:** SHA-256 hash comparison across all 33 worktree files: **0 mismatches**.

---

## 6. Uncertainties / Risks

- None for data loss: all local PR #136 changes and conflict stages are backed up and verified outside the repository.
- Resolving `District.java` in favor of PR #139's `BigDecimal` mapping (Stage 2) is cleaner than `Double` + `columnDefinition`, as `BigDecimal` natively supports `@Column(precision = 9, scale = 6)` in Hibernate 7.

---

## 7. Recommended Next Recovery Steps

1. Resolve `District.java` (keeping PR #139's `BigDecimal` mapping) and `MigrationConventionTest.java` (clean tuple list) and `git add` both.
2. Keep the 28 `@SpringBootTest` / test fixes (since `origin/dev` also needs them for `backend` CI to pass on PR #139) or commit them cleanly.
3. Proceed with PR #139 Phase 3 (add `DeliveryZoneController` / `HubNetwork.servedZoneIds()` tests), Phase 4 (booking flow review), and Phase 5 (full backend + frontend verification).
