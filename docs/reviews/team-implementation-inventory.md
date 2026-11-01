# ACT-06 — SwiftDrop Team Implementation Inventory and Integration Coordination

> **Document Status:** Complete Team Implementation Inventory & Integration Roadmap  
> **Date:** 2026-11-01  
> **Target Audience:** SwiftDrop Development Team (Members 1–10), System Architect, Faculty Evaluators  
> **Baseline Audit Reference:** [`docs/reviews/foundation-readiness-audit.md`](foundation-readiness-audit.md)  
> **Related Architecture Decisions:** ADR 0001–0006; D-01–D-32  

---

## 1. Executive Summary

This inventory assesses the actual development status across all 10 team workstreams (WS1–WS10) following the completion of the Foundation Readiness Audit (FRA-01).

### Key Takeaways:
1. **Repository Health & Stability:**
   - Backend: **187 unit/integration tests passing (0 failures, 0 errors, 0 skipped)**.
   - Frontend: **Next.js 16.4.3 production build succeeded** (22 static routes prerendered, ESLint clean).
   - Test coverage tooling: **JaCoCo 0.8.12** configured and operational in Maven and GitHub Actions CI.
2. **Workstream Progress Disparity:**
   - **WS1 (Identity & Foundations):** Substantially complete (JWT, BCrypt, Google OAuth code exchange, RBAC, Admin user management, module boundary test).
   - **WS3 (Delivery Core):** Architecture finalized (ADR 0006 Accepted, ACT-05B contract refinement complete). Ready for immediate entity, state machine, and interface implementation.
   - **WS6 & WS8 (Pricing & Delivery Proof):** Public contract API shells exist, but domain logic and database migrations are not yet implemented.
   - **WS2, WS4, WS5, WS7, WS9, WS10:** Frontend portal layouts exist with placeholder cards, and backend package descriptors (`package-info.java`) exist, but domain entities and REST controllers are not yet implemented.
3. **Integration Blockers Identified:**
   - **Delivery Public API Contracts:** Downstream modules (`merchant`, `dispatch`, `notification`, `tracking`) require the concrete declaration of `delivery.api` interfaces (SD-204 / SD-205) to compile and write MockMvc / integration tests.
   - **Database Schemas:** Flyway migrations exist solely for `identity`. `delivery` and `pricing` schemas must be introduced to enable cross-module persistence.
   - **Messaging Infrastructure:** While ADR 0004 defines outbox and message structures, runtime components (`parcel_outbox_events` and `OutboxRelay`) remain unwritten.

---

## 2. Confirmed Ten-Member Workstream Allocation

The workstream structure is confirmed based on [`docs/work-plan.md`](../work-plan.md) and [`docs/implementation-contract.md`](../implementation-contract.md):

| Workstream | Assigned Owner | Core Module / Scope | Key Deliverables / Issue IDs |
|---|---|---|---|
| **WS1** | **Member 1** (Lead / Architecture) | `identity`, `config`, `shared`, Frontend App Shell | SD-002, SD-003, SD-005, SD-006, SD-007, SD-008, SD-101 to SD-104, SD-106, SD-107 |
| **WS2** | **Member 2** | Customer Booking, History & Receipts | SD-105, SD-206, SD-207, SD-208, SD-209, SD-210, SD-901 |
| **WS3** | **Member 3** | Delivery Core, State Machine, Outbox Write, Public API | SD-204, SD-205, SD-301, SD-302, SD-303, SD-304, SD-505, SD-902 |
| **WS4** | **Member 4** | Merchant Registration, Approval, Dashboard, Webhook Endpoints | SD-401, SD-402, SD-406, SD-407, SD-408, SD-412, SD-907 |
| **WS5** | **Member 5** | Merchant API Keys, Order Intake, Webhook Delivery | SD-403, SD-404, SD-405, SD-409, SD-410, SD-411, SD-413, SD-905 |
| **WS6** | **Member 6** | Courier Operations, Delivery Proof & Status Updates | SD-309, SD-310, SD-311, SD-312, SD-313, SD-316 |
| **WS7** | **Member 7** | Dispatcher Assignment, Workload Balancer, Quality Lead | SD-004, SD-305, SD-306, SD-307, SD-308, SD-703, SD-704, SD-903, SD-908, SD-909 |
| **WS8** | **Member 8** | Pricing Engine, Zones, Pricing Rules, Delivery Reports | SD-201, SD-202, SD-203, SD-211, SD-212, SD-314, SD-315, SD-906 |
| **WS9** | **Member 9** | Outbox Relay, RabbitMQ Events, Notifications | SD-501, SD-502, SD-503, SD-504, SD-506, SD-507, SD-701, SD-904 |
| **WS10** | **Member 10** | Tracking SSE, Public Tracking UI, Dockerfiles, Demo | SD-009, SD-508, SD-510, SD-603, SD-801, SD-802, SD-803, SD-804, SD-910 |

