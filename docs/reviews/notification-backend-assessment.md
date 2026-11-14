# SwiftDrop Backend – Architecture and Implementation Status Assessment

- **Document:** `docs/reviews/notification-backend-assessment.md`
- **Assessment Date:** 2026-11-13
- **Branch:** `feat/dev-notifications`
- **Status:** Complete, Read-Only Assessment
- **Objective:** Factually baseline the backend architecture, module states, event models, outbox infrastructure, and security mechanisms prior to planning and implementing the Notification System.

---

## 1. Executive Summary

A comprehensive, non-destructive, read-only inspection of the SwiftDrop backend codebase (`backend/src/main/java/com/swiftdrop/`, resources, database migrations, and tests) was performed.

### Key Findings:
1. **Overall Architectural Pattern**: The backend adheres strictly to a **Modular Monolith** pattern governed by ADR 0001, ADR 0002, and ADR 0003, with strong compile-time and test-time architectural boundaries enforced by `com.swiftdrop.ModuleBoundaryTest`.
2. **Delivery Module Status**: Highly mature and operational. It models the core parcel lifecycle, multi-leg hub operations (ADR 0009), courier assignments (ADR 0008), delivery proofs, transactional outbox logging, and public case-insensitive tracking queries.
3. **Notification Module Status**: **Completely unbuilt**. The package `com.swiftdrop.notification` exists alongside `com.swiftdrop.notification.api`, but contains only `package-info.java` files. There are **zero** notification entities, repositories, services, controllers, listeners, migrations, or test classes. No third-party email, SMS, or push providers are configured or present in `pom.xml`.
4. **Messaging & Outbox Infrastructure**: `delivery` declares and owns the durable RabbitMQ topic exchange `swiftdrop.events` (ADR 0004 §5) and implements a fully tested transactional outbox relay (`delivery_parcel_outbox_events` table, `OutboxPublisher`, `OutboxRelay`, `OutboxMonitor`) that publishes JSON-serialized `ParcelStatusChanged` events. However, **no consumer queues, bindings, or listeners exist yet in the codebase**. Neither `merchant` nor `notification` currently has an active RabbitMQ consumer or queue declaration.
5. **Event Inventory**: Only **one** RabbitMQ domain event exists in the entire backend: `ParcelStatusChanged` (`parcel.status-changed`, schema version 1), emitted exclusively when parcel status transitions occur. In-memory Spring ApplicationEvents exist for `MerchantStatusChanged` and `MerchantApplicationSubmitted`, but are not published to RabbitMQ. Notably, **courier assignment changes do not publish an event** to RabbitMQ or Spring Application Events.
6. **Authentication & Authorization**: Identity is handled via stateless JWT bearer tokens issuing claims with roles (`CUSTOMER`, `MERCHANT`, `COURIER`, `DISPATCHER`, `ADMIN`). User existence and enabled status are actively verified during token decoding via `UserDirectory`. Controller endpoints resolve caller identity directly from `Jwt.getSubject()` (UUID).
7. **Safe Integration Path**: The existing infrastructure allows `notification` to subscribe cleanly to `swiftdrop.events` via its own dedicated consumer queue (`notification.parcel-status-changed`) and consume `ParcelStatusChanged` events with at-least-once outbox replay guarantees and sequence-based gap reconciliation (`ParcelEvents.eventsBetween`) without modifying any existing business logic in `delivery`.

---

## 2. Actual Backend Architecture

The application is structured into clearly separated vertical modules under package `com.swiftdrop`:

```
backend/src/main/java/com/swiftdrop/
├── config/             # Cross-cutting Spring Security & CORS configuration
├── delivery/           # Core parcel management, assignments, hubs, proofs, outbox relay
├── dispatch/           # Dispatcher actions (assignment, failure marking, hub scanning)
├── identity/           # User accounts, authentication, JWT tokens, Google sign-in
├── merchant/           # Merchant registration, status management, webhooks
├── notification/       # [EMPTY] Target module for asynchronous customer notifications
├── pricing/            # Zone-based tariff rules & fee calculations
├── shared/             # Global web advice, pagination, RFC 9457 error contracts
└── tracking/           # Public SSE streaming for live parcel status tracking
```

