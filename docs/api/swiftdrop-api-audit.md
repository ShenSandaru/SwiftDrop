# SwiftDrop Backend API Audit & Detail Extraction

> [!NOTE]
> Extracted strictly from source code under `backend/src/main/java/com/swiftdrop` (read-only audit). No `.env` files, secrets, or real personal data were read or included.

## 1. Summary

| Item | Value |
|---|---|
| **Repository** | `ShenSandaru/SwiftDrop` |
| **Branch** | `dev` |
| **Commit SHA** | `ba225c945bcae4250e8057850c5b97c77a83bfa5` |
| **Global `/api/v1` Prefix** | None configured globally in `application.properties` or `WebMvcConfigurer`. Every REST controller declares `/api/v1/...` directly in its `@RequestMapping` or method mapping annotations. Non-versioned exceptions: `/mcp`, `/oauth2/authorization/**`, `/login/oauth2/code/**`, `/actuator/health/**`, and Swagger/OpenAPI paths. |
| **Controller Count** | **28** `@RestController` classes (`identity`: 4, `pricing`: 5, `delivery`: 6, `dispatch`: 1, `tracking`: 1, `merchant`: 7, `notification`: 2, `assistant`: 2) |
| **Endpoint Count** | **108** controller-mapped HTTP endpoints (`identity`: 19, `pricing`: 15, `delivery`: 21, `dispatch`: 15, `tracking`: 1, `merchant`: 27, `notification`: 6, `assistant`: 4) + **2** Spring Security OAuth2 filter-managed endpoints (`GET /oauth2/authorization/google`, `GET /login/oauth2/code/google`) |

---

## 2. Master Endpoint Table