---

## 3. Team Implementation Matrix

| Field | WS1 (M1) | WS2 (M2) | WS3 (M3) | WS4 (M4) | WS5 (M5) | WS6 (M6) | WS7 (M7) | WS8 (M8) | WS9 (M9) | WS10 (M10) |
|---|---|---|---|---|---|---|---|---|---|---|
| **Module** | `identity`, `config`, `shared` | `delivery` (customer web) | `delivery` (core domain) | `merchant` (portal/admin) | `merchant` (API/intake) | `delivery` (courier web) | `dispatch` | `pricing` | `notification`, outbox relay | `tracking`, infra |
| **Planned Issues** | SD-002, 003, 101-104 | SD-105, 206-210 | SD-204, 205, 301-304 | SD-401, 402, 406-408 | SD-403-405, 409-411 | SD-309-313 | SD-004, 305-308 | SD-201-203, 314-315 | SD-501-504, 506-507 | SD-009, 508, 801-804 |
| **Actual Code Found** | Full controllers, entities, JWT, Google OAuth, BCrypt, `UserDirectory` | UI placeholder cards in `(customer)` | Enums (`ParcelStatus`, `FailureReason`, `ParcelSource`), `ParcelSummary` | UI placeholder cards in `(merchant)` | Package shell `merchant.api` | UI placeholder cards in `(courier)` | Package shell `dispatch.api`, UI placeholder `(dispatch)` | `FeeCalculator`, `FeeQuote`, `ParcelSize` interfaces | Package shell `notification.api` | UI `/track`, package shell `tracking.api` |
| **Tests** | 187 automated tests (14 test classes) | None | None | None | None | None | JaCoCo test config (ACT-03) | None | None | None |
| **Database** | 3 migrations (`roles`, `users`, `user_roles`, `external_identities`, `google_login_codes`) | None | None | None | None | None | None | None | None | None |
| **Public Contracts** | Provides `UserDirectory`, `UserSummary`, `IdentityClaims` | Consumes `delivery.api`, `pricing.api` | Provides `ParcelCreation`, `ParcelAssignment`, `ParcelQueries`, `ParcelEvents` | Consumes `UserDirectory`, `ParcelQueries` | Consumes `ParcelCreation` | Consumes `delivery` internal services | Consumes `UserDirectory`, `ParcelAssignment`, `ParcelQueries` | Provides `FeeCalculator`, `FeeQuote` | Consumes `ParcelStatusChangedEvent`, `ParcelEvents` | Consumes `ParcelQueries`, `ParcelStatusChangedEvent` |
| **Dependencies** | None | WS3 (Delivery), WS8 (Pricing) | WS1 (Identity), WS8 (Pricing) | WS1 (Identity), WS3 (Delivery) | WS4 (Merchant owner), WS3 (Delivery) | WS3 (Delivery core) | WS1 (Identity), WS3 (Delivery) | WS1 (Identity) | WS3 (Delivery outbox) | WS3 (Delivery queries/events) |
| **Blockers** | None | Blocked on WS3 `ParcelCreation` & WS8 `FeeCalculator` | Blocked on declaring `delivery.api` (SD-204) & schema | Blocked on WS3 `ParcelQueries` & merchant schema | Blocked on WS3 `ParcelCreation` contract | Blocked on WS3 schema & proof storage port | Blocked on WS3 `ParcelAssignment` contract | Blocked on Flyway migration (SD-201) | Blocked on WS3 outbox events & broker queue bindings | Blocked on WS3 tracking query contract |
| **Readiness** | **READY** | **CONDITIONAL** | **READY FOR DEV** | **CONDITIONAL** | **CONDITIONAL** | **CONDITIONAL** | **CONDITIONAL** | **READY FOR DEV** | **CONDITIONAL** | **CONDITIONAL** |

