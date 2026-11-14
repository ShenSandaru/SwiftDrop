# PR #112 – Merchant Order API Comprehensive Review

- **Pull Request:** #112 (`https://github.com/ShenSandaru/SwiftDrop/pull/112`)
- **Title:** `merchant order api`
- **Branch:** `feat/merchant-order-api`
- **Author:** `Ashankavinda277` (Ashan Kavinda)
- **Base Branch:** `feat/dev_merchant` (intended for `dev`)
- **Governing Architecture & ADRs:** [ADR 0003](file:///d:/Programming/SwiftDrop/SwiftDrop/docs/adr/0003-modular-monolith-architecture.md) (Modular Monolith), [ADR 0004 §7.2](file:///d:/Programming/SwiftDrop/SwiftDrop/docs/adr/ADR-0004-merchant-module-design.md) (Order Ingestion), [ADR 0008](file:///d:/Programming/SwiftDrop/SwiftDrop/docs/adr/0008-merchant-api-key-authentication.md) (Merchant API Key Auth)
- **Issue Reference:** SD-405, MER-2 (Merchant Order API)

---

## 1. Executive Summary

PR #112 implements the **Merchant Order Intake API (`/api/v1/merchant-orders`)**, enabling external e-commerce platforms and merchant software systems to inject delivery orders directly into SwiftDrop via API keys.

The architectural design is **exceptionally clean and strictly adheres to the SwiftDrop modular monolith guidelines (ADR 0003 & ADR 0004)**:
- **Clean Module Boundaries**: The Merchant module does **not** create or touch parcel database tables directly. Instead, `MerchantOrderService` delegates parcel creation and queries across module boundaries through the official public contract `com.swiftdrop.delivery.api.ParcelCreation` and `ParcelQueries`.
- **Zero Cross-Module Pollution**: Pricing rules and fee calculations remain encapsulated inside `delivery` and `pricing`.
- **Strong Tenant Isolation**: The merchant identity is extracted exclusively from the authenticated API key (`@AuthenticationPrincipal AuthenticatedMerchant`). The request body cannot specify or spoof a `merchantId`.
- **Idempotency Support**: Repeated requests with the same `merchantOrderReference` safely return the existing order (HTTP 200) instead of creating duplicates.

---

## 2. Inventory of PR Changes

### Core Implementation Files (13 files changed, +831 / -2)

1. **Delivery Module Public API:**
   - [`backend/src/main/java/com/swiftdrop/delivery/api/NewMerchantParcel.java`](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/api/NewMerchantParcel.java):
     * DTO passed across the module boundary from `merchant` to `delivery`.
     * Carries `merchantId`, `merchantOrderReference`, pickup/dropoff `DeliveryContact`, `weightGrams`, `description`, and parcel `size`.
     * Decouples the merchant module from `pricing` by offering a `withSizeName(...)` factory that accepts size names as `String` (e.g. `"SMALL"`, `"MEDIUM"`).

2. **Merchant Order Application & Web Layer:**
   - [`backend/src/main/java/com/swiftdrop/merchant/web/MerchantOrderController.java`](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantOrderController.java):
     * Secured with `@PreAuthorize("hasAuthority('MERCHANT_API')")` (requires dedicated API key authentication filter from PR #110).
     * `POST /api/v1/merchant-orders`: Creates a new parcel (HTTP 201 Created with `Location` header) or returns an existing one on duplicate submission (HTTP 200 OK).
     * `GET /api/v1/merchant-orders/{merchantOrderReference}`: Fetches an order status by external reference.
   - [`backend/src/main/java/com/swiftdrop/merchant/application/MerchantOrderService.java`](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/application/MerchantOrderService.java):
     * Thin application service delegating to `ParcelCreation.createMerchantParcel(...)` and `ParcelQueries.findByMerchantReference(...)`.
   - **DTOs & Exception Handling:**
     * `MerchantOrderRequest`: Validated payload (`@NotBlank merchantOrderReference`, pickup/dropoff address, size, positive weight).
     * `OrderAddressRequest`: Validated contact information (`contactName`, `contactPhone`, `addressLine`, `city`).
     * `MerchantOrderCreatedResponse` & `MerchantOrderResponse`: Clean public responses returning `merchantOrderReference`, `trackingNumber`, `status`, `deliveryFee`, and estimated delivery.
     * `MerchantOrderExceptionHandler`: Maps `RouteNotPricedException` to HTTP 422 Unprocessable Entity and `IllegalArgumentException` to HTTP 400 Bad Request.

3. **Tests:**
   - `NewMerchantParcelTest`: Unit tests for factory methods, case-insensitive size resolution, and boundary validation (6 tests passing).
   - `MerchantOrderApiTest`: MockMvc slice tests verifying authentication enforcement, validation, idempotency, and tenant isolation.
   - `MerchantOrderIT`: End-to-end integration test with real database and pricing rules.

---

## 3. Threat & Cross-Module Risk Analysis

| Evaluation Dimension | Risk Level | Findings & Verification |
| :--- | :---: | :--- |
| **Cross-Module Boundaries** | **NO THREAT** | PR #112 strictly uses public Java interfaces (`com.swiftdrop.delivery.api.ParcelCreation`, `ParcelQueries`). No direct repository access or foreign table manipulation. ADR 0003 is perfectly respected. |
| **Authentication & Authorization** | **LOW / BLOCKED ON PR #110** | Endpoints are protected by `hasAuthority('MERCHANT_API')`. Calling this endpoint with normal customer/merchant JWT tokens is rejected by Spring Security. |
| **Tenant Isolation & IDOR** | **NO THREAT** | `merchantId` is derived exclusively from the API key credentials. An authenticated merchant cannot query or create orders for another merchant. |
| **Database Schemas & Migrations** | **NO THREAT** | PR #112 introduces **no new database tables or Flyway migrations**. It leverages existing delivery parcel tables (`parcels`, `merchant_order_reference` column). |
| **Data Integrity & Concurrency** | **NO THREAT** | Idempotency is enforced by the database unique constraint on `(merchant_id, merchant_order_reference)` in the `delivery` module. |

---

## 4. Key Findings & Blockers

### ⚠️ Finding 1: Test Fixture Mismatch with `dev` Hub Network (ADR 0009)
- **Problem:**
  When running `MerchantOrderApiTest`, the test suite fails to boot the Spring Test context with:
  ```
  UnsatisfiedDependencyException: Error creating bean with name 'hubAdministration':
  No qualifying bean of type 'com.swiftdrop.delivery.domain.HubRepository' available
  ```
- **Root Cause:**
  PR #112 was branched before `dev` merged PR #121 (ADR 0009 Hub Network). When `HubAdministration` and `HubNetwork` were added to `delivery`, they introduced dependencies on `HubRepository` and `ParcelAssignmentRepository`.
  In `MerchantOrderApiTest.java`, the `@MockitoBean` annotation mocks out delivery repositories for the sliced test, but is missing `HubRepository.class`.
- **Impact:**
  `MerchantOrderApiTest` fails on `dev` until `HubRepository.class` is added to `@MockitoBean(types = {...})`.

### ⚠️ Finding 2: Direct Dependency on PR #110 (Merchant API Keys)
- **Problem:**
  `MerchantOrderController` requires API key authentication (`@PreAuthorize("hasAuthority('MERCHANT_API')")`) and `AuthenticatedMerchant`.
- **Root Cause:**
  `MerchantApiKeyAuthenticationFilter` and `SecurityConfiguration` routing for `/api/v1/merchant-orders/**` are provided by **PR #110**.
- **Impact:**
  PR #112 cannot function in isolation on `dev` without PR #110 being merged first. PR #110 and PR #112 must either be merged in sequence or integrated together.

---

## 5. Review Verdict & Recommendations

### **Verdict:** **APPROVE WITH MINOR TEST ADJUSTMENT**

PR #112 is a well-designed, clean, and secure implementation of the Merchant Order API.

### Recommended Next Steps for Merge:
1. **Dependency Ordering**: Ensure PR #110 (API key authentication) is merged into `dev` prior to PR #112.
2. **Fixture Update**: In `backend/src/test/java/com/swiftdrop/merchant/web/MerchantOrderApiTest.java`, add `HubRepository.class` to the `@MockitoBean(types = {...})` list so tests pass cleanly against the latest `dev` Hub Network architecture.