| Module | Method | Full Path | Purpose | Auth | Roles / Scope | Request DTO | Response DTO | Success Status | Source File |
|---|---|---|---|---|---|---|---|---|---|
| identity | POST | `/api/v1/auth/tokens` | Exchange email/password for Bearer JWT | Public | Any | `LoginRequest` | `AccessTokenResponse` | 200 | [AccessTokenController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/AccessTokenController.java) |
| identity | POST | `/api/v1/auth/google/exchanges` | Redeem single-use Google OAuth code for JWT | Public | Any | `GoogleExchangeRequest` | `AccessTokenResponse` | 200 | [AccessTokenController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/AccessTokenController.java) |
| identity | GET | `/oauth2/authorization/google` | Initiate Google OIDC login redirect | OAuth session | Public (if configured) | — | 302 Redirect | 302 | [GoogleLoginConfiguration.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/security/GoogleLoginConfiguration.java) |
| identity | GET | `/login/oauth2/code/google` | Google OIDC callback; issues single-use exchange code | OAuth session | Public (if configured) | — | 302 Redirect to frontend | 302 | [GoogleLoginResponses.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/security/GoogleLoginResponses.java) |
| identity | POST | `/api/v1/staff-users` | Create COURIER or DISPATCHER staff account | JWT | `ADMIN` | `CreateStaffUserRequest` | `UserResponse` | 201 | [StaffUserController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/StaffUserController.java) |
| identity | POST | `/api/v1/users` | Public customer account registration | Public | Any | `RegisterUserRequest` | `UserResponse` | 201 | [UserController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/UserController.java) |
| identity | POST | `/api/v1/users/staff` | Compat endpoint to create COURIER account | JWT | `ADMIN` | `RegisterUserRequest` | `UserResponse` | 201 | [UserController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/UserController.java) |
| identity | GET | `/api/v1/users` | List users (unpaged or paginated/role-filtered) | JWT | `ADMIN` | — | `List<UserResponse>` or `PageResponse<UserResponse>` | 200 | [UserController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/UserController.java) |
| identity | PATCH | `/api/v1/users/{id}` | Enable or disable a user account | JWT | `ADMIN` (not self/admin) | `UpdateUserStatusRequest` | `UserResponse` | 200 | [UserController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/UserController.java) |
| identity | GET | `/api/v1/users/me` | Get authenticated user's own profile | JWT | Authenticated (self) | — | `UserResponse` | 200 | [UserController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/UserController.java) |
| identity | PATCH | `/api/v1/users/me` | Update own full name and phone number | JWT | Authenticated (self) | `UpdateProfileRequest` | `UserResponse` | 200 | [UserController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/UserController.java) |
| identity | POST | `/api/v1/users/me/password` | Change own local password | JWT | Authenticated (self) | `ChangePasswordRequest` | — | 204 | [UserController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/UserController.java) |
| identity | PUT | `/api/v1/users/me/avatar` | Upload or replace own avatar image | JWT | Authenticated (self) | `multipart/form-data` (`file`) | `UserResponse` | 200 | [UserController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/UserController.java) |
| identity | GET | `/api/v1/users/me/avatar` | Download own avatar image | JWT | Authenticated (self) | — | `byte[]` (image) | 200 | [UserController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/UserController.java) |
| identity | GET | `/api/v1/users/{id}/avatar` | Download a user's avatar image | Public | Any (enabled user) | — | `byte[]` (image) | 200 | [UserController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/UserController.java) |
| identity | DELETE | `/api/v1/users/me/avatar` | Delete own avatar image | JWT | Authenticated (self) | — | `UserResponse` | 200 | [UserController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/UserController.java) |
| identity | GET | `/api/v1/users/me/addresses` | List customer's saved addresses | JWT | `CUSTOMER` (self) | — | `List<UserAddressResponse>` | 200 | [AddressController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/AddressController.java) |
| identity | POST | `/api/v1/users/me/addresses` | Create saved address | JWT | `CUSTOMER` (self) | `CreateAddressRequest` | `UserAddressResponse` | 201 | [AddressController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/AddressController.java) |
| identity | PUT | `/api/v1/users/me/addresses/{addressId}` | Update saved address | JWT | `CUSTOMER` (owner) | `UpdateAddressRequest` | `UserAddressResponse` | 200 | [AddressController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/AddressController.java) |
| identity | DELETE | `/api/v1/users/me/addresses/{addressId}` | Delete saved address | JWT | `CUSTOMER` (owner) | — | — | 204 | [AddressController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/AddressController.java) |
| identity | PATCH | `/api/v1/users/me/addresses/{addressId}/default` | Set saved address as default | JWT | `CUSTOMER` (owner) | — | `UserAddressResponse` | 200 | [AddressController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/AddressController.java) |
| pricing | GET | `/api/v1/districts` | List all districts with postal zones | JWT | Authenticated | — | `List<DistrictResponse>` | 200 | [DistrictController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/pricing/web/DistrictController.java) |
| pricing | GET | `/api/v1/districts/{code}` | Get district by 3-letter code | JWT | Authenticated | — | `DistrictResponse` | 200 | [DistrictController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/pricing/web/DistrictController.java) |
| pricing | POST | `/api/v1/price-quotes` | Calculate delivery fee quote | JWT or `X-Api-Key` | Authenticated | `PriceQuoteRequest` | `PriceQuoteResponse` | 200 | [PriceQuoteController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/pricing/web/PriceQuoteController.java) |
| pricing | GET | `/api/v1/pricing-config` | Get global dynamic pricing config | JWT | Authenticated | — | `PricingConfigResponse` | 200 | [PricingConfigController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/pricing/web/PricingConfigController.java) |
| pricing | PUT | `/api/v1/pricing-config` | Update global dynamic pricing config | JWT | `ADMIN` | `PricingConfigRequest` | `PricingConfigResponse` | 200 | [PricingConfigController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/pricing/web/PricingConfigController.java) |
| pricing | GET | `/api/v1/pricing-rules` | List all explicit zone-pair pricing rules | JWT | `ADMIN` | — | `List<PricingRuleResponse>` | 200 | [PricingRuleController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/pricing/web/PricingRuleController.java) |
| pricing | GET | `/api/v1/pricing-rules/{id}` | Get pricing rule by ID | JWT | `ADMIN` | — | `PricingRuleResponse` | 200 | [PricingRuleController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/pricing/web/PricingRuleController.java) |
| pricing | POST | `/api/v1/pricing-rules` | Create explicit pricing rule | JWT | `ADMIN` | `PricingRuleRequest` | `PricingRuleResponse` | 201 | [PricingRuleController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/pricing/web/PricingRuleController.java) |
| pricing | PUT | `/api/v1/pricing-rules/{id}` | Update pricing rule (optimistic lock) | JWT | `ADMIN` | `PricingRuleRequest` | `PricingRuleResponse` | 200 | [PricingRuleController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/pricing/web/PricingRuleController.java) |
| pricing | GET | `/api/v1/zones` | List active delivery zones | JWT or `X-Api-Key` | Authenticated | — | `List<ZoneResponse>` | 200 | [ZoneController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/pricing/web/ZoneController.java) |
| pricing | GET | `/api/v1/zones?all=true` | List all zones (active & inactive) | JWT | `ADMIN` | — | `List<ZoneAdminResponse>` | 200 | [ZoneController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/pricing/web/ZoneController.java) |
| pricing | GET | `/api/v1/zones/{id}` | Get zone by ID | JWT | `ADMIN` | — | `ZoneAdminResponse` | 200 | [ZoneController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/pricing/web/ZoneController.java) |
| pricing | POST | `/api/v1/zones` | Create delivery zone | JWT | `ADMIN` | `ZoneRequest` | `ZoneAdminResponse` | 201 | [ZoneController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/pricing/web/ZoneController.java) |
| pricing | PUT | `/api/v1/zones/{id}` | Update delivery zone (optimistic lock) | JWT | `ADMIN` | `ZoneRequest` | `ZoneAdminResponse` | 200 | [ZoneController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/pricing/web/ZoneController.java) |
| pricing | DELETE | `/api/v1/zones/{id}` | Delete unused delivery zone | JWT | `ADMIN` | — | — | 204 | [ZoneController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/pricing/web/ZoneController.java) |
| delivery | GET | `/api/v1/courier/parcels` | List courier's deliveries for today | JWT | `COURIER` (self) | — | `List<CourierParcelResponse>` | 200 | [CourierController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/CourierController.java) |
| delivery | POST | `/api/v1/parcels/{id}/status-changes` | Apply courier status transition | JWT | `COURIER` (active task) | `StatusChangeRequest` | `ParcelDetailResponse` | 200 | [CourierController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/CourierController.java) |
| delivery | PUT | `/api/v1/parcels/{id}/proof` | Capture/replace proof of delivery | JWT | `COURIER` (assigned) | `multipart/form-data` (`recipientName`, `photo`) | `ProofResponse` | 200 | [CourierController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/CourierController.java) |
| delivery | GET | `/api/v1/delivery-zones` | List active zones served by an active hub | JWT | Authenticated | — | `List<ServiceZone>` | 200 | [DeliveryZoneController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/DeliveryZoneController.java) |
| delivery | GET | `/api/v1/hubs` | List all hubs | JWT | `ADMIN`, `DISPATCHER` | — | `List<HubResponse>` | 200 | [HubController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/HubController.java) |
| delivery | POST | `/api/v1/hubs` | Create hub | JWT | `ADMIN` | `HubRequest` | `HubResponse` | 201 | [HubController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/HubController.java) |
| delivery | PUT | `/api/v1/hubs/{id}` | Update hub | JWT | `ADMIN` | `HubRequest` | `HubResponse` | 200 | [HubController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/HubController.java) |
| delivery | DELETE | `/api/v1/hubs/{id}` | Delete hub | JWT | `ADMIN` | — | — | 204 | [HubController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/HubController.java) |
| delivery | GET | `/api/v1/hubs/links` | List hub-to-hub links | JWT | `ADMIN`, `DISPATCHER` | — | `List<HubLinkResponse>` | 200 | [HubController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/HubController.java) |
| delivery | POST | `/api/v1/hubs/links` | Create hub-to-hub link | JWT | `ADMIN` | `HubLinkRequest` | `HubLinkResponse` | 201 | [HubController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/HubController.java) |
| delivery | DELETE | `/api/v1/hubs/links/{hubId}/{otherHubId}` | Remove hub-to-hub link | JWT | `ADMIN` | — | — | 204 | [HubController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/HubController.java) |
| delivery | GET | `/api/v1/hubs/staff` | List staff hub assignments | JWT | `ADMIN` | — | `List<StaffMemberResponse>` | 200 | [HubStaffController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/HubStaffController.java) |
| delivery | PUT | `/api/v1/hubs/{hubId}/staff/{userId}` | Assign staff member to hub | JWT | `ADMIN` | — | — | 204 | [HubStaffController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/HubStaffController.java) |
| delivery | DELETE | `/api/v1/hubs/{hubId}/staff/{userId}` | Remove staff member from hub | JWT | `ADMIN` | — | — | 204 | [HubStaffController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/HubStaffController.java) |
| delivery | POST | `/api/v1/parcels` | Book a new customer parcel | JWT | `CUSTOMER` | `CreateParcelRequest` | `ParcelCreatedResponse` | 201 | [ParcelController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/ParcelController.java) |
| delivery | GET | `/api/v1/parcels` | Paginated parcel list | JWT | `CUSTOMER` (own), `ADMIN`, `DISPATCHER` | — | `PageResponse<ParcelListItemResponse>` | 200 | [ParcelController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/ParcelController.java) |
| delivery | GET | `/api/v1/parcels/{id}` | Get parcel detail and timeline | JWT | `CUSTOMER` (own), `COURIER` (task), `ADMIN`, `DISPATCHER` | — | `ParcelDetailResponse` | 200 | [ParcelController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/ParcelController.java) |
| delivery | POST | `/api/v1/parcels/{id}/cancellation` | Cancel customer parcel before pickup | JWT | `CUSTOMER` (own) | — | — | 204 | [ParcelController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/ParcelController.java) |
| delivery | GET | `/api/v1/parcels/{id}/receipt` | Get parcel booking/delivery receipt | JWT | `CUSTOMER` (own), `ADMIN`, `DISPATCHER` | — | `ReceiptResponse` | 200 | [ParcelController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/ParcelController.java) |
| delivery | GET | `/api/v1/parcels/{id}/proof/photo` | Download proof-of-delivery photo | JWT | `CUSTOMER` (own), `COURIER` (task), `ADMIN`, `DISPATCHER` | — | `byte[]` (image) | 200 | [ParcelController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/ParcelController.java) |
| delivery | GET | `/api/v1/tracked-parcels/{trackingNumber}` | Public parcel tracking lookup (no PII) | Public | Any (rate-limited) | — | `PublicTrackingView` | 200 | [TrackingController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/TrackingController.java) |
| dispatch | GET | `/api/v1/dispatch/parcels` | List operational parcels in hub scope | JWT | `ADMIN`, `DISPATCHER` (hub-scoped) | — | `PageResponse<DispatchParcelSummary>` | 200 | [DispatchController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java) |
| dispatch | GET | `/api/v1/dispatch/stale-parcels` | List stale unfinished parcels | JWT | `ADMIN`, `DISPATCHER` (hub-scoped) | — | `PageResponse<DispatchParcelSummary>` | 200 | [DispatchController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java) |
| dispatch | GET | `/api/v1/dispatch/active-assignments` | List active courier assignments by type | JWT | `ADMIN`, `DISPATCHER` (hub-scoped) | — | `PageResponse<ParcelAssignmentSummary>` | 200 | [DispatchController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java) |
| dispatch | GET | `/api/v1/dispatch/scope` | Get caller's hub scope | JWT | `ADMIN`, `DISPATCHER` | — | `StaffScopeView` | 200 | [DispatchController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java) |
| dispatch | GET | `/api/v1/dispatch/courier-workloads` | Active parcel counts per courier | JWT | `ADMIN`, `DISPATCHER` (hub-scoped) | — | `Map<UUID, Long>` | 200 | [DispatchController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java) |
| dispatch | GET | `/api/v1/dispatch/couriers` | List assignable enabled couriers | JWT | `ADMIN`, `DISPATCHER` (hub-scoped) | — | `List<DispatchCourierResponse>` | 200 | [DispatchController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java) |
| dispatch | GET | `/api/v1/dispatch/parcels/{id}/assignment-history` | Get parcel assignment history | JWT | `ADMIN`, `DISPATCHER` | — | `ParcelAssignmentHistory` | 200 | [DispatchController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java) |
| dispatch | POST | `/api/v1/dispatch/parcel-assignments` | Assign or reassign courier to parcel | JWT | `ADMIN`, `DISPATCHER` (hub-scoped) | `DispatchAssignmentRequest` | `ParcelAssignmentSummary` | 201 | [DispatchController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java) |
| dispatch | POST | `/api/v1/dispatch/parcels/walk-in` | Book & receive walk-in parcel at hub | JWT | `ADMIN`, `DISPATCHER` (hub-scoped) | `CreateWalkInParcelRequest` | `WalkInParcelResponse` | 201 | [DispatchController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java) |
| dispatch | POST | `/api/v1/dispatch/parcels/{id}/hub-intake` | Receive parcel at hub | JWT | `ADMIN`, `DISPATCHER` | `HubScanRequest` | — | 204 | [DispatchController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java) |
| dispatch | POST | `/api/v1/dispatch/parcels/{id}/hub-scans` | Scan arriving parcel at hub | JWT | `ADMIN`, `DISPATCHER` (hub-scoped) | `HubScanRequest` | — | 204 | [DispatchController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java) |
| dispatch | POST | `/api/v1/dispatch/parcels/{id}/transit` | Dispatch parcel to next hub | JWT | `ADMIN`, `DISPATCHER` (hub-scoped) | — | — | 204 | [DispatchController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java) |
| dispatch | POST | `/api/v1/dispatch/parcels/{id}/return` | Start return flow for parcel | JWT | `ADMIN`, `DISPATCHER` (hub-scoped) | — | — | 204 | [DispatchController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java) |
| dispatch | POST | `/api/v1/dispatch/parcels/{id}/failure` | Mark parcel as failed | JWT | `ADMIN`, `DISPATCHER` (hub-scoped) | `ParcelFailureRequest` | — | 204 | [DispatchController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java) |
| dispatch | POST | `/api/v1/dispatch/parcels/{id}/cancellation` | Cancel parcel from dispatch | JWT | `ADMIN`, `DISPATCHER` (hub-scoped) | — | — | 204 | [DispatchController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/dispatch/web/DispatchController.java) |
| tracking | GET | `/api/v1/tracked-parcels/{trackingNumber}/events` | Public SSE live tracking stream | Public | Any (rate/stream-limited) | — | `SseEmitter` (`text/event-stream`) | 200 | [TrackingStreamController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/tracking/web/TrackingStreamController.java) |
| merchant | POST | `/api/v1/merchants` | Submit public merchant application | Public | Any | `MerchantApplicationRequest` | `MerchantApplicationResponse` | 201 | [MerchantController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantController.java) |
| merchant | GET | `/api/v1/merchants/me` | Get own merchant application/status | JWT | `MERCHANT` (any status) | — | `MerchantStatusResponse` | 200 | [MerchantController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantController.java) |
| merchant | PATCH | `/api/v1/merchants/me` | Update own profile (resubmits if REJECTED) | JWT | `MERCHANT` (self) | `MerchantProfileUpdateRequest` | `MerchantStatusResponse` | 200 | [MerchantController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantController.java) |
| merchant | GET | `/api/v1/merchants` | List merchant applications | JWT | `ADMIN` | — | `PageResponse<MerchantStatusResponse>` | 200 | [MerchantController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantController.java) |
| merchant | GET | `/api/v1/merchants/{id}` | Get merchant detail with audit history | JWT | `ADMIN` | — | `MerchantDetailResponse` | 200 | [MerchantController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantController.java) |
| merchant | POST | `/api/v1/merchants/{id}/approval-decisions` | Approve, reject, or suspend merchant | JWT | `ADMIN` | `ApprovalDecisionRequest` | `MerchantStatusResponse` | 200 | [MerchantController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantController.java) |
| merchant | POST | `/api/v1/merchants/{merchantId}/approval` | Compat approve/reject merchant endpoint | JWT | `ADMIN` | `MerchantApprovalRequest` | `MerchantStatusResponse` | 200 | [MerchantController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantController.java) |
| merchant | POST | `/api/v1/merchants/me/api-keys` | Create merchant API key (max 5 active) | JWT | `MERCHANT` + approved | `CreateApiKeyRequest` | `CreatedApiKeyResponse` | 201 | [MerchantApiKeyController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantApiKeyController.java) |
| merchant | GET | `/api/v1/merchants/me/api-keys` | List own API keys | JWT | `MERCHANT` + approved | — | `List<ApiKeyResponse>` | 200 | [MerchantApiKeyController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantApiKeyController.java) |
| merchant | DELETE | `/api/v1/merchants/me/api-keys/{keyId}` | Revoke an API key (idempotent) | JWT | `MERCHANT` + approved (owner) | — | — | 204 | [MerchantApiKeyController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantApiKeyController.java) |
| merchant | POST | `/api/v1/merchant-orders` | Create merchant order (idempotent) | `X-Api-Key` | `MERCHANT_API_KEY` | `MerchantOrderRequest` | `MerchantOrderCreatedResponse` | 201 / 200 | [MerchantOrderController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantOrderController.java) |
| merchant | GET | `/api/v1/merchant-orders/{merchantOrderReference}` | Get own order by merchant reference | `X-Api-Key` | `MERCHANT_API_KEY` (owner) | — | `MerchantOrderResponse` | 200 | [MerchantOrderController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantOrderController.java) |
| merchant | GET | `/api/v1/merchants/me/dashboard` | Get merchant portal dashboard summary | JWT | `MERCHANT` + approved | — | `MerchantDashboardResponse` | 200 | [MerchantPortalController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantPortalController.java) |
| merchant | GET | `/api/v1/merchants/me/orders` | List own merchant orders (paginated) | JWT | `MERCHANT` + approved | — | `PageResponse<MerchantOrderSummaryResponse>` | 200 | [MerchantPortalController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantPortalController.java) |
| merchant | GET | `/api/v1/merchants/me/orders/{id}` | Get own order detail & timeline | JWT | `MERCHANT` + approved (owner) | — | `MerchantOrderDetailResponse` | 200 | [MerchantPortalController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantPortalController.java) |
| merchant | POST | `/api/v1/merchants/me/webhook-endpoints` | Create webhook endpoint (max 5 active) | JWT | `MERCHANT` + approved | `CreateWebhookEndpointRequest` | `CreateWebhookEndpointResponse` | 201 | [MerchantWebhookEndpointController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantWebhookEndpointController.java) |
| merchant | GET | `/api/v1/merchants/me/webhook-endpoints` | List own active webhook endpoints | JWT | `MERCHANT` + approved | — | `List<WebhookEndpointResponse>` | 200 | [MerchantWebhookEndpointController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantWebhookEndpointController.java) |
| merchant | GET | `/api/v1/merchants/me/webhook-endpoints/{id}` | Get own webhook endpoint detail | JWT | `MERCHANT` + approved (owner) | — | `WebhookEndpointResponse` | 200 | [MerchantWebhookEndpointController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantWebhookEndpointController.java) |
| merchant | PATCH | `/api/v1/merchants/me/webhook-endpoints/{id}` | Update own webhook endpoint | JWT | `MERCHANT` + approved (owner) | `UpdateWebhookEndpointRequest` | `WebhookEndpointResponse` | 200 | [MerchantWebhookEndpointController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantWebhookEndpointController.java) |
| merchant | DELETE | `/api/v1/merchants/me/webhook-endpoints/{id}` | Soft-delete own webhook endpoint | JWT | `MERCHANT` + approved (owner) | — | — | 204 | [MerchantWebhookEndpointController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantWebhookEndpointController.java) |
| merchant | POST | `/api/v1/merchants/me/webhook-endpoints/{id}/secret-rotations` | Rotate webhook signing secret | JWT | `MERCHANT` + approved (owner) | — | `RotateSecretResponse` | 200 | [MerchantWebhookEndpointController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantWebhookEndpointController.java) |
| merchant | GET | `/api/v1/merchants/me/webhook-deliveries` | List own webhook deliveries | JWT | `MERCHANT` + approved | — | `PageResponse<WebhookDeliveryView>` | 200 | [WebhookDeliveryController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/WebhookDeliveryController.java) |
| merchant | GET | `/api/v1/merchants/me/webhook-deliveries/{id}` | Get own webhook delivery detail | JWT | `MERCHANT` + approved (owner) | — | `WebhookDeliveryDetail` | 200 | [WebhookDeliveryController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/WebhookDeliveryController.java) |
| merchant | POST | `/api/v1/merchants/me/webhook-deliveries/{id}/redeliveries` | Queue manual webhook redelivery | JWT | `MERCHANT` + approved (owner) | — | `RedeliveryResponse` | 202 | [WebhookDeliveryController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/WebhookDeliveryController.java) |
| merchant | GET | `/api/v1/webhook-deliveries` | Admin list all webhook deliveries | JWT | `ADMIN` | — | `PageResponse<WebhookDeliveryView>` | 200 | [AdminWebhookDeliveryController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/AdminWebhookDeliveryController.java) |
| merchant | GET | `/api/v1/webhook-deliveries/{id}` | Admin get any webhook delivery detail | JWT | `ADMIN` | — | `WebhookDeliveryDetail` | 200 | [AdminWebhookDeliveryController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/AdminWebhookDeliveryController.java) |
| merchant | POST | `/api/v1/webhook-deliveries/{id}/redeliveries` | Admin queue manual webhook redelivery | JWT | `ADMIN` | — | `RedeliveryResponse` | 202 | [AdminWebhookDeliveryController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/AdminWebhookDeliveryController.java) |
| notification | GET | `/api/v1/notifications` | Paginated in-app notification feed | JWT | Authenticated (self) | — | `PageResponse<NotificationResponse>` | 200 | [NotificationController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/notification/web/NotificationController.java) |
| notification | GET | `/api/v1/notifications/unread-count` | Count unread in-app notifications | JWT | Authenticated (self) | — | `UnreadCountResponse` | 200 | [NotificationController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/notification/web/NotificationController.java) |
| notification | PATCH | `/api/v1/notifications/{id}/read` | Mark single notification as read | JWT | Authenticated (owner) | — | `NotificationResponse` | 200 | [NotificationController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/notification/web/NotificationController.java) |
| notification | POST | `/api/v1/notifications/read-all` | Mark all unread notifications as read | JWT | Authenticated (self) | — | `BulkReadResponse` | 200 | [NotificationController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/notification/web/NotificationController.java) |
| notification | POST | `/api/v1/notifications/push-tokens` | Register or reassign FCM push token | JWT | Authenticated (self) | `RegisterPushTokenRequest` | `PushTokenResponse` | 200 | [PushTokenController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/notification/web/PushTokenController.java) |
| notification | DELETE | `/api/v1/notifications/push-tokens` | Deactivate own FCM push token | JWT | Authenticated (owner) | `UnregisterPushTokenRequest` | — | 204 | [PushTokenController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/notification/web/PushTokenController.java) |
| assistant | POST | `/api/v1/assistant/messages` | Stateless customer AI chat | Public | Any (rate-limited) | `AssistantRequest` | `AssistantResponse` | 200 | [AssistantController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/assistant/web/AssistantController.java) |
| assistant | POST | `/mcp` | Stateless MCP JSON-RPC 2.0 endpoint | Public | Any (Origin-checked, rate-limited) | JSON-RPC 2.0 Object | JSON-RPC 2.0 Object / empty | 200 / 202 | [McpController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/assistant/web/McpController.java) |
| assistant | GET | `/mcp` | Rejected (no server-initiated stream) | Public | Any | — | — (`Allow: POST`) | 405 | [McpController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/assistant/web/McpController.java) |
| assistant | DELETE | `/mcp` | Rejected (stateless; no sessions) | Public | Any | — | — (`Allow: POST`) | 405 | [McpController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/assistant/web/McpController.java) |