### Module Boundary Enforcement (`ModuleBoundaryTest.java`)
ADR 0003 and `backend/src/test/java/com/swiftdrop/ModuleBoundaryTest.java` enforce strict dependency rules:
- Cross-module dependencies are permitted **only** via public `api` packages (`com.swiftdrop.<module>.api.*`).
- Internal packages (`domain`, `application`, `infrastructure`, `web`, `security`) are strictly isolated.
- Permitted graph:
  - `identity` -> No dependencies
  - `pricing` -> No dependencies
  - `delivery` -> depends on `identity.api`, `pricing.api`
  - `dispatch` -> depends on `identity.api`, `delivery.api`
  - `merchant` -> depends on `identity.api`, `delivery.api`
  - `notification` -> permitted to depend on `identity.api`, `delivery.api` (no other modules)
  - `tracking` -> depends on `delivery.api`
  - `config` and `shared` -> no module may depend on `config`; `shared` depends on nothing.

---

## 3. Module-by-Module Implementation Status

| Module | Primary Responsibility | Key Domain Entities & Repositories | Public APIs & Controllers | Cross-Module Interactions | Tests | Current Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`config`** | Global HTTP Security, CORS, JWT authentication filter | None | `SecurityConfiguration` | Consumes `identity.api.UserDirectory` | Unit & API tests in `AuthenticationApiTest` | **Implemented** |
| **`delivery`** | Parcel booking, lifecycle, hub network, courier assignment state, proof storage, outbox publishing | `Parcel`, `ParcelStatusHistoryEntry`, `DeliveryProof`, `ParcelAssignmentRecord`, `Hub`, `HubRoute`, `OutboxEvent` (Repositories: `ParcelRepository`, `ParcelStatusHistoryRepository`, `DeliveryProofRepository`, `ParcelAssignmentRepository`, `HubRepository`, `OutboxEventRepository`) | `ParcelController`, `CourierController`, `HubController`, `TrackingController`; Public APIs: `ParcelQueries`, `ParcelEvents`, `ParcelAssignment`, `ParcelHubOperations` | Uses `identity.api.UserDirectory`, `pricing.api.FeeCalculator` | Extensive: `ParcelBookingIT`, `ParcelStatusChangeIT`, `CourierFlowIT`, `HubLifecycleIT`, `OutboxRelayIT`, unit suites | **Implemented & Mature** |
| **`dispatch`** | Dispatcher portal operations, manual assignment, failure intervention, hub scans | None (delegates domain state to `delivery.api`) | `DispatchController` (`/api/v1/dispatch/*`) | Calls `delivery.api.ParcelAssignment`, `delivery.api.ParcelHubOperations`, `delivery.api.ParcelQueries`, `identity.api.UserDirectory` | `DispatchAssignmentConcurrencyTest` (delivery side), `DispatchController` mocked tests | **Implemented** |
| **`identity`** | User management, credentials, JWT generation, Google OAuth exchange, admin bootstrap | `User`, `ExternalIdentity`, `GoogleLoginCode` (Repositories: `UserRepository`, `ExternalIdentityRepository`, `GoogleLoginCodeRepository`) | `UserController`, `StaffUserController`, `AccessTokenController`; Public API: `UserDirectory`, `IdentityClaims`, `UserSummary` | Standalone foundation module | `AdminUserManagementApiTest`, `GoogleExchangeApiTest`, `IdentityAuthorizationMatrixTest`, `AccountServiceTest` | **Implemented & Mature** |
| **`merchant`** | Merchant profiles, self-registration, verification, webhook subscription/dispatch records | `Merchant`, `MerchantStatusHistory`, `MerchantWebhookEndpoint` (Repositories: `MerchantRepository`, `MerchantStatusHistoryRepository`, `MerchantWebhookEndpointRepository`) | `MerchantController` (`/api/v1/merchants/*`); Public API: `MerchantLookup`, `MerchantView`, `MerchantStatusChanged` | Calls `identity.api.UserDirectory`, `delivery.api.ParcelQueries` | `MerchantControllerTest`, `MerchantAdminServiceTest`, `MerchantAccessSecurityTest` | **Partially Implemented** (Domain & CRUD ready; outbound webhook dispatcher not built) |
| **`notification`**| Customer notifications (delivery updates, alerts) | **None** | **None** | Permitted: `identity.api`, `delivery.api` | **None** | **Missing / Planned** (Package structure only) |
| **`pricing`** | Zone pair definition, volumetric/weight fee calculations | `Zone`, `PricingRule` (Repositories: `ZoneRepository`, `PricingRuleRepository`) | `ZoneController`, `PriceQuoteController`; Public API: `FeeCalculator`, `FeeQuote`, `ParcelSize` | Standalone foundation module | `PriceQuoteApiTest`, `ZoneApiTest`, `ZonePairFeeCalculatorTest` | **Implemented** |
| **`shared`** | RFC 9457 error response handler, shared pagination models | None | `GlobalExceptionHandler`, `PageResponse`, `PageLimit` | Shared by all modules | `GlobalExceptionHandlerTest`, `PageResponseTest` | **Implemented** |
| **`tracking`** | Real-time SSE status streaming for end users | None | `TrackingStreamController` (`/api/v1/tracked-parcels/{trackingNumber}/live`); Public API: `LiveTracking` | Listens to `delivery.api.ParcelStatusChanged` via `@TransactionalEventListener` | `LiveTrackingIT`, `LiveTrackingTest`, `TrackingStreamApiTest` | **Implemented** |