---

## 4. Branch and Worktree Discovery

### Local & Remote Inventory:
- **Local Checked Out:** `feat/foundation-checker` (HEAD at `33efda1`). Clean working tree.
- **`dev` / `origin/dev`:** Synchronized at `d90f602`. All foundational identity code and tests are merged here.
- **`issue-automation` / `origin/issue-automation`:** Contains automated issue creation tooling (`scripts/create-swiftdrop-issues.mjs` and `scripts/issue-definitions.mjs` at `b74a876`).
- **`chore/ci-dev-triggers`:** Historical experiment for CI triggers, superseded by `.github/workflows/ci.yml`.
- **`origin/SEO---phase-2`:** Obsolete frontend experiment.

### Member Accessibility Assessment:
All code currently committed by team members exists in the centralized remote Git repository and is present locally. No developer-local working copies are detached or inaccessible.

---

## 5. Shared-File Ownership & Conflict Prevention Matrix

To eliminate merge conflicts during parallel implementation across 10 developers, the following ownership rules are strictly established:

| Shared File / Area | Sole Primary Owner | Secondary Reviewer | Rule for Other Team Members |
|---|---|---|---|
| `backend/pom.xml` | **Member 1** (WS1) | Member 7 (WS7) | **NO DIRECT EDITS.** Dependency additions require prior architectural approval and review by M1. |
| `backend/src/main/resources/application.properties` | **Member 1** (WS1) | Member 3 (WS3) | Each module adds configuration keys only under its dedicated prefix (`swiftdrop.<module>.*`). |
| `compose.yaml` | **Member 10** (WS10) | Member 1 (WS1) | Do not edit port allocations or existing service definitions without M10 approval. |
| `db/migration/` (Flyway scripts) | **Each Module Owner** in subfolder | Member 1 (WS1) | **Strict Partitioning:** Developers modify ONLY their assigned folder: `db/migration/<module>/`. Never edit another module's migrations. |
| `com.swiftdrop.config` | **Member 1** (WS1) | — | Composition root. Zero business logic. Modules must never import `config`. |
| `com.swiftdrop.shared` | **Member 1** (WS1) | All | Contains technical web/error formats only. No business domain types allowed. |
| `frontend/lib/api.ts` | **Member 1** (WS1) | Member 2 (WS2) | Central API client. Extend via helper methods; do not break `apiFetch` signature. |
| `frontend/app/layout.tsx` & nav shell | **Member 1** (WS1) | Member 4 (WS4) | Global shell. Developers implement routes only inside their own route groups: `(customer)`, `(merchant)`, `(courier)`, `(dispatch)`, `(admin)`. |
| `.github/workflows/ci.yml` | **Member 7** (WS7) | Member 1 (WS1) | Quality lead owns CI stages and test coverage execution. |

---

## 6. Cross-Module Dependency & Contract Map