---

## 3. Shared Error Format & Pagination

### 3.1 RFC 9457 Error Format (`GlobalExceptionHandler`)
- **File:** [GlobalExceptionHandler.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/shared/web/GlobalExceptionHandler.java#L28-L72) (`@RestControllerAdvice`, `@Order(Ordered.LOWEST_PRECEDENCE)`).
- **Content-Type:** `application/problem+json` (Spring `ProblemDetail`).
- **Standard Fields:** `type` (default `about:blank`), `title`, `status`, `detail`, `instance`.
- **Custom Property:** `invalidFields` (`List<String>`, sorted distinct field names) on validation errors.

| Exception | HTTP Status | `detail` | Extra Properties |
|---|---|---|---|
| `MethodArgumentNotValidException` | `400 Bad Request` | `"Invalid request"` | `invalidFields: List<String>` |
| `HttpMessageNotReadableException` | `400 Bad Request` | `"Malformed request body"` | — |
| `OptimisticLockingFailureException` | `409 Conflict` | `"The resource was updated concurrently; please reload and try again"` | — |
| `AccessDeniedException` | `403 Forbidden` | `"Access denied"` | — |
| `ErrorResponseException` / `ResponseStatusException` | Preserved | Preserved from exception | — |
| `Exception` (fallback) | `500 Internal Server Error` | `"Unexpected error"` | — |

### 3.2 Pagination Wrapper (`PageResponse<T>` & `PageLimit`)
- **Files:** [PageResponse.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/shared/web/PageResponse.java#L15-L30), [PageLimit.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/shared/web/PageLimit.java#L19-L50).
- **Fields:** `items: List<T>`, `page: int` (0-based), `size: int`, `totalItems: long`, `totalPages: int`.
- **Clamping:** `page` defaults to `0` (`< 0` clamped to `0`); `size` defaults to `20` (`< 1` becomes `20`; `> 100` clamped to `100`).

---

## 4. Detailed Endpoints by Module & Controller

### 4.1 Module: `identity` (4 controllers, 19 endpoints)

**Module Exception Handler:** [IdentityExceptionHandler.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/web/IdentityExceptionHandler.java#L24-L79) (scoped to `identity.web`):
- `MethodArgumentNotValidException` → `400` (`invalidFields`)
- `HttpMessageNotReadableException` → `400` (`"Malformed request body"`)
- `IllegalArgumentException` → `400`
- `InvalidPasswordException` → `400` (`invalidFields: ["password"]`)
- `InvalidRoleException` → `400` (`invalidFields: ["role"]`)
- `InvalidCredentialsException` → `401`
- `InvalidExchangeCodeException` → `401`
- `EmailAlreadyRegisteredException` → `409`
- `SelfDisablingException` → `409`

#### `AccessTokenController` (`identity/web/AccessTokenController.java`)
1. **`POST /api/v1/auth/tokens`** (`login`, L27–32)
   - **Purpose:** Exchange email and password for a Bearer JWT access token.
   - **Auth/Scope:** Public (`permitAll()`). `AccountService.authenticate` verifies enabled account & BCrypt hash (constant-time dummy hash check).
   - **Request (`LoginRequest`):**
     | Field | Type | Required | Constraints | Description |
     |---|---|---|---|---|
     | `email` | `String` | Yes | `@NotBlank`, `@Size(max = 320)` | Account email |
     | `password` | `String` | Yes | `@NotBlank`, `@Size(max = 72)` | Local password (max 72 UTF-8 bytes) |
   - **Response (`200 OK`, `AccessTokenResponse`):** `accessToken: String`, `tokenType: String` (`"Bearer"`), `expiresIn: long` (seconds).
   - **Errors:** `400` (validation/malformed), `401` (`InvalidCredentialsException`).

2. **`POST /api/v1/auth/google/exchanges`** (`exchangeGoogleCode`, L34–37)
   - **Purpose:** Redeem a single-use Google OAuth exchange code for a Bearer JWT.
   - **Auth/Scope:** Public (`permitAll()`). `GoogleExchangeService.exchange` atomically redeems code (60s TTL, single-use).
   - **Request (`GoogleExchangeRequest`):** `code: String` (Yes, `@NotBlank`).
   - **Response (`200 OK`, `AccessTokenResponse`):** Same as above.
   - **Errors:** `400`, `401` (`InvalidExchangeCodeException`).

#### `StaffUserController` (`identity/web/StaffUserController.java`)
1. **`POST /api/v1/staff-users`** (`createStaff`, L36–50)
   - **Purpose:** Create a `COURIER` or `DISPATCHER` staff account with optional hub assignment.
   - **Auth/Scope:** JWT, `@PreAuthorize("hasRole('ADMIN')")`. `AccountService.registerStaff` restricts role to `COURIER`/`DISPATCHER` and enforces at most one `DISPATCHER` per `hubId`.
   - **Request (`CreateStaffUserRequest`):**
     | Field | Type | Required | Constraints | Description |
     |---|---|---|---|---|
     | `fullName` | `String` | Yes | `@NotBlank`, `@Size(max = 200)` | Staff full name (trimmed) |
     | `email` | `String` | Yes | `@NotBlank`, `@Email`, `@Size(max = 320)` | Email (trimmed) |
     | `password` | `String` | Yes | `@NotBlank`, `@Size(min = 8, max = 72)` | Password |
     | `role` | `String` | No | `COURIER` or `DISPATCHER` (case-insensitive) | Defaults to `COURIER` |
     | `hubId` | `UUID` | No | — | Optional hub assignment |
   - **Response (`201 Created`, `UserResponse`):** `id: UUID`, `email: String`, `fullName: String`, `phoneNumber: String`, `roles: List<String>`, `enabled: boolean`, `hasAvatar: boolean`, `avatarUpdatedAt: Instant`, `createdAt: Instant`, `profileCompleted: boolean`, `hubId: UUID`.
   - **Errors:** `400` (`InvalidRoleException`, `InvalidPasswordException`), `403`, `409` (`EmailAlreadyRegisteredException`, duplicate hub dispatcher).

#### `UserController` (`identity/web/UserController.java`)
- **`POST /api/v1/users`** (`register`, L52–57): Public. Request `RegisterUserRequest` (`fullName`, `email`, `password`). Always assigns `CUSTOMER`. `201 Created`, `UserResponse`. Errors: `400`, `409`.
- **`POST /api/v1/users/staff`** (`createStaff`, L60–66): JWT, `hasRole('ADMIN')`. Request `RegisterUserRequest`. Creates `COURIER`. `201 Created`, `UserResponse`. Errors: `400`, `403`, `409`.
- **`GET /api/v1/users`** (`listUsers`, L72–97): JWT, `hasRole('ADMIN')`. Query: `page: Integer` (opt, `0`), `size: Integer` (opt, `20`, clamped `1..100`), `role: String` (opt, `Role` name). Returns `List<UserResponse>` when all 3 params omitted, else `PageResponse<UserResponse>`. Errors: `400` (`InvalidRoleException`), `403`.
- **`PATCH /api/v1/users/{id}`** (`updateUserStatus`, L103–112): JWT, `hasRole('ADMIN')`. Path `id: UUID`. Request `UpdateUserStatusRequest` (`enabled: Boolean @NotNull`). `200 OK`, `UserResponse`. Errors: `400`, `403`, `404`, `409` (`SelfDisablingException` on self or any `ADMIN`).
- **`GET /api/v1/users/me`** (`me`, L115–123): JWT (self). `200 OK`, `UserResponse`. Errors: `401` (disabled), `404`.
- **`PATCH /api/v1/users/me`** (`updateProfile`, L126–133): JWT (self). Request `UpdateProfileRequest` (`fullName @NotBlank @Size(max=200)`, `phoneNumber` opt `@Pattern("^\\+[1-9][0-9]{7,14}$")`). `200 OK`, `UserResponse`. Errors: `400`, `401`, `404`.
- **`POST /api/v1/users/me/password`** (`changePassword`, L136–144): JWT (self). Request `ChangePasswordRequest` (`currentPassword @NotBlank`, `newPassword @NotBlank @Size(min=8,max=72)`). `204 No Content`. Errors: `400` (Google-only account / invalid password), `401` (wrong current password / disabled), `404`.
- **`PUT /api/v1/users/me/avatar`** (`uploadAvatar`, L147–159): JWT (self). `consumes = multipart/form-data`, part `file` (`MultipartFile`, max 2 MB, allowed `image/jpeg`, `image/png`, `image/webp`). `200 OK`, `UserResponse`. Errors: `400`, `401`, `404`.
- **`GET /api/v1/users/me/avatar`** (`getMyAvatar`, L162–169) & **`GET /api/v1/users/{id}/avatar`** (`getAvatar`, L172–180): `me` requires JWT; `{id}` is Public. `200 OK`, binary `byte[]`, stored image MIME `Content-Type`, `Cache-Control: max-age=3600, public`. Errors: `401` (`me`), `404`.
- **`DELETE /api/v1/users/me/avatar`** (`deleteAvatar`, L183–188): JWT (self). `200 OK`, `UserResponse`. Errors: `401`, `404`.

#### `AddressController` (`identity/web/AddressController.java`)
Class-level: `/api/v1/users/me/addresses`, `@PreAuthorize("hasRole('CUSTOMER')")`. Ownership enforced via `findByIdAndUserId` in `AddressService`.
- **`GET /api/v1/users/me/addresses`** (`getAddresses`, L37–43): `200 OK`, `List<UserAddressResponse>` (`id`, `userId`, `label`, `recipientName`, `phoneNumber`, `streetAddress`, `city`, `postalCode`, `isDefault`, `createdAt`).
- **`POST /api/v1/users/me/addresses`** (`createAddress`, L45–53): Request `CreateAddressRequest`:
  | Field | Type | Required | Constraints | Description |
  |---|---|---|---|---|
  | `label` | `String` | Yes | `@NotBlank`, `@Size(max = 50)` | Address label |
  | `recipientName` | `String` | Yes | `@NotBlank`, `@Size(max = 200)` | Recipient name |
  | `phoneNumber` | `String` | Yes | `@NotBlank`, `@Size(max = 30)` | Contact phone |
  | `streetAddress` | `String` | Yes | `@NotBlank`, `@Size(max = 255)` | Street line |
  | `city` | `String` | Yes | `@NotBlank`, `@Size(max = 100)` | City |
  | `postalCode` | `String` | No | `@Size(max = 20)` | Postal code |
  | `isDefault` | `boolean` | No | — | First address auto-defaults to `true` |
  Response: `201 Created`, `Location: /api/v1/users/me/addresses/{id}`, `UserAddressResponse`. Errors: `400`, `404`.
- **`PUT /api/v1/users/me/addresses/{addressId}`** (`updateAddress`, L55–62): Request `UpdateAddressRequest` (same fields). `200 OK`, `UserAddressResponse`. Errors: `400`, `404`.
- **`DELETE /api/v1/users/me/addresses/{addressId}`** (`deleteAddress`, L64–71): `204 No Content`. Errors: `404`.
- **`PATCH /api/v1/users/me/addresses/{addressId}/default`** (`setDefaultAddress`, L73–79): `200 OK`, `UserAddressResponse`. Errors: `404`.

---

### 4.2 Module: `pricing` (5 controllers, 15 endpoints)

**Module Exception Handler:** [PricingExceptionHandler.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/pricing/web/PricingExceptionHandler.java#L20-L43) (`@Order(HIGHEST_PRECEDENCE)`): `PricingNotFoundException` (404), `RouteNotPricedException` (409), `PricingConflictException` (409), `ObjectOptimisticLockingFailureException` (409).

- **`DistrictController` (`pricing/web/DistrictController.java`):**
  - `GET /api/v1/districts` (JWT): `200 OK`, `List<DistrictResponse>` (`code`, `name`, `province`, `postalCode`, `defaultBranchName`, `latitude`, `longitude`, `zones: List<PostalZoneResponse(code, name, postalCode)>`).
  - `GET /api/v1/districts/{code}` (JWT): `200 OK`, `DistrictResponse`. Errors: `404`.
- **`PriceQuoteController` (`pricing/web/PriceQuoteController.java`):**
  - `POST /api/v1/price-quotes` (JWT **or** `X-Api-Key`; both → `400` via `AmbiguousCredentialsFilter`):
    - Request `PriceQuoteRequest`: `pickupZoneId: UUID (@NotNull)`, `dropoffZoneId: UUID (@NotNull)`, `size: ParcelSize (@NotNull)`, `weightGrams: Integer (@NotNull @Min(1) @Max(30_000))`.
    - Response `200 OK`, `PriceQuoteResponse`: `amount: String`, `currency: String`, `pricingRuleId: UUID`, `breakdown: { baseFee, weightFee, sizeSurcharge }`.
    - Errors: `400`, `401`, `409` (`RouteNotPricedException`).
- **`PricingConfigController` (`pricing/web/PricingConfigController.java`):**
  - `GET /api/v1/pricing-config` (JWT): `200 OK`, `PricingConfigResponse`.
  - `PUT /api/v1/pricing-config` (JWT, `ADMIN`): Request `PricingConfigRequest` (8 `BigDecimal` fields `@NotNull @DecimalMin("0.00")`, optional `currency @Pattern("^[A-Z]{3}$")`). `200 OK`, `PricingConfigResponse`. Errors: `400`, `403`.
- **`PricingRuleController` (`pricing/web/PricingRuleController.java` — all `ADMIN`):**
  - `GET /api/v1/pricing-rules`: `200 OK`, `List<PricingRuleResponse>`.
  - `GET /api/v1/pricing-rules/{id}`: `200 OK`, `PricingRuleResponse`. Errors: `404`.
  - `POST /api/v1/pricing-rules`: Request `PricingRuleRequest` (`originZoneId @NotNull`, `destinationZoneId @NotNull`, `baseFee @NotNull @DecimalMin("0.00")`, `perKgFee @NotNull @DecimalMin("0.00")`, optional surcharges, `currency @Size(3..3)` default `"LKR"`, `active` default `true`, `version: Long`). `201 Created`, `PricingRuleResponse`. Errors: `400`, `404`, `409`.
  - `PUT /api/v1/pricing-rules/{id}`: Request `PricingRuleRequest` (optimistic lock `version`). `200 OK`, `PricingRuleResponse`. Errors: `400`, `404`, `409`.
- **`ZoneController` (`pricing/web/ZoneController.java`):**
  - `GET /api/v1/zones` (JWT **or** `X-Api-Key`): `200 OK`, `List<ZoneResponse>` (`id`, `code`, `name`).
  - `GET /api/v1/zones?all=true` (JWT, `ADMIN`): `200 OK`, `List<ZoneAdminResponse>` (`id`, `code`, `name`, `active`, `hubId`, `version`, `createdAt`).
  - `GET /api/v1/zones/{id}` (JWT, `ADMIN`): `200 OK`, `ZoneAdminResponse`. Errors: `404`.
  - `POST /api/v1/zones` (JWT, `ADMIN`): Request `ZoneRequest` (`code @NotBlank @Size(max=50)`, `name @NotBlank @Size(max=200)`, optional `hubId`, `active`, `version`). `201 Created`, `ZoneAdminResponse`. Errors: `400`, `409`.
  - `PUT /api/v1/zones/{id}` (JWT, `ADMIN`): Request `ZoneRequest` (optimistic lock `version`). `200 OK`, `ZoneAdminResponse`. Errors: `400`, `404`, `409`.
  - `DELETE /api/v1/zones/{id}` (JWT, `ADMIN`): `204 No Content`. Errors: `404`, `409` (in use).

---

### 4.3 Module: `delivery` (6 controllers, 21 endpoints)

**Module Exception Handler:** [DeliveryExceptionHandler.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/web/DeliveryExceptionHandler.java#L26-L79) (`@Order(HIGHEST_PRECEDENCE)`): `ParcelNotFoundException` (404), `ParcelConflictException` / `OptimisticLockingFailureException` (409), `IllegalArgumentException` / `MultipartException` (400), `MissingServletRequestParameterException` / `MissingServletRequestPartException` / `MethodArgumentTypeMismatchException` (400 + `invalidFields`), `MaxUploadSizeExceededException` (400, `"Photo must be at most 2 MB"`).

- **`CourierController` (`delivery/web/CourierController.java`, `@PreAuthorize("hasRole('COURIER')")`):**
  - `GET /api/v1/courier/parcels`: `200 OK`, `List<CourierParcelResponse>`.
  - `POST /api/v1/parcels/{id}/status-changes`: Active task required (`ParcelStatusService.changeStatus`; never held → 404; previously held → 409). Request `StatusChangeRequest` (`toStatus: ParcelStatus @NotNull`, `failureReason: FailureReason` opt, `note @Size(max=500)` opt). `200 OK`, `ParcelDetailResponse`. Errors: `400`, `404`, `409`.
  - `PUT /api/v1/parcels/{id}/proof`: `consumes = multipart/form-data`. Assigned courier only (`DeliveryProofService.capture`). Form param `recipientName` (required, non-blank, max 200), file part `photo` (optional, max 2 MB, magic-byte checked `image/jpeg` or `image/png` in `PhotoType`). Idempotent replacement. `200 OK`, `ProofResponse`. Errors: `400`, `404`, `409` (status not `OUT_FOR_DELIVERY`/`RETURNING`).
- **`DeliveryZoneController` (`delivery/web/DeliveryZoneController.java`):**
  - `GET /api/v1/delivery-zones` (JWT): `200 OK`, `List<ServiceZone>` (`id`, `code`, `name`).
- **`HubController` (`delivery/web/HubController.java`):**
  - `GET /api/v1/hubs` (`ADMIN`/`DISPATCHER`): `200 OK`, `List<HubResponse>`.
  - `POST /api/v1/hubs` (`ADMIN`): Request `HubRequest` (`code @NotBlank @Size(max=20)`, `name @NotBlank @Size(max=100)`, `city @NotBlank @Size(max=100)`, `zoneId: UUID` opt, `central: boolean`, `active: Boolean` opt). `201 Created`, `HubResponse`.
  - `PUT /api/v1/hubs/{id}` (`ADMIN`): `200 OK`, `HubResponse` (`code` ignored on update).
  - `DELETE /api/v1/hubs/{id}` (`ADMIN`): `204 No Content`.
  - `GET /api/v1/hubs/links` (`ADMIN`/`DISPATCHER`): `200 OK`, `List<HubLinkResponse>`.
  - `POST /api/v1/hubs/links` (`ADMIN`): Request `HubLinkRequest` (`hubId @NotNull`, `otherHubId @NotNull`). `201 Created`, `HubLinkResponse`.
  - `DELETE /api/v1/hubs/links/{hubId}/{otherHubId}` (`ADMIN`): `204 No Content`.
- **`HubStaffController` (`delivery/web/HubStaffController.java`, `ADMIN`):**
  - `GET /api/v1/hubs/staff`: `200 OK`, `List<StaffMemberResponse>`.
  - `PUT /api/v1/hubs/{hubId}/staff/{userId}`: `204 No Content` (idempotent).
  - `DELETE /api/v1/hubs/{hubId}/staff/{userId}`: `204 No Content`.
- **`ParcelController` (`delivery/web/ParcelController.java`):**
  - `POST /api/v1/parcels` (`CUSTOMER`): Request `CreateParcelRequest` (`pickup`, `dropoff`, `size: ParcelSize @NotNull`, `weightGrams @NotNull @Min(1) @Max(30_000)`, `description @Size(max=200)` opt). `201 Created`, `Location: /api/v1/parcels/{id}`, `ParcelCreatedResponse`.
  - `GET /api/v1/parcels` (`CUSTOMER`/`ADMIN`/`DISPATCHER`): Query `page` (0), `size` (20, max 100), `status: ParcelStatus` (opt). Customers see own only. `200 OK`, `PageResponse<ParcelListItemResponse>`.
  - `GET /api/v1/parcels/{id}` (`CUSTOMER`/`COURIER`/`ADMIN`/`DISPATCHER`): `Viewer.canView` (404 otherwise; notes hidden for customers). `200 OK`, `ParcelDetailResponse`.
  - `POST /api/v1/parcels/{id}/cancellation` (`CUSTOMER`, own only): `204 No Content`. Errors: `404`, `409`.
  - `GET /api/v1/parcels/{id}/receipt` (`CUSTOMER` own / `ADMIN` / `DISPATCHER`): `200 OK`, `ReceiptResponse`. Errors: `404`.
  - `GET /api/v1/parcels/{id}/proof/photo` (`CUSTOMER` own / `COURIER` task / `ADMIN` / `DISPATCHER`): `200 OK`, binary `byte[]`, `image/jpeg` or `image/png`, `Cache-Control: no-store`. Errors: `404`.
- **`TrackingController` (`delivery/web/TrackingController.java`):**
  - `GET /api/v1/tracked-parcels/{trackingNumber}` (Public): Rate-limited (`60`/min/IP → `429`, `Retry-After: 60`). `200 OK`, `PublicTrackingView` (no PII). Errors: `404`, `429`.

---

### 4.4 Module: `dispatch` (1 controller, 15 endpoints)

**Module Exception Handler:** [DispatchExceptionHandler.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/dispatch/web/DispatchExceptionHandler.java#L21-L60) (`@Order(HIGHEST_PRECEDENCE)`): `ParcelNotFoundException` / `DispatchCourierNotFoundException` (404), `ParcelForbiddenException` (403), `ParcelConflictException` / `DispatchCourierUnavailableException` / `DataIntegrityViolationException` / `OptimisticLockingFailureException` (409), `MethodArgumentTypeMismatchException` (400 + `invalidFields`).

**`DispatchController` (`dispatch/web/DispatchController.java`, `/api/v1/dispatch`, `@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")`):**
Dispatchers are hub-scoped via `HubAuthority.requireCanAct` (403) and `hubStaff.colleaguesOf`; admins act everywhere.
- `GET /parcels`: Query `status`, `source`, `assigned`, `search`, `terminal`, `page` (0), `size` (20) → `200 OK`, `PageResponse<DispatchParcelSummary>`.
- `GET /stale-parcels`: Query `page`, `size` (untouched `>= 24h` default) → `200 OK`, `PageResponse<DispatchParcelSummary>`.
- `GET /active-assignments`: Query `type: ParcelAssignmentType` (**required**), `page`, `size` → `200 OK`, `PageResponse<ParcelAssignmentSummary>`.
- `GET /scope`: `200 OK`, `StaffScopeView`.
- `GET /courier-workloads`: `200 OK`, `Map<UUID, Long>`.
- `GET /couriers`: `200 OK`, `List<DispatchCourierResponse>`.
- `GET /parcels/{id}/assignment-history`: `200 OK`, `ParcelAssignmentHistory`. Errors: `404`.
- `POST /parcel-assignments`: Request `DispatchAssignmentRequest` (`parcelId @NotNull`, `courierId @NotNull`, `type: ParcelAssignmentType @NotNull`) → `201 Created`, `ParcelAssignmentSummary`. Errors: `400`, `403`, `404`, `409`.
- `POST /parcels/walk-in`: Request `CreateWalkInParcelRequest` (`hubId: UUID` opt for single-hub dispatcher, `pickup`, `dropoff`, `size`, `weightGrams`, `description`) → `201 Created`, `WalkInParcelResponse`. Errors: `400`, `409`.
- `POST /parcels/{id}/hub-intake`: Request `HubScanRequest` (`hubId @NotNull`) → `204 No Content`.
- `POST /parcels/{id}/hub-scans`: Request `HubScanRequest` (`hubId @NotNull`) → `204 No Content`.
- `POST /parcels/{id}/transit`: `204 No Content`.
- `POST /parcels/{id}/return`: `204 No Content`.
- `POST /parcels/{id}/failure`: Request `ParcelFailureRequest` (`reason: FailureReason @NotNull`, `note @Size(max=500)`) → `204 No Content`.
- `POST /parcels/{id}/cancellation`: `204 No Content`.

---

### 4.5 Module: `tracking` (1 controller, 1 endpoint)

**Module Exception Handler:** [TrackingExceptionHandler.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/tracking/web/TrackingExceptionHandler.java#L19-L32) (empty body): `UnknownTrackingNumberException` → `404`, `TooManyStreamsException` → `503` + `Retry-After: 30`.

- **`TrackingStreamController` (`tracking/web/TrackingStreamController.java`):**
  - `GET /api/v1/tracked-parcels/{trackingNumber}/events` (Public):
    - **Response:** `200 OK`, `Content-Type: text/event-stream` (`SseEmitter`, 30m timeout).
    - **Events (`LiveTracking.java:38-132`):**
      1. `event: snapshot` — immediate `PublicTrackingView` (closes immediately if terminal status).
      2. `event: status` (`id: <sequence>`) — `StatusUpdate(status, occurredAt, sequence)` (closes if final).
      3. Comment `: heartbeat` every `25s`.
    - **Limits:** `60` req/min/IP (`429`, `Retry-After: 60`); concurrency caps: `1,000` global, `20`/parcel, `30`/client IP (`503`, `Retry-After: 30`).

---

### 4.6 Module: `merchant` (7 controllers, 27 endpoints)

**Module Exception Handlers:**
- [MerchantExceptionHandler.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantExceptionHandler.java#L26-L126): RFC 9457 with custom `type` URIs (`https://swiftdrop.com/problems/invalid-request`, `duplicate-registration`, `illegal-state-transition`, `concurrent-modification`, `access-denied`, `webhook-limit-exceeded`, `webhook-endpoint-not-found`, `order-not-found`).
- [MerchantApiKeyExceptionHandler.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantApiKeyExceptionHandler.java#L19-L37): `ApiKeyNotFoundException` (404), `ApiKeyLimitReachedException` (409, `type: https://swiftdrop.com/problems/api-key-limit`).
- [MerchantOrderExceptionHandler.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/MerchantOrderExceptionHandler.java#L18-L46): `IllegalArgumentException` (400, `"Invalid request"`), `ParcelConflictException` (409), `MerchantOrderNotFoundException` (404).
- [WebhookDeliveryExceptionHandler.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/web/WebhookDeliveryExceptionHandler.java#L14-L31): `WebhookDeliveryNotFoundException` (404), `RedeliveryRefusedException` (409).

- **`MerchantController` (`/api/v1/merchants`):**
  - `POST /api/v1/merchants` (Public): Request `MerchantApplicationRequest` → `201 Created`, `MerchantApplicationResponse`. Errors: `400`, `409`.
  - `GET /api/v1/merchants/me` (`MERCHANT`): `200 OK`, `MerchantStatusResponse`. Errors: `403`, `404`.
  - `PATCH /api/v1/merchants/me` (`MERCHANT`): Request `MerchantProfileUpdateRequest` (all optional; auto-resubmits `REJECTED` → `PENDING`). `200 OK`, `MerchantStatusResponse`.
  - `GET /api/v1/merchants` (`ADMIN`): Query `status: MerchantStatus` (opt), `page` (0), `size` (20) → `200 OK`, `PageResponse<MerchantStatusResponse>`.
  - `GET /api/v1/merchants/{id}` (`ADMIN`): `200 OK`, `MerchantDetailResponse` (with `statusHistory`). Errors: `400` (not found), `403`.
  - `POST /api/v1/merchants/{id}/approval-decisions` (`ADMIN`): Request `ApprovalDecisionRequest` (`decision: APPROVE|REJECT|SUSPEND`, `reason` required for `REJECT`/`SUSPEND`) → `200 OK`, `MerchantStatusResponse`.
  - `POST /api/v1/merchants/{merchantId}/approval` (`ADMIN`): Compat endpoint (`APPROVE|REJECT`) → `200 OK`, `MerchantStatusResponse`.
- **`MerchantApiKeyController` (`/api/v1/merchants/me/api-keys`, `MERCHANT` + approved):**
  - `POST /`: Request `CreateApiKeyRequest` (`label @NotBlank @Size(max=100)`). Max 5 active keys. `201 Created`, `Cache-Control: no-store`, `CreatedApiKeyResponse` (raw `apiKey` shown once). Errors: `400`, `403`, `409`.
  - `GET /`: `200 OK`, `List<ApiKeyResponse>`.
  - `DELETE /{keyId}`: `204 No Content` (idempotent). Errors: `403`, `404`.
- **`MerchantOrderController` (`/api/v1/merchant-orders`, `X-Api-Key` / `MERCHANT_API_KEY`):**
  - `POST /`: Request `MerchantOrderRequest` (`merchantOrderReference`, `pickup`, `dropoff`, `size`, `weightGrams`, `description`). **Idempotency:** `201 Created` + `Location` if newly created; `200 OK` if `merchantOrderReference` already exists for this merchant. Response: `MerchantOrderCreatedResponse`. Errors: `400`, `401`, `409`.
  - `GET /{merchantOrderReference}`: `200 OK`, `MerchantOrderResponse`. Errors: `401`, `404`.
- **`MerchantPortalController` (`/api/v1/merchants/me`, `MERCHANT` + approved):**
  - `GET /dashboard`: `200 OK`, `MerchantDashboardResponse` (counts + 5 most recent orders).
  - `GET /orders`: Query `status: ParcelStatus` (opt), `page` (0), `size` (20) → `200 OK`, `PageResponse<MerchantOrderSummaryResponse>`.
  - `GET /orders/{id}`: `200 OK`, `MerchantOrderDetailResponse`. Errors: `404`.
- **`MerchantWebhookEndpointController` (`/api/v1/merchants/me/webhook-endpoints`, `MERCHANT` + approved):**
  - `POST /`: Request `CreateWebhookEndpointRequest` (`url @NotBlank @Size(max=2048)`, `description @Size(max=255)` opt, `events: Set<ParcelStatus> @NotEmpty`). Max 5 active. `201 Created`, `CreateWebhookEndpointResponse` (raw `secret` shown once). Errors: `400`, `403`, `409`.
  - `GET /`: `200 OK`, `List<WebhookEndpointResponse>`.
  - `GET /{id}`: `200 OK`, `WebhookEndpointResponse`. Errors: `404`.
  - `PATCH /{id}`: Request `UpdateWebhookEndpointRequest` (all optional) → `200 OK`, `WebhookEndpointResponse`.
  - `DELETE /{id}`: Soft-delete → `204 No Content`.
  - `POST /{id}/secret-rotations`: `200 OK`, `RotateSecretResponse(endpointId, secret, secretHint)`.
- **`WebhookDeliveryController` (`/api/v1/merchants/me/webhook-deliveries`, `MERCHANT` + approved) & `AdminWebhookDeliveryController` (`/api/v1/webhook-deliveries`, `ADMIN`):**
  - `GET /`: Query `endpointId` (opt), `status: WebhookDeliveryStatus` (opt), `page` (0), `size` (20) (+ `merchantId` on admin) → `200 OK`, `PageResponse<WebhookDeliveryView>`.
  - `GET /{id}`: `200 OK`, `WebhookDeliveryDetail` (attempt series; no payload/headers/secrets). Errors: `404`.
  - `POST /{id}/redeliveries`: `202 Accepted`, `RedeliveryResponse(deliveryId, series, status)`. Errors: `404`, `409` (`RedeliveryRefusedException`).

---

### 4.7 Module: `notification` (2 controllers, 6 endpoints)

Uses `GlobalExceptionHandler`. All endpoints require JWT (`@PreAuthorize("isAuthenticated()")`) and scope to `jwt.getSubject()`.
- **`NotificationController` (`/api/v1/notifications`):**
  - `GET /`: Query `unreadOnly: boolean` (`false`), `page` (`0`), `size` (`20`, max `100`). Filters to `IN_APP`. `200 OK`, `PageResponse<NotificationResponse>`.
  - `GET /unread-count`: `200 OK`, `UnreadCountResponse(unreadCount: long)`.
  - `PATCH /{id}/read`: Idempotent. `200 OK`, `NotificationResponse`. Errors: `404`.
  - `POST /read-all`: `200 OK`, `BulkReadResponse(updatedCount: int)`.
- **`PushTokenController` (`/api/v1/notifications/push-tokens`):**
  - `POST /`: Request `RegisterPushTokenRequest` (`token @NotBlank`, `platform @Size(max=20)` opt). Reassigns existing token if owned by another user. `200 OK`, `PushTokenResponse`. Errors: `400`, `503` (if push service unavailable).
  - `DELETE /`: Request `UnregisterPushTokenRequest` (`token @NotBlank`). `204 No Content`. Errors: `400`, `503`.

---

### 4.8 Module: `assistant` (2 controllers, 4 HTTP endpoints)

Uses `GlobalExceptionHandler`. See Cross-Cutting Sections D & E below.

---

## 5. Cross-Cutting Sections (A–H)

### A. Spring Security Filter Chains
1. **`googleLoginSecurity`** ([GoogleLoginConfiguration.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/security/GoogleLoginConfiguration.java#L50-L68)) — `@Order(HIGHEST_PRECEDENCE)`, active only when `swiftdrop.security.google.client-id` is non-blank. Matches `/oauth2/authorization/**`, `/login/oauth2/code/**`. OAuth2/OIDC login with `HttpSessionOAuth2AuthorizedClientRepository` and `RequestAttributeSecurityContextRepository`; redirects to `${swiftdrop.frontend.base-url}/auth/google/callback?code=...` or `?error=...` with `Cache-Control: no-store`.
2. **`merchantApiSecurity`** ([SecurityConfiguration.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/config/SecurityConfiguration.java#L65-L82)) — `@Order(HIGHEST_PRECEDENCE + 1)`. Matches `/api/v1/merchant-orders/**`. Accepts `X-Api-Key` only (`MerchantApiKeyAuthenticationFilter`, authority `MERCHANT_API_KEY`). Failure: `401 Unauthorized`, `WWW-Authenticate: ApiKey`, `application/problem+json`. Stateless, CSRF disabled.
3. **`pricingAndZonesSecurity`** ([SecurityConfiguration.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/config/SecurityConfiguration.java#L93-L115)) — `@Order(HIGHEST_PRECEDENCE + 2)`. Matches `POST /api/v1/price-quotes` and `GET /api/v1/zones`. Accepts Bearer JWT **or** `X-Api-Key`, never both (`AmbiguousCredentialsFilter` returns `400 Bad Request` `application/problem+json` if both are present). Stateless, CSRF disabled.
4. **`apiSecurity`** ([SecurityConfiguration.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/config/SecurityConfiguration.java#L117-L142)) — Default catch-all. Permits: `POST /api/v1/users`, `/api/v1/auth/tokens`, `/api/v1/auth/google/exchanges`, `/api/v1/merchants`, `GET /api/v1/tracked-parcels/**`, `GET /api/v1/users/*/avatar`, `POST /api/v1/assistant/messages`, `/mcp`, `/actuator/health/**`, `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`, `/error`. All else requires Bearer JWT validated against `UserDirectory.findUser(userId)` (must exist & be enabled).

### B. JWT & Google OAuth2 Exchange Flow
- **Algorithm:** `HS256` (`HmacSHA256`) ([JwtConfiguration.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/identity/security/JwtConfiguration.java#L41-L60)).
- **Config Keys:** `swiftdrop.security.jwt.secret` (min 32 UTF-8 bytes), `swiftdrop.security.jwt.issuer` (default `swiftdrop`), `swiftdrop.security.jwt.access-token-ttl` (default `PT1H`).
- **Claims:** `iss`, `sub` (user UUID), `iat`, `exp`, `roles` (sorted list of `Role` names, mapped to `ROLE_*` authorities).
- **Google OAuth2 Config Keys:** `swiftdrop.security.google.client-id`, `swiftdrop.security.google.client-secret`, `swiftdrop.frontend.base-url`.
- **Flow:** `GET /oauth2/authorization/google` → Google OIDC → `GET /login/oauth2/code/google` (validates ID token, no UserInfo call, creates single-use 60s code, invalidates session, redirects to frontend) → `POST /api/v1/auth/google/exchanges` (redeems code for Bearer JWT).

### C. Outbound Merchant Webhook Envelope & HMAC Signing
- **Event Type:** `"parcel.status-changed"`, `schemaVersion: 1`, `producer: "delivery"` ([ParcelStatusChanged.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/delivery/api/ParcelStatusChanged.java#L14-L30)).
- **Envelope JSON:**
  ```json
  {
    "eventId": "00000000-0000-0000-0000-000000000001",
    "eventType": "parcel.status-changed",
    "schemaVersion": 1,
    "occurredAt": "2026-01-01T12:00:00Z",
    "producer": "delivery",
    "data": {
      "parcelId": "00000000-0000-0000-0000-000000000002",
      "trackingNumber": "SD-EXAMPLE-001",
      "previousStatus": "CREATED",
      "newStatus": "PICKED_UP",
      "sequence": 2,
      "source": "MERCHANT",
      "ownerCustomerId": null,
      "ownerMerchantId": "00000000-0000-0000-0000-000000000003",
      "merchantOrderReference": "ORD-1001",
      "failureReasonCode": null,
      "hubCode": "CMB",
      "attempt": null
    }
  }
  ```
- **Headers:** `Content-Type: application/json`, `User-Agent: SwiftDrop-Webhooks/1`, `X-SwiftDrop-Event-Id`, `X-SwiftDrop-Event-Type`, `X-SwiftDrop-Timestamp` (Unix epoch seconds), `X-SwiftDrop-Signature` (`v1=<lowercaseHex>`).
- **Signing Rule** ([WebhookSignature.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/merchant/application/WebhookSignature.java#L29-L77)): `HMAC-SHA256(key = UTF-8(whsec_...), message = UTF-8(timestampSeconds + ".") || rawBodyBytes)`, formatted as `"v1=" + lowercaseHex`. Default tolerance: 5 minutes; constant-time comparison.

### D. MCP Endpoint (`POST /mcp`)
- **Files:** [McpController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/assistant/web/McpController.java), [McpServer.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/assistant/application/McpServer.java).
- **Auth & Origin Check:** Public (`permitAll()`). Rejects any non-null `Origin` not in `${swiftdrop.cors.allowed-origins}` with `403` / JSON-RPC `-32600`.
- **Rate Limit:** Applied on `tools/call` only using shared `assistantRateLimiter` (default `10`/min per key `"mcp:" + remoteAddr`).
- **Supported Versions:** Modern `2026-07-28`; Legacy `2025-11-25`, `2025-06-18`, `2025-03-26`.
- **Supported JSON-RPC Methods:** `initialize` (legacy), `server/discover` (2026-07-28), `ping`, `tools/list`, `tools/call`. Notifications (no `id`) return `202 Accepted`. `GET /mcp` and `DELETE /mcp` return `405 Method Not Allowed` (`Allow: POST`).
- **Tools (3):**
  1. `estimate_fee` — Input: `{ fromCity: string*, toCity: string*, weightKg: number* (0 < w <= 30), size?: "SMALL"|"MEDIUM"|"LARGE" }`. Output: `{ from, to, size, chargedKg, totalFee, currency, baseFee, weightFee, sizeFee }`.
  2. `list_service_areas` — Input: `{}`. Output: `{ areas: string[] }`.
  3. `track_parcel` — Input: `{ trackingNumber: string* (max 40) }`. Output: `{ found, trackingNumber, status, from, to, history: [{ status, at, hub?, attempt? }] }`.

### E. Assistant Chat Endpoint (`POST /api/v1/assistant/messages`)
- **File:** [AssistantController.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/assistant/web/AssistantController.java#L36-L49).
- **Request (`AssistantRequest`):** `message: String` (`@NotBlank`, `@Size(max=500)`), `history: List<Turn>` (optional, `@Size(max=10)`, each `Turn(role: USER|ASSISTANT @NotNull, text @NotBlank @Size(max=500))`).
- **Response (`200 OK`, `AssistantResponse`):** `reply: String`, `unavailable: boolean`, `cards: List<Card(tool: String, data: Map<String, Object>)>`.
- **Rate Limit:** `swiftdrop.assistant.rate-limit-per-minute` (default `10`/min per `remoteAddr` via `ClientRateLimiter`). Exceeding returns `429 Too Many Requests` (`application/problem+json`, `Retry-After: 60`). Max tool calls per message: `3`.

### F. Enums in API Payloads
- **`Role`** (`identity/domain/Role.java`): `CUSTOMER`, `MERCHANT`, `COURIER`, `DISPATCHER`, `ADMIN`
- **`ParcelStatus`** (`delivery/api/ParcelStatus.java`): `CREATED`, `PICKED_UP`, `AT_HUB`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERY_ATTEMPT_FAILED`, `DELIVERED`, `RETURNING`, `RETURNED`, `CANCELLED`, `FAILED`
- **`ParcelSize`** (`pricing/api/ParcelSize.java`): `SMALL`, `MEDIUM`, `LARGE`
- **`FailureReason`** (`delivery/api/FailureReason.java`): `RECIPIENT_UNAVAILABLE`, `ADDRESS_NOT_FOUND`, `REFUSED`, `DAMAGED`, `OTHER`
- **`ParcelSource`** (`delivery/api/ParcelSource.java`): `CUSTOMER`, `MERCHANT`
- **`ParcelAssignmentType`** (`delivery/api/ParcelAssignmentType.java`): `PICKUP`, `DELIVERY`, `RETURN`
- **`ParcelAssignmentStatus`** (`delivery/api/ParcelAssignmentStatus.java`): `ACTIVE`, `COMPLETED`, `REASSIGNED`, `CANCELLED`
- **`DispatchAction`** (`delivery/api/DispatchAction.java`): `ASSIGN_PICKUP`, `RECEIVE_AT_HUB`, `CANCEL`, `SCAN_AT_HUB`, `SEND_TO_NEXT_HUB`, `ASSIGN_DELIVERY`, `START_RETURN`, `ASSIGN_RETURN`, `MARK_FAILED`
- **`MerchantStatus`** (`merchant/api/MerchantStatus.java`): `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED`
- **`ApprovalDecisionRequest.Decision`**: `APPROVE`, `REJECT`, `SUSPEND`
- **`MerchantApprovalRequest.Decision`**: `APPROVE`, `REJECT`
- **`WebhookDeliveryStatus`** (`merchant/domain/WebhookDeliveryStatus.java`): `PENDING`, `IN_PROGRESS`, `SUCCEEDED`, `FAILED`, `SECRET_UNAVAILABLE`
- **`WebhookAttemptOutcome`** (`merchant/domain/WebhookAttemptOutcome.java`): `SUCCEEDED`, `HTTP_ERROR`, `TIMEOUT`, `NETWORK_ERROR`, `REFUSED_DESTINATION`, `ENDPOINT_INACTIVE`, `MERCHANT_NOT_APPROVED`, `MERCHANT_NOT_FOUND`
- **`MerchantWebhookRedelivery.RequestedBy`**: `MERCHANT`, `ADMIN`
- **`NotificationChannel`** (`notification/domain/NotificationChannel.java`): `IN_APP`, `EMAIL`, `SMS`, `PUSH`
- **`ChatRole`** (`assistant/application/ChatRole.java`): `USER`, `ASSISTANT`

### G. CORS & OpenAPI / Swagger Settings
- **CORS** ([SecurityConfiguration.java](file:///d:/Programming/SwiftDrop/SwiftDrop/backend/src/main/java/com/swiftdrop/config/SecurityConfiguration.java#L144-L155)): Path `/api/**`; allowed origins from `swiftdrop.cors.allowed-origins` (default `http://localhost:3000,http://localhost:3001,http://localhost:3002`); methods `GET, POST, PUT, PATCH, DELETE, OPTIONS`; headers `Authorization, Content-Type` (`X-Api-Key` is not in CORS allowed headers); `allowCredentials = false`.
- **OpenAPI / Swagger:** Enabled by default in `application.properties`; disabled in prod via `application-prod.properties` keys `springdoc.api-docs.enabled` and `springdoc.swagger-ui.enabled` (default `false` via `SWIFTDROP_SWAGGER_ENABLED`).

### H. Documentation Discrepancies
1. **Swagger/OpenAPI auth (`README.md:177`):** Claims `/swagger-ui.html` and `/v3/api-docs` require a bearer token. Actual code in `SecurityConfiguration.java:131` sets `.permitAll()` (public in non-prod, disabled in prod).
2. **Pricing access (`README.md:203`):** Lists `POST /api/v1/price-quotes` as `customer, admin`, omitting that `pricingAndZonesSecurity` (`SecurityConfiguration.java:93-115`) also accepts `X-Api-Key` (merchant API keys) for both `POST /api/v1/price-quotes` and `GET /api/v1/zones`.

---

## 6. Not Verified / Needs Follow-Up

- **Spring multipart global limits (`spring.servlet.multipart.max-file-size` / `max-request-size`):** Application-level 2 MB limits are verified in `AccountService.MAX_AVATAR_BYTES` and `DeliveryProof.java`, and `DeliveryExceptionHandler` handles `MaxUploadSizeExceededException`, but container-level `spring.servlet.multipart.*` overrides in `application.properties` were not explicitly inspected.
- **Actuator endpoints beyond `/actuator/health`:** Only `/actuator/health` and `/actuator/health/**` are permitted in `SecurityConfiguration.java:129`; other exposed actuator endpoints (if any) were not audited.