---

## 4. Delivery Module – Existing Implementation & Workflows

### 4.1 Parcel Creation & Booking Workflow
- Initiated via `POST /api/v1/parcels` (`ParcelController.createParcel`):
  - Validates recipient and pickup addresses (`DeliveryContact`).
  - Calculates fees synchronously via `pricing.api.FeeCalculator`.
  - Determines originating and destination hubs via `delivery.application.HubNetwork`.
  - Generates secure tracking numbers (`TrackingNumberGenerator`).
  - Saves `Parcel` with status `CREATED`.
  - Atomically records initial `ParcelStatusHistoryEntry` (sequence = 1) and writes an outbox row into `delivery_parcel_outbox_events` via `ParcelEventRecorder.record`.
  - Emits in-process Spring event `ParcelStatusChanged` for local transactional subscribers.

### 4.2 Parcel Lifecycle & Status Transitions
Defined in `com.swiftdrop.delivery.api.ParcelStatus`:
- `CREATED` -> Initial status upon booking.
- `PICKED_UP` -> Courier collects package from customer/merchant.
- `AT_HUB` -> Package scanned into intermediate/transit hub (`delivery.application.ParcelHubService`).
- `IN_TRANSIT` -> Package dispatched from hub towards another hub or destination area.
- `OUT_FOR_DELIVERY` -> Courier takes package for final drop-off.
- `DELIVERY_ATTEMPT_FAILED` -> Recipient unreachable/absent (increments attempt counter up to `swiftdrop.delivery.max-delivery-attempts=3`).
- `DELIVERED` -> Terminal success (requires proof-of-delivery photo or signature via `DeliveryProofService`).
- `RETURNING` -> Exceeded max attempts or rejected; routing back to sender.
- `RETURNED` -> Terminal status: back with sender.
- `CANCELLED` -> Terminal status: cancelled before pickup.
- `FAILED` -> Terminal status: lost or damaged; dispatcher intervention.

Every transition invokes `ParcelEventRecorder.record(...)`, which:
1. Validates state transition legality (`ParcelLifecycle`).
2. Checks failure reason presence for `FAILED` or `DELIVERY_ATTEMPT_FAILED`.
3. Increments parcel sequence number.
4. Persists `ParcelStatusHistoryEntry`.
5. Persists `OutboxEvent` (`delivery_parcel_outbox_events`).
6. Fires in-memory Spring `ApplicationEventPublisher.publishEvent(ParcelStatusChanged)`.

