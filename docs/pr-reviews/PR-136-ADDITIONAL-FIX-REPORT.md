# PR #136 – Phase 3: Additional Fix and Verification Report

**Branch:** `feat/admin_pricing/zones`  
**Base Reference:** `6583b1500e2ec8ec2b3a9ce30160bd6a47e2720a`  
**Date:** 2026-11-14  

---

## 1. Root Causes Identified

1. **Missing `@MockitoBean` declarations in database-free `@SpringBootTest` tests:**
   - PR #136 introduced `DistrictRepository` and `PricingConfigRepository` dependencies into `pricing` controllers/services (`PricingConfigController`, `ZonePairFeeCalculator`, etc.).
   - 26 database-free `@SpringBootTest` API/context tests (which exclude `DataSourceAutoConfiguration`, `HibernateJpaAutoConfiguration`, and `FlywayAutoConfiguration`) failed to load their `ApplicationContext` with `NoSuchBeanDefinitionException` because they lacked `@MockitoBean` declarations for `DistrictRepository` and `PricingConfigRepository`.
   - Additionally, `DistrictApiTest` and `PricingRuleApiTest` (authored before PR #123 and PR #131 were merged into `dev`) were missing `@MockitoBean` declarations for `UserAddressRepository`, `Merchant*Repository` (8 repositories), `NotificationRecordRepository`, and `PricingConfigRepository` (plus `DistrictRepository` in `PricingRuleApiTest`).

2. **Outdated fallback pricing expectations in unit/API tests:**
   - `ZonePairFeeCalculator` now falls back to global distance-based pricing via `PricingConfigRepository.findById(PricingConfig.DEFAULT_ID).orElseGet(PricingConfig::defaultConfig)` when no active `PricingRule` exists for a route, rather than throwing `RouteNotPricedException` (HTTP `409 Conflict`).
   - `ZonePairFeeCalculatorTest` (`routeWithoutActiveRuleUsesGlobalFallback`, `routesWithoutReverseRuleUseGlobalFallback`) and `PriceQuoteApiTest` (`routeWithoutExplicitRuleUsesGlobalFallback`) still asserted the old `RouteNotPricedException` / `409` behavior.

3. **Hibernate 7 schema validation mismatch on `District.latitude` / `District.longitude`:**
   - Flyway migration `V202611131600__pricing_add_coordinates_to_districts.sql` defines `latitude NUMERIC(9,6)` and `longitude NUMERIC(9,6)`.
   - `District.java` mapped `Double latitude` and `Double longitude` with `precision = 9, scale = 6`, which caused Hibernate 7 to throw `IllegalArgumentException: scale has no meaning for SQL floating point types`. Removing `precision`/`scale` alone caused `SchemaManagementException` during `ddl-auto=validate` (`found [numeric (Types#NUMERIC)], but expecting [float(53) (Types#FLOAT)]`).
   - Adding `columnDefinition = "NUMERIC(9,6)"` on both `@Column` annotations resolved both Hibernate 7's `precision`/`scale` restriction and PostgreSQL `validate` schema checking without altering Flyway migrations or the `Double` field type.

---

## 2. Files Modified

### Production Code
- `backend/src/main/java/com/swiftdrop/pricing/domain/District.java`: Added `columnDefinition = "NUMERIC(9,6)"` and removed `precision = 9, scale = 6` on `latitude` and `longitude`.

### Test Code
- `backend/src/test/java/com/swiftdrop/pricing/application/ZonePairFeeCalculatorTest.java`: Updated `routeWithoutActiveRuleUsesGlobalFallback` and `routesWithoutReverseRuleUseGlobalFallback` to assert global fallback `FeeQuote` (`LKR`).
- `backend/src/test/java/com/swiftdrop/pricing/web/PriceQuoteApiTest.java`: Updated `routeWithoutExplicitRuleUsesGlobalFallback` to expect `status().isOk()` and `currency = "LKR"`.
- `backend/src/test/java/com/swiftdrop/pricing/web/DistrictApiTest.java`: Added `PricingConfigRepository` mock (alongside `UserAddressRepository`, `Merchant*Repository`, and `NotificationRecordRepository`).
- `backend/src/test/java/com/swiftdrop/pricing/web/PricingRuleApiTest.java`: Added `DistrictRepository` and `PricingConfigRepository` mocks (alongside `UserAddressRepository`, `Merchant*Repository`, and `NotificationRecordRepository`).
- Added `DistrictRepository.class` and `PricingConfigRepository.class` to `@MockitoBean(types = { ... })` in 24 other database-free `@SpringBootTest` classes:
  - `SwiftdropBackendApplicationTests.java`
  - `AssistantApiTest.java`, `McpApiTest.java`
  - `AuthenticationApiTest.java`
  - `CourierApiTest.java`, `HubApiTest.java`, `ParcelApiTest.java`, `TrackingApiTest.java`
  - `DispatchHubApiTest.java`
  - `GoogleLoginApiTest.java`, `AdminUserManagementApiTest.java`, `GoogleExchangeApiTest.java`, `IdentityAuthorizationMatrixTest.java`, `UserProfileApiTest.java`
  - `MerchantApiKeyApiTest.java`, `MerchantControllerTest.java`, `MerchantOrderApiTest.java`, `MerchantWebhookEndpointControllerTest.java`, `WebhookDeliveryApiTest.java`
  - `NotificationControllerTest.java`
  - `PriceQuoteApiTest.java`, `ZoneApiTest.java`
  - `AuthorizationMatrixHarnessTest.java`
  - `TrackingStreamApiTest.java`

---

## 3. Verification Results

Executed `cd backend; .\mvnw.cmd verify -Pintegration` after the final edit:

- **Unit Tests (Maven Surefire):** All unit tests passed (`0` failures, `0` errors).
- **Integration Tests (Maven Failsafe + Testcontainers):** `Tests run: 181, Failures: 0, Errors: 0, Skipped: 0` (including `DatabaseMigrationIT` with all 31 Flyway migrations and Hibernate `validate`).
- **Build Status:** `BUILD SUCCESS` (total time `09:07 min`).
- **Git Diff Check:** `git diff --check` exited `0` with no whitespace errors.

---

## 4. Remaining Risks & Merge-Readiness Verdict

- **Risks:** None identified in the backend unit or integration test suites. Frontend tests were not part of this backend verification phase.
- **Verdict:** **READY FOR MERGE** (backend unit and integration test suites pass cleanly with 0 failures and 0 errors).