```mermaid
graph TD
    subgraph CoreProviders ["Foundational Service Providers"]
        M1_ID["WS1: Member 1<br/>identity.api (UserDirectory)"]
        M8_PR["WS8: Member 8<br/>pricing.api (FeeCalculator)"]
        M3_DEL["WS3: Member 3<br/>delivery.api (Creation, Assignment, Queries, Events)"]
    end

    subgraph BusinessConsumers ["Downstream Workstreams"]
        M2_CUS["WS2: Member 2<br/>Customer Booking & Portal"]
        M7_DIS["WS7: Member 7<br/>Dispatching & Courier Workloads"]
        M5_MER_API["WS5: Member 5<br/>Merchant API Orders & Key Auth"]
        M4_MER_UI["WS4: Member 4<br/>Merchant Dashboard & Webhook Endpoints"]
        M6_COU["WS6: Member 6<br/>Courier Proof & Status Updates"]
        M9_MSG["WS9: Member 9<br/>Notification Broker & Outbox Relay"]
        M10_TRK["WS10: Member 10<br/>Live Tracking SSE & Public Page"]
    end

    M1_ID -->|User Lookups| M3_DEL
    M8_PR -->|Fee Quotes| M3_DEL
    M8_PR -->|Fee Estimates| M2_CUS

    M3_DEL -->|ParcelCreation| M5_MER_API
    M3_DEL -->|ParcelAssignment| M7_DIS
    M3_DEL -->|ParcelQueries (Workload)| M7_DIS
    M3_DEL -->|ParcelQueries (Orders)| M4_MER_UI
    M3_DEL -->|ParcelQueries (Public)| M10_TRK
    M3_DEL -->|ParcelStatusChangedEvent| M9_MSG
    M3_DEL -->|ParcelEvents (Reconciliation)| M9_MSG
    M3_DEL -->|In-Process Event| M10_TRK
    M3_DEL -->|Internal Proof Service| M6_COU
    M3_DEL -->|Internal Booking Service| M2_CUS
```

---

## 7. Parallel Development Boundaries

To maximize team velocity while preserving architectural integrity, work is classified into four operational tiers:

### Tier A: Work That Can Proceed Immediately (Zero Shared Blockers)
- **WS1 (Member 1):** Shared test harness improvements, documentation updates, frontend auth token refresh interceptors.
- **WS8 (Member 8):** Implement `pricing` database schema (`V...__pricing_create_zones_and_pricing_rules.sql`), `Zone` and `PricingRule` entities, and `FeeCalculator` implementation.
- **WS3 (Member 3):** Implement Tracking Number Generator (`SD-205`), parcel database schema (`SD-204`), and declare the `delivery.api` interfaces/records refined in ACT-05B.
- **WS10 (Member 10):** Author multi-stage Dockerfiles (`backend/Dockerfile`, `frontend/Dockerfile`) and verify local Docker Compose startup.

### Tier B: Work That Can Proceed Using Agreed Interfaces or Mocks
- **WS7 (Member 7 - Dispatch):** Build courier profile entities and dispatch assignment application service using a mock/stub of `delivery.api.ParcelAssignment` and `ParcelQueries`.
- **WS5 (Member 5 - Merchant API):** Build API key generation, hashing (`X-Api-Key` security filter), and order intake controller using a mock of `delivery.api.ParcelCreation`.
- **WS4 (Member 4 - Merchant UI):** Build merchant registration, admin approval state machine, and webhook endpoint persistence.
- **WS10 (Member 10 - Tracking):** Build SSE emitter controller and tracking UI mock stream consuming `PublicTrackingView`.

### Tier C: Work Requiring Shared Architecture or Database Decisions
- **WS9 (Member 9 - Outbox Relay & Messaging):** Requires the `parcel_outbox_events` table migration to be finalized by WS3 before writing the polling query.
- **WS6 (Member 6 - Proof of Delivery):** Requires the `delivery_proofs` table migration to be finalized by WS3 before writing the proof upload controller.

### Tier D: Work Requiring Another Module's Production Implementation
- **WS2 (Member 2 - Customer Booking Wireup):** Requires WS3 `ParcelBookingService` and WS8 `FeeCalculator` to execute end-to-end booking tests.