### 4.3 Courier Assignment Workflow
- Managed via `ParcelAssignmentService` (`com.swiftdrop.delivery.application.ParcelAssignmentService` implementing `delivery.api.ParcelAssignment`).
- Types: `PICKUP`, `DELIVERY`, `RETURN` (`ParcelAssignmentType`).
- Updates `ParcelAssignmentRecord` in table `delivery_parcel_assignments`.
- Updates `parcel.assignedCourierId`.
- **CRITICAL FINDING**: Courier assignment **does NOT change the parcel status** and does **NOT** write to `delivery_parcel_outbox_events`. It does not emit a `ParcelStatusChanged` event.

### 4.4 Hub Network Operations
- `ParcelHubOperations` interface / `ParcelHubService`:
  - `scanAtHub(UUID parcelId, String hubCode, Actor actor)`: transitions parcel to `AT_HUB` or `IN_TRANSIT`.
  - `recordFailure(UUID parcelId, FailureReason reason, ...)`: transitions parcel to `FAILED`.
  - `cancelForCustomer(UUID parcelId, UUID customerId)`: transitions parcel from `CREATED` to `CANCELLED`.
  - All valid status transitions produce an outbox event.

### 4.5 APIs Exposing Delivery Information
- `GET /api/v1/parcels` (Customer/Admin list with pagination).
- `GET /api/v1/parcels/{id}` (Parcel details for owner/dispatcher/courier).
- `GET /api/v1/tracked-parcels/{trackingNumber}` (Public tracking view, no auth required, sanitized of personal data).
- `GET /api/v1/courier/parcels` (Active tasks for authenticated courier).
- Java API: `ParcelQueries` (used across modules for unassigned parcels, active courier loads, merchant parcels).
- Java API: `ParcelEvents.eventsBetween(parcelId, fromSeq, toSeq)` (used for historical gap reconciliation).

---

## 5. Existing Messaging & Outbox Architecture

### 5.1 RabbitMQ Configuration
- Configured in `backend/src/main/resources/application.properties`:
  ```properties
  spring.rabbitmq.host=${SWIFTDROP_RABBITMQ_HOST:localhost}
  spring.rabbitmq.port=${SWIFTDROP_RABBITMQ_PORT:5672}
  spring.rabbitmq.username=${SWIFTDROP_RABBITMQ_USER:swiftdrop}
  spring.rabbitmq.password=${SWIFTDROP_RABBITMQ_PASSWORD}
  spring.rabbitmq.publisher-confirm-type=correlated
  spring.rabbitmq.publisher-returns=true
  spring.rabbitmq.template.mandatory=true
  ```

### 5.2 Exchanges & Queues
- **Exchange**: `swiftdrop.events`
  - Declared by `com.swiftdrop.delivery.infrastructure.outbox.ParcelEventsExchangeConfiguration`.
  - Type: Durable `TopicExchange`.
  - Routing Key: `parcel.status-changed`.
- **Queues**:
  - `notification.parcel-status-changed`: **Not yet declared anywhere**.
  - `merchant.parcel-status-changed`: **Not yet declared anywhere**.
- **Dead-Letter Exchange**:
  - `swiftdrop.dead-letter`: Specified in ADR 0004 §5.1, but **not yet declared** in source code.
- **Message Consumers**:
  - Currently **ZERO** `@RabbitListener` or Spring AMQP message consumers exist in any package.

### 5.3 Transactional Outbox Pattern (`delivery`)
The outbox system in `delivery.infrastructure.outbox` is fully operational and adheres to ADR 0004:
- **Table**: `delivery_parcel_outbox_events`
- **Locking**: `pg_try_advisory_xact_lock` ensures only a single outbox relay runner executes at any given time across cluster nodes.
- **Ordering**: Processes unpublished rows ordered by outbox ID. Per-parcel sequence order is maintained; if an event fails for a parcel, subsequent events for that same parcel are held back in that batch.
- **Retry & Backoff**: Exponential backoff recorded directly on outbox rows (`attempts`, `next_attempt_at`).
- **Publisher Confirms**: `OutboxPublisher` waits up to confirm-timeout (default 5000ms) for correlated acks before marking rows published.
- **Retention**: `OutboxRetention` periodically purges published rows older than the retention threshold.

