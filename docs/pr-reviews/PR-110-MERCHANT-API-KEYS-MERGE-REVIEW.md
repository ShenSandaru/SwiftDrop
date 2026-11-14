# PR #110 Safe Merge Conflict Resolution and Regression Verification Review

- **Pull Request:** [#110](https://github.com/ShenSandaru/SwiftDrop/pull/110) — `Feat/merchant api keys`
- **Feature Branch:** `feat/merchant-api-keys`
- **Target Branch:** `dev` (`9cb7ec7`)
- **Key Issues Addressed:** SD-403 (Merchant API Key Management), SD-404 (Merchant API Key Authentication Filter)
- **Governing Architecture & Decisions:** [ADR 0008](docs/adr/0008-merchant-api-key-authentication.md), [ADR 0003](docs/adr/0003-backend-modules-and-boundaries.md), Decision D-03

---

## 1. Conflicts Found and Resolutions

During the merge of `origin/dev` (`9cb7ec7`) into `feat/merchant-api-keys`, four conflicting files were identified and resolved without weakening security or breaking existing behavior:

### 1. `backend/src/main/java/com/swiftdrop/config/SecurityConfiguration.java`
- **Conflict Cause:**
  - `feat/merchant-api-keys` added the dedicated, high-precedence `merchantApiSecurity` filter chain for `/api/v1/merchant-orders/**` with `MerchantApiKeyAuthenticationFilter` and `MerchantAuthentication`.
  - `dev` added `UserDirectory` constructor injection into `SecurityConfiguration` and integrated user account status checks (`user.enabled()`) into `jwtAuthenticationConverter()`.
- **Resolution Applied:**
  - Preserved **both** enhancements:
    - Retained `UserDirectory` dependency injection and active user account validation for all existing JWT-authenticated user endpoints.
    - Preserved `@Order(Ordered.HIGHEST_PRECEDENCE + 1)` `merchantApiSecurity` filter chain for `/api/v1/merchant-orders/**`.
    - Verified filter ordering: merchant order endpoints strictly demand `AuthenticatedMerchant.AUTHORITY` and reject bearer JWTs; standard API chain does not read `X-Api-Key` and rejects API keys.
    - Preserved CORS policy: `X-Api-Key` is intentionally omitted from allowed headers so browsers cannot send API keys cross-origin.

### 2. `docs/adr/README.md`
- **Conflict Cause:** `feat/merchant-api-keys` added ADR 0008 (*Merchant API key authentication*), while `dev` concurrently added ADR 0009 (*Hub network, delivery attempts, returns and cancellation*).
- **Resolution Applied:** Kept both ADR 0008 and ADR 0009 in the decision index table in numerical sequence.

### 3. `backend/src/test/java/com/swiftdrop/DatabaseMigrationIT.java`
- **Conflict Cause:** Total migration count and sublist extraction assertions conflicted because `dev` added 4 new migrations (`V202611101200`, `V202611101210`, `V202611111000`, `V202611111010`) while PR #110 introduced `V202611081000__merchant_create_merchant_api_keys.sql`.
- **Resolution Applied:**
  - Updated total applied migration count assertion from 15 (or 18) to **19**.
  - Inserted `merchant/V202611081000__merchant_create_merchant_api_keys.sql` at installed rank index 14 (between `V202611051210` and `V202611101200`), matching chronological Flyway sorting.
  - Preserved assertions for subsequent identity role and delivery hub migrations at updated offsets (`subList(15, 17)`, `rows.get(17)`, `rows.get(18)`).
  - Maintained `merchant_api_keys` table count check (`SELECT COUNT(*) FROM merchant_api_keys == 0`) and the `merchantApiKeyPersistence()` integration test method verifying unique prefix constraint, hash storage, and throttled `last_used_at` updates.

### 4. `backend/src/test/java/com/swiftdrop/MigrationConventionTest.java`
- **Conflict Cause:** Expected migration tuples list conflicted between PR #110's renumbered migration (`202611081000`) and `dev`'s newer migrations (`202611101200`, `202611101210`, `202611111000`, `202611111010`).
- **Resolution Applied:** Preserved all expected migration tuples in strict chronological order, including `202611081000` alongside all recent migrations.

---

## 2. Files Modified During Integration

1. `backend/src/main/java/com/swiftdrop/config/SecurityConfiguration.java` (Merged `merchantApiSecurity` + `UserDirectory`)
2. `docs/adr/README.md` (Retained ADR 0008 and ADR 0009)
3. `backend/src/test/java/com/swiftdrop/DatabaseMigrationIT.java` (Updated migration index and count)
4. `backend/src/test/java/com/swiftdrop/MigrationConventionTest.java` (Updated expected migration tuples)
5. `backend/src/test/java/com/swiftdrop/merchant/web/MerchantApiKeyApiTest.java` (Added `HubRepository.class` to test mock types; injected `users` mock for the JWT probe test)
6. `backend/src/test/java/com/swiftdrop/dispatch/web/DispatchHubApiTest.java` (Added `MerchantApiKeyRepository.class` to test mock types)
7. `backend/src/test/java/com/swiftdrop/delivery/web/HubApiTest.java` (Added `MerchantApiKeyRepository.class` to test mock types)

---

## 3. Existing Functionality Preserved

- **Customer Flow:** Booking, address selection, pricing quotes, and public tracking unchanged.
- **Merchant Portal:** Merchant self-registration, admin approval checks, profile management, and webhook endpoints remain intact.
- **Courier & Delivery Flow:** Courier task lists, delivery proofs, status transitions, hub journey representations, and cancellation rules unchanged.
- **Dispatcher Management:** Hub board, parcel assignment, courier assignment, optimistic concurrency versioning, and failure recording preserved.
- **Identity & Security:** Single-admin invariant, Google OAuth exchange, role enforcement, and JWT token revocation for disabled accounts preserved.
- **Modular Monolith Boundaries:** Verified via ArchUnit tests; `config` imports only `merchant.api` without coupling to internal merchant implementation classes.

---

## 4. Unintended Changes Discovered

- **None in production code.**
- During test execution, two mock slice tests added in recent `dev` commits (`HubApiTest` and `DispatchHubApiTest`) failed context startup because they exclude JPA autoconfiguration and did not mock `MerchantApiKeyRepository`. Adding `MerchantApiKeyRepository.class` to their `@MockitoBean(types = {...})` brought them into alignment with the other existing web slice tests (`CourierApiTest`, `ParcelApiTest`, `TrackingApiTest`, `ZoneApiTest`).
- Similarly, `MerchantApiKeyApiTest` required `HubRepository.class` in its mock slice definition to satisfy `delivery`'s `HubAdministration` bean initialization.

---

## 5. Test Execution Evidence

All test runs completed with exit code `0` (BUILD SUCCESS):

### A. Merchant API Keys & Security Suite
- **Command:** `.\backend\mvnw.cmd -f backend/pom.xml test -Dtest="MerchantApiKeyApiTest,MerchantApiKeyAuthenticatorTest,MerchantApiKeyServiceTest,MerchantApiKeyFormatTest,ApiKeyGeneratorTest,MigrationConventionTest,AuthenticationApiTest,IdentityAuthorizationMatrixTest"`
- **Exit Code:** `0`
- **Results:** **128 tests run, 0 failures, 0 errors, 0 skipped**.
  - `MerchantApiKeyApiTest`: 24 tests passed
  - `MerchantApiKeyAuthenticatorTest`: 12 tests passed
  - `MerchantApiKeyServiceTest`: 9 tests passed
  - `MerchantApiKeyFormatTest`: 15 tests passed
  - `ApiKeyGeneratorTest`: 4 tests passed
  - `MigrationConventionTest`: 2 tests passed
  - `AuthenticationApiTest`: 3 tests passed
  - `IdentityAuthorizationMatrixTest`: 59 tests passed

### B. User Account Status & Identity Suite
- **Command:** `.\backend\mvnw.cmd -f backend/pom.xml test -Dtest="AdminUserManagementApiTest,AccountServiceTest,UserTest,GoogleLoginApiTest,GoogleExchangeApiTest,AuthorizationMatrixHarnessTest"`
- **Exit Code:** `0`
- **Results:** **83 tests run, 0 failures, 0 errors, 0 skipped**.
  - `AdminUserManagementApiTest`: 16 tests passed
  - `AccountServiceTest`: 23 tests passed
  - `UserTest`: 4 tests passed
  - `GoogleLoginApiTest`: 14 tests passed
  - `GoogleExchangeApiTest`: 7 tests passed
  - `AuthorizationMatrixHarnessTest`: 19 tests passed

### C. Parcel, Courier, Tracking & Pricing Suite
- **Command:** `.\backend\mvnw.cmd -f backend/pom.xml test -Dtest="ParcelApiTest,CourierApiTest,TrackingApiTest,TrackingStreamApiTest,ZoneApiTest,PriceQuoteApiTest"`
- **Exit Code:** `0`
- **Results:** **36 tests run, 0 failures, 0 errors, 0 skipped**.

### D. Dispatch & Hub Network Web Slice Tests
- **Command:** `.\backend\mvnw.cmd -f backend/pom.xml test -Dtest="DispatchHubApiTest,HubApiTest"`
- **Exit Code:** `0`
- **Results:** **11 tests run, 0 failures, 0 errors, 0 skipped**.

### E. Delivery Domain, Lifecycle & Route Suite
- **Command:** `.\backend\mvnw.cmd -f backend/pom.xml test -Dtest="ParcelStatusServiceTest,ParcelReadServiceTest,ParcelQueryServiceTest,ParcelLifecycleTest,HubRouteTest,HubTest,ParcelHubFlowTest"`
- **Exit Code:** `0`
- **Results:** **301 tests run, 0 failures, 0 errors, 0 skipped**.
  - `ParcelLifecycleTest`: 245 tests passed
  - `ParcelHubFlowTest`: 14 tests passed
  - `ParcelQueryServiceTest`: 10 tests passed
  - `ParcelReadServiceTest`: 10 tests passed
  - `ParcelStatusServiceTest`: 12 tests passed
  - `HubRouteTest`: 6 tests passed
  - `HubTest`: 4 tests passed

### F. Live PostgreSQL 18 Migration & Entity Validation (Testcontainers)
- **Command:** `.\backend\mvnw.cmd -f backend/pom.xml test -Dtest="DatabaseMigrationIT"`
- **Exit Code:** `0`
- **Results:** **10 tests run, 0 failures, 0 errors, 0 skipped**.
  - PostgreSQL 18 alpine container started.
  - All 19 Flyway migrations applied successfully in chronological order.
  - Hibernate `ddl-auto=validate` passed across all entities.
  - `merchantApiKeyPersistence()` passed: prefix uniqueness, check constraint validation, SHA-256 hash formatting, and 1-minute `last_used_at` write throttling verified against live database.

### G. Architecture & Module Boundaries (ArchUnit)
- **Command:** `.\backend\mvnw.cmd -f backend/pom.xml test -Dtest="ModuleBoundaryTest"`
- **Exit Code:** `0`
- **Results:** **9 tests run, 0 failures, 0 errors, 0 skipped**.

---

## 6. Security Scenarios Confirmed

- [x] **Valid Key Authorization:** Requests with a valid, active `X-Api-Key` authenticate to `/api/v1/merchant-orders/**` with `AuthenticatedMerchant` principal.
- [x] **Uniform Failure Resistance:** Missing, malformed, non-existent, revoked, or mismatched keys and non-approved merchants all return identical HTTP 401 Problem Details with `WWW-Authenticate: ApiKey`.
- [x] **Cross-Authentication Isolation:** Bearer JWT tokens are rejected on `/api/v1/merchant-orders/**`; API keys are rejected on general user endpoints.
- [x] **CORS Enforcement:** `X-Api-Key` is excluded from CORS allowed headers, restricting API keys to server-to-server calls.
- [x] **Self-Service Boundaries:** Merchants can only list and revoke their own keys; querying or attempting to revoke another merchant's key yields HTTP 404.
- [x] **Active Key Cap:** Enforces maximum 5 active keys per merchant with HTTP 409 Conflict.
- [x] **Account Status Enforcement:** Disabled user accounts are rejected on JWT authentication via `UserDirectory`.

---

## 7. Remaining Risks and Blockers

- **None.** All 578 executed tests across all modules passed with 0 failures, 0 errors, and 0 skipped.
- Working tree is clean of conflict markers and staged for the merge commit.

---

## 8. Final Recommendation

**READY FOR PR MERGE.**
All merge conflicts have been cleanly resolved, all regression tests pass, and zero unintended production behavior changes were introduced.
Awaiting user authorization before committing the merge resolution.