---

## 8. Proposed Next Two Development Waves

### Development Wave 1: Foundational Schemas & Contract Freeze (Sprint Target: Days 1–3)

| Task | Owner | Actionable Deliverable | Success Criteria |
|---|---|---|---|
| **W1-T1** | **Member 3 (WS3)** | Implement Issue **SD-204**: Declare `delivery.api` interfaces (`ParcelCreation`, `ParcelAssignment`, `ParcelQueries`, `ParcelEvents`) and records (`ParcelStatusChangedEvent`, `PageRequest`, `PageResult`). | `./backend/mvnw.cmd test -Dtest=ModuleBoundaryTest` passes. |
| **W1-T2** | **Member 3 (WS3)** | Create Flyway migration `V202611021200__delivery_create_parcels_and_history.sql` and `V202611021201__delivery_create_proofs_and_outbox.sql`. | Pass `MigrationConventionTest`. |
| **W1-T3** | **Member 8 (WS8)** | Create Flyway migration `V202611021300__pricing_create_zones_and_rules.sql` and implement `FeeCalculator`. | Unit tests for fee calculation formula pass. |
| **W1-T4** | **Member 10 (WS10)** | Author `backend/Dockerfile` and `frontend/Dockerfile`. | `docker compose build` succeeds. |

### Development Wave 2: Domain Logic & Parallel Consumer Integration (Sprint Target: Days 4–7)

| Task | Owner | Actionable Deliverable | Success Criteria |
|---|---|---|---|
| **W2-T1** | **Member 3 (WS3)** | Implement `DeliveryStateMachine` enforcing ADR 0006 transitions and write to outbox table. | Unit test matrix testing all legal/illegal transitions passes. |
| **W2-T2** | **Member 2 (WS2)** | Implement customer booking `POST /api/v1/parcels` and connect frontend booking form. | Customer can submit parcel booking in UI. |
| **W2-T3** | **Member 7 (WS7)** | Implement courier assignment `POST /api/v1/parcel-assignments` consuming `ParcelAssignment`. | Dispatcher can assign couriers to `CREATED` parcels. |
| **W2-T4** | **Member 5 (WS5)** | Implement `POST /api/v1/merchant-orders` consuming `ParcelCreation`. | API order creates parcel idempotently via API key. |
| **W2-T5** | **Member 9 (WS9)** | Implement `OutboxRelay` scheduled publisher to RabbitMQ exchange `swiftdrop.events`. | Outbox rows are published and acknowledged. |
| **W2-T6** | **Member 6 (WS6)** | Implement courier proof upload `PUT /api/v1/parcels/{id}/proof`. | Courier can upload proof in `OUT_FOR_DELIVERY`. |
| **W2-T7** | **Member 10 (WS10)**| Connect `tracking` SSE controller to in-process domain events. | Browser receives live status update. |

---

## 9. Questions Requiring Team Decisions

1. **Adoption of PR-01 (Pagination Record Decoupling):** Does the team approve replacing `org.springframework.data.domain.Pageable` with `PageRequest(page, size)` and `PageResult<T>` in `delivery.api`? *(Recommended: YES, preserves module decoupling).*
2. **Adoption of PR-02 (Tenant Query Isolation):** Does Member 4/5 approve requiring explicit `UUID merchantId` on all merchant queries, returning HTTP 404 for mismatches? *(Recommended: YES, satisfies security requirements).*
3. **Database Column Type for Proof Photo:** Confirm `BYTEA` storage in PostgreSQL for v1 local demonstration vs. external object storage S3 mock. *(Recommended: BYTEA in PostgreSQL with a 2MB validation cap).*
4. **Demonstration Profile Timing:** Should the D-27 demo data initializer (`@Profile("demo")`) be implemented in Wave 2 alongside the state machine? *(Recommended: YES, accelerates parallel testing).*