### 5.4 End-to-End Event Flow
```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant DeliveryCtrl as Delivery / Hub Controller
    participant Service as ParcelBooking / Hub / Status Service
    participant Recorder as ParcelEventRecorder
    participant DB as PostgreSQL (Business Tables + Outbox Table)
    participant Relay as OutboxRelay (Scheduled Task)
    participant Broker as RabbitMQ (swiftdrop.events)
    participant Consumer as Future Notification Consumer

    Client->>DeliveryCtrl: Request status transition (e.g. deliver, pick up)
    DeliveryCtrl->>Service: Execute transition
    Service->>Recorder: record(parcel, prevStatus, actor, ...)
    Recorder->>DB: INSERT parcel_status_history + INSERT parcel_outbox_event (Same TX)
    Service-->>DeliveryCtrl: Commit DB Transaction
    DeliveryCtrl-->>Client: 200 OK / 201 Created

    Note over Relay,Broker: Asynchronous Decoupled Polling
    Relay->>DB: Read unpublished outbox rows (Advisory locked)
    Relay->>Broker: Publish JSON to exchange 'swiftdrop.events' (routing: 'parcel.status-changed')
    Broker-->>Relay: Publisher Confirm ACK
    Relay->>DB: Mark outbox row published_at = now()

    Note over Broker,Consumer: Consumer Delivery
    Broker->>Consumer: Deliver to 'notification.parcel-status-changed'
    Consumer->>DB: Deduplicate & verify sequence; reconcile gaps via ParcelEvents if needed
```

---

## 6. Complete Event Inventory

### 6.1 Existing RabbitMQ Events

| Event Type (Routing Key) | Envelope Record | Data Payload Record | Producing Module | Published Mechanism |
| :--- | :--- | :--- | :--- | :--- |
| `parcel.status-changed` | `com.swiftdrop.delivery.api.ParcelStatusChanged` | `com.swiftdrop.delivery.api.ParcelStatusChangedData` | `delivery` | Transactional Outbox Relay (`OutboxRelay` -> RabbitMQ `swiftdrop.events`) |

**Status values contained in `ParcelStatusChanged`**:
- `CREATED` (sequence 1)
- `PICKED_UP`
- `AT_HUB` (contains `hubCode`)
- `IN_TRANSIT` (contains `hubCode`)
- `OUT_FOR_DELIVERY` (contains `attempt`)
- `DELIVERY_ATTEMPT_FAILED` (contains `attempt`, `failureReasonCode`)
- `DELIVERED`
- `RETURNING`
- `RETURNED`
- `CANCELLED`
- `FAILED` (contains `failureReasonCode`)

### 6.2 Existing In-Process Events (Spring Application Events only)

| Event Record | Producing Module | Scope | Currently Consumed By |
| :--- | :--- | :--- | :--- |
| `ParcelStatusChanged` | `delivery.application.ParcelEventRecorder` | In-process Spring event | `com.swiftdrop.tracking.application.LiveTracking` (`@TransactionalEventListener`) |
| `MerchantApplicationSubmitted` | `merchant.application.MerchantRegistrationService` | In-process Spring event | None |
| `MerchantStatusChanged` | `merchant.application.MerchantAdminService` | In-process Spring event | None |

### 6.3 Missing / Suggested Events (Not in Codebase)
The following events are **NOT** published by the current backend:
- `courier.assigned` / `parcel.courier-assigned`: Not emitted. Courier assignments in `ParcelAssignmentService` update records in `delivery_parcel_assignments` without triggering status changes or outbox events.
- `merchant.registered` / `merchant.approved` (to RabbitMQ): Only exist as local in-memory Spring events.
- `user.registered`: No event published upon customer or staff registration.

---

## 7. Database and Migration Overview

Flyway controls the schema via `backend/src/main/resources/db/migration/` under module subdirectories (`delivery`, `identity`, `merchant`, `pricing`). `spring.jpa.hibernate.ddl-auto` is set to `validate`.

### 7.1 Existing Migration Ledger

| Module | Migration File | Tables Created / Modified |
| :--- | :--- | :--- |
| `identity` | `V202610251600__identity_create_users_and_roles.sql` | `identity_users`, `identity_roles`, `identity_user_roles` |
| `identity` | `V202610251700__identity_create_external_identities.sql` | `identity_external_identities` |
| `identity` | `V202610261800__identity_create_google_login_codes.sql` | `identity_google_login_codes` |
| `identity` | `V202611101200__identity_replace_dispatcher_admin_with_admin_and_dispatcher.sql` | Role split migration |
| `pricing` | `V202611021200__pricing_create_zones_and_pricing_rules.sql` | `pricing_zones`, `pricing_rules` |
| `delivery` | `V202611031000__delivery_create_parcels.sql` | `delivery_parcels` |
| `delivery` | `V202611031010__delivery_create_parcel_status_history.sql` | `delivery_parcel_status_history` |
| `delivery` | `V202611031020__delivery_create_delivery_proofs.sql` | `delivery_proofs` |
| `delivery` | `V202611031030__delivery_create_parcel_outbox_events.sql` | `delivery_parcel_outbox_events` |
| `delivery` | `V202611041200__delivery_add_dispatch_assignment_history.sql` | `delivery_parcel_assignments` |
| `delivery` | `V202611051210__delivery_add_parcel_assignment_version.sql` | Adds version column to assignments |
| `delivery` | `V202611101210__delivery_update_actor_role_for_admin_and_dispatcher.sql` | Role schema updates |
| `delivery` | `V202611111000__delivery_create_hubs.sql` | `delivery_hubs`, `delivery_hub_routes` |
| `delivery` | `V202611111010__delivery_add_hub_lifecycle.sql` | Hub lifecycle updates |
| `merchant` | `V202611041000__merchant_create_merchants.sql` | `merchant_merchants` |
| `merchant` | `V202611041010__merchant_create_merchant_status_history.sql` | `merchant_status_history` |
| `merchant` | `V202611041020__merchant_create_merchant_webhook_endpoints.sql` | `merchant_webhook_endpoints` |
| `merchant` | `V202611041030__merchant_create_merchant_webhook_endpoint_events.sql` | `merchant_webhook_endpoint_events` |

### 7.2 Tables & Fields Useful for Notifications

1. **`identity_users`**:
   - `id` (UUID PK): Maps to `customerId` or `courierId`.
   - `email` (VARCHAR 255): Primary destination for email notifications.
   - `full_name` (VARCHAR 100): Customer recipient name for personalized message templates.
   - `enabled` (BOOLEAN): Can prevent notifying deactivated users.

2. **`delivery_parcels`**:
   - `id` (UUID PK), `tracking_number` (VARCHAR): Referenced in notifications.
   - `sender_customer_id` (UUID FK): Target recipient for booking confirmations and return alerts.
   - `recipient_name` (VARCHAR), `recipient_phone` (VARCHAR): Necessary if SMS/WhatsApp notifications are extended to non-registered external recipients.
   - `assigned_courier_id` (UUID): Target if alerting courier of assignment or cancelation.

3. **`delivery_parcel_status_history`**:
   - `event_id` (UUID), `sequence` (INT), `status`, `occurred_at`: Source of truth for sequence validation and gap reconciliation via `ParcelEvents.eventsBetween`.

4. **Notification Module Database State**:
   - **Zero tables exist** for the notification module.
   - In accordance with ADR 0004 §4.3, the notification module will need tables prefixed with `notification_` (e.g., `notification_consumer_state`, `notification_pending_events`, `notification_logs`).

---

## 8. Authentication and Authorization

### 8.1 Mechanism & Token Claims
- Stateless JWT Bearer tokens validated through Spring Security Resource Server (`SecurityConfiguration.java`).
- `JwtAuthenticationConverter`:
  - Extracts subject as `UUID userId`.
  - Verifies user existence and `enabled == true` in database on every request via `UserDirectory.findUser(userId)`.
  - Extracts `roles` claim array (e.g. `["CUSTOMER"]`) and prefixes each with `ROLE_` for Spring Security authority mapping (`ROLE_CUSTOMER`, `ROLE_MERCHANT`, `ROLE_COURIER`, `ROLE_DISPATCHER`, `ROLE_ADMIN`).

### 8.2 Role Hierarchy & Actor Resolution
- Active roles: `CUSTOMER`, `MERCHANT`, `COURIER`, `DISPATCHER`, `ADMIN`.
- In `delivery.web.Callers`:
  - `Callers.userId(jwt)` extracts the caller's UUID.
  - `Callers.viewer(jwt)` constructs an immutable `Viewer(userId, roles)` used by application services for row-level ownership checks.

### 8.3 User-Level Data Isolation Patterns
- **Parcels**: `ParcelController` passes `caller.userId()` into `ParcelReadService`. Non-staff users are rejected if `parcel.customerId != caller.userId()`.
- **Merchants**: Secured with custom expression `@merchantAccess.isApproved(authentication)` and `@merchantAccess.getMerchantId(authentication)`, mapping the authenticated user ID to the corresponding merchant profile.
- **Implication for Notification API**:
  - Future endpoints such as `GET /api/v1/notifications` must extract caller UUID via `authentication.getName()` or `Jwt.getSubject()` and query `WHERE recipient_user_id = :userId`.
  - Only `ADMIN` or internal system components should ever access notifications across user boundaries.

---

## 9. Shared Infrastructure & Coding Conventions

### 9.1 Conventions in `com.swiftdrop.shared`
- **Global Error Handling**: `GlobalExceptionHandler` handles common framework exceptions (`MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `OptimisticLockingFailureException`, `AccessDeniedException`) producing RFC 9457 `ProblemDetail` structures.
- **Pagination**: Zero-indexed page parameters encapsulated in `PageLimit` and returned via `PageResponse<T>`.

### 9.2 Validation & DTOs
- Jakarta validation annotations (`@NotBlank`, `@NotNull`, `@Size`, `@Pattern`) on web request records.
- Immutability enforced: All API and event exchange models are Java `record`s with compact constructors performing precondition checks (`Objects.requireNonNull`).

### 9.3 Asynchronous Processing Patterns
- Scheduled background tasks run via `@Scheduled` (`OutboxRelay`, `OutboxRetention`) with configurable poll intervals.
- Mutual exclusion across application instances is achieved using PostgreSQL advisory locks:
  `SELECT pg_try_advisory_xact_lock(:key)`.
- Transaction event handling utilizes `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.

---

## 10. Notification Module – Current Status

A detailed scan of `backend/src/main/java/com/swiftdrop/notification/` reveals:

```
backend/src/main/java/com/swiftdrop/notification/
├── package-info.java
└── api/
    └── package-info.java
```

- **Domain Entities**: 0
- **Repositories**: 0
- **Services**: 0
- **Controllers**: 0
- **Message Consumers / Listeners**: 0
- **Database Migrations**: 0
- **Test Classes**: 0
- **External Email / SMS Integrations**: 0

**Conclusion**: The Notification module is currently an empty architectural placeholder with no implemented logic or infrastructure.

---

## 11. Existing Tests & Regression Protection

The backend test suite is comprehensive and actively enforces architectural and functional compliance.

### 11.1 Key Test Suites & Regressions to Protect

| Test Class | Purpose & Scope | Regression Risk if Broken |
| :--- | :--- | :--- |
| `com.swiftdrop.ModuleBoundaryTest` | Scans all source files against ADR 0003 package access rules | **High**: Any illegal import into or from `notification` will fail the build |
| `com.swiftdrop.MigrationConventionTest` | Verifies Flyway migration file prefixes, ordering, and SQL formatting conventions | **High**: Any malformed SQL or improper naming will fail tests |
| `com.swiftdrop.DatabaseMigrationIT` | Executes all Flyway migrations against test containers | Schema conflict or invalid DDL |
| `com.swiftdrop.delivery.infrastructure.outbox.OutboxRelayIT` | Verifies outbox polling, confirms, retry backoff, and advisory locking | Ensuring `delivery` outbox stays untouched |
| `com.swiftdrop.delivery.ParcelAuthorizationMatrixIT` | Validates role-based parcel endpoint security matrix | Authorization leaks |
| `com.swiftdrop.tracking.LiveTrackingIT` | Verifies transactional in-memory event consumption | Transaction phase ordering |

All existing tests must remain 100% passing when introducing the notification module.

---

## 12. Integration Opportunities

1. **Exchange Reuse**: The durable topic exchange `swiftdrop.events` is already declared and operational. `notification` can declare its own durable quorum queue `notification.parcel-status-changed` and bind it with routing key `parcel.status-changed`.
2. **Payload Compatibility**: `ParcelStatusChangedData` already includes `parcelId`, `trackingNumber`, `previousStatus`, `newStatus`, `sequence`, `source`, `ownerCustomerId`, `ownerMerchantId`, `failureReasonCode`, `hubCode`, and `attempt`.
3. **Consumer Gap Reconciliation**: `delivery.api.ParcelEvents.eventsBetween(...)` is already implemented and tested in `ParcelEventHistory.java`, providing an existing mechanism for the notification consumer to recover missing events when sequence gaps are detected (as required by ADR 0004 §4.3).
4. **User Contact Lookup**: `identity.api.UserDirectory.findUser(UUID)` is already in place and provides the customer's full name, email, and enabled status.

---

## 13. Risks and Architectural Considerations

1. **ADR 0003 Module Boundary Violation**:
   - `notification` is only permitted to depend on `identity.api` and `delivery.api`.
   - It cannot reference `delivery.domain`, `delivery.infrastructure`, `merchant`, or `pricing`.
2. **Outbox Interference**:
   - The notification module **must not modify** or write to `delivery_parcel_outbox_events`. Outbox tables are strictly owned by their publishing module.
3. **Missing Courier Assignment Events**:
   - In the current code, courier assignment (`ParcelAssignmentService`) does **not** generate a `ParcelStatusChanged` event or write to the outbox. If notifications are required when a courier is assigned, either an architectural decision must be made to emit an event from assignment, or courier assignment notifications cannot be triggered via the outbox.
4. **Duplicate Message Handling**:
   - Because outbox publishing guarantees at-least-once delivery, RabbitMQ retries or relay redeliveries can send duplicate events. The notification consumer must maintain an idempotent processed-events ledger (`last_applied_sequence` and `processed_event_ids`) as mandated by ADR 0004 §4.3.
5. **Slow Third-Party Notification Providers**:
   - If real or mock email/SMS providers block or timeout during message consumption, they could stall RabbitMQ worker threads. The dispatch of external notifications should be decoupled or have strict timeouts.

---

## 14. Missing Components Required for Notifications

When implementation commences, the following components will need to be introduced:

1. **Database Schema (Flyway Migration)**:
   - Migration under `backend/src/main/resources/db/migration/notification/` creating:
     - `notification_consumer_state` (tracks `parcel_id`, `last_applied_sequence`).
     - `notification_pending_events` (stores out-of-order events pending gap resolution).
     - `notification_records` (logs sent/mocked notifications with timestamps, channel, recipient user ID, status).
2. **RabbitMQ Consumer Configuration**:
   - Queue declaration: `notification.parcel-status-changed`.
   - Binding declaration to exchange `swiftdrop.events`.
   - Dead-letter exchange (`swiftdrop.dead-letter`) and queue configuration (`notification.parcel-status-changed.dlq`).
3. **Consumer & Reconciliation Engine**:
   - `@RabbitListener` consuming `ParcelStatusChanged` messages.
   - Idempotency & deduplication logic.
   - Gap reconciliation worker leveraging `ParcelEvents.eventsBetween(...)`.
4. **Notification Sender / Service**:
   - Notification template engine or formatter (supporting status changes: `CREATED`, `PICKED_UP`, `OUT_FOR_DELIVERY`, `DELIVERED`, `DELIVERY_ATTEMPT_FAILED`, `FAILED`, `CANCELLED`).
   - Mock notification sender service (or email sender) that logs notification dispatches without leaking sensitive customer PII.
5. **Public API & Web Endpoints (If User-Facing In-App Notifications are Required)**:
   - `NotificationController` exposing `/api/v1/notifications` filtered by caller JWT user ID.
6. **Test Coverage**:
   - Architecture test updates (verifying boundaries).
   - Consumer unit tests and integration tests with RabbitMQ / Testcontainers.

