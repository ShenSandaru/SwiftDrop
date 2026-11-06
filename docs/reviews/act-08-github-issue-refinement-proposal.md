# ACT-08: GitHub Issue Backlog Refinement Proposal

- **Document ID:** ACT-08 (Revised & Reconciled per ACT-08.1)
- **Date:** 2026-11-04
- **Audited Commit / Branch:** `dev` at `681d019df6f0be88e21aed16e6e94d85a706adc1` (identical to `main`, `origin/dev`, and `origin/main`)
- **Baseline Sources:**
  - ACT-07 Progress Audit (`docs/reviews/act-07-project-progress-audit.md`)
  - Live GitHub Issue Repository State (`ShenSandaru/SwiftDrop`, issues `#8`–`#78`)
  - Merged PRs (`#81`, `#83`, `#84`, `#85`, `#87`) and Git commit logs
- **Mode:** Strictly READ-ONLY (no issue, code, test, migration, ADR, or PR modifications)

---

## 1. Executive Summary

This proposal provides an evidence-based refinement plan for the SwiftDrop GitHub issue backlog (`#8` through `#78`, 71 issues total).

### Key Takeaways

1. **Large Completed Scope Unclosed on GitHub:**
   - Out of **71 total issues**, only **1 issue (`#11 SD-201`) is currently `CLOSED`** on GitHub.
   - Exactly **18 OPEN issues are fully implemented Close Candidates** whose original functional acceptance criteria have been satisfied in merged code (`dev`/`main`).
   - 1 additional issue (`#16 SD-205`) is functionally implemented in code, but its acceptance criteria require a formal amendment to reflect Decision D-05 (Crockford Base32 format) prior to closure.
   - Keeping these completed issues open misrepresents project velocity and obscures the true remaining critical path.

2. **Resolution of Previous Audit Inconsistencies (Re-audited per ACT-08.1):**
   - **Close Candidate Arithmetic Reconciled:** The backlog contains exactly **18 OPEN close candidates** where original acceptance criteria match merged code, **1 ALREADY CLOSED** issue (`#11`), and **5 issues needing refinement** (including `#16`, whose AC format `SD-YYYYMMDD-XXXXXXXX` was superseded by Decision D-05).
   - **Assignee Count Verified:** Live metadata confirms **6 issues** have assignees (`#11`, `#16`, `#17`, `#18`, `#35`, `#47`), representing **6 distinct team members**. 65 of 71 issues (91.5%) remain unassigned.
   - **Completion Metrics Adjusted:** The subjective 44.8% completion percentage has been replaced with verified, reproducible implementation statistics across backlog units, backend modules, and end-to-end business flows.
   - **Architecture & Contract Drift Addressed:**
     - `ADR 0007` (Public SSE Tracking) is implemented on `main` but remains marked `Proposed`.
     - `SD-508` specified `/api/v1/tracked-parcels/{trackingNumber}/live` and a 15s heartbeat, while merged code and `ADR 0007` use `/events` and a 25s heartbeat.
     - `SD-203` specified a public quote endpoint (`PUB`), but the merged endpoint requires authentication (`401` when anonymous).
     - `SD-703` conflated JaCoCo coverage artifact upload (already completed under `SD-004`) with Surefire/Failsafe XML archiving and running `-Pintegration` in CI.

3. **Critical Stabilization Blockers (P0):**
   - **Fresh Database Usability:** In a clean deployment, customer parcel booking fails immediately (`409 Conflict: Route not priced`) because no zones or pricing rules exist (missing admin CRUD `SD-211` and missing `demo` seed `SD-009`).
   - **Browser Form Blocker:** In `frontend/app/(customer)/customer/new/page.tsx:169`, `min="0.001" step="0.1"` triggers native HTML5 `stepMismatch` validation failure for standard weights (`0.5`, `1`, `2` kg), preventing booking submission.
   - **Integration Test Failures:** `mvnw clean verify -Pintegration` fails with 2 test failures (`DeliveryQueriesIT` timestamp precision mismatch, `DatabaseMigrationIT` `.env` credential contamination) and a 30s Tomcat shutdown hang (`LiveTrackingIT`).

4. **Recommended Backlog Restructuring:**
   - **Close 18 Open Issues** that meet all original acceptance criteria directly.
   - **Refine 5 Incomplete / Ambiguous Issues** (`#10`, `#16`, `#37`, `#54`, `#63`).
   - **Address 4 Partially Implemented Issues** (`#13`, `#33`, `#56`, `#59`).
   - **Adopt 10 Decoupled, Single-Responsibility Proposed Issues** (`NEW-01A` through `NEW-10`) to repair defects, enforce security, add missing foreign keys, build admin interfaces, establish frontend component testing early, and align governance.

---

## 2. Audit Methodology and Scope

### Audit Scope
- Live GitHub issue state for `ShenSandaru/SwiftDrop` issues `#8` through `#78` (71 issues).
- Full source inspection of `backend/`, `frontend/`, `docs/`, `compose.yaml`, and `.github/workflows/ci.yml`.
- Cross-referencing against merged Pull Requests (`#1`–`#7`, `#79`–`#88`).
- Verification against test executions: `mvnw clean test`, `mvnw clean verify`, `mvnw clean verify -Pintegration`, `npm run lint`, and `npm run build`.

### Mutually Exclusive Classification Definitions
Every issue `#8`–`#78` is placed into exactly one of the following mutually exclusive categories:
- **IMPLEMENTED — CLOSE CANDIDATE:** Original acceptance criteria are fully satisfied in merged code on `dev`/`main`.
- **IMPLEMENTED (ALREADY CLOSED):** Formally closed on GitHub and verified satisfied.
- **PARTIALLY IMPLEMENTED:** Meaningful code exists in merged PRs, but critical functional criteria, browser validation, or test assertions remain incomplete.
- **NEEDS REFINEMENT:** The issue requirement remains valid, but scope, terminology, acceptance criteria, or architectural approach must be updated prior to closure or execution.
- **STILL VALID:** Work has not yet been meaningfully implemented; the original issue remains relevant and actionable.
- **BLOCKED:** Work cannot proceed until an upstream dependency, missing module, or broker infrastructure is in place.
- **DUPLICATE / OVERLAPPING:** Scope duplicates or significantly overlaps another issue and needs consolidation.
- **OUTDATED:** The issue is no longer relevant due to superseding architectural decisions.

---

## 3. Verified Current Backlog Statistics

### 3.1 Reconciled Classification Counts (Sum = 71)

| Category | Count | Percentage of Backlog | Issues Included |
|---|---:|---:|---|
| **IMPLEMENTED — CLOSE CANDIDATE** | **18** | 25.4% | `#8`, `#12`, `#17`, `#18`, `#19`, `#20`, `#21`, `#22`, `#23`, `#24`, `#25`, `#26`, `#29`, `#30`, `#31`, `#32`, `#34`, `#60` |
| **IMPLEMENTED (ALREADY CLOSED)** | **1** | 1.4% | `#11` (`SD-201`, closed in PR `#83`) |
| **PARTIALLY IMPLEMENTED** | **4** | 5.6% | `#13`, `#33`, `#56`, `#59` |
| **NEEDS REFINEMENT** | **5** | 7.0% | `#10`, `#16`, `#37`, `#54`, `#63` |
| **STILL VALID** | **29** | 40.8% | `#9`, `#14`, `#15`, `#27`, `#28`, `#35`, `#36`, `#38`, `#39`, `#40`, `#41`, `#42`, `#43`, `#44`, `#45`, `#46`, `#47`, `#51`, `#52`, `#61`, `#64`, `#65`, `#66`, `#68`, `#69`, `#71`, `#73`, `#74`, `#77` |
| **BLOCKED** | **14** | 19.7% | `#48`, `#49`, `#50`, `#53`, `#55`, `#57`, `#58`, `#62`, `#67`, `#70`, `#72`, `#75`, `#76`, `#78` |
| **DUPLICATE / OVERLAPPING** | **0** | 0.0% | *(Scope overlap between `#13` and `#14` on `GET /api/v1/zones` documented)* |
| **OUTDATED** | **0** | 0.0% | *(No obsolete issues; stale notes on 9 issues superseded by PR `#81`/`#85`)* |
| **TOTAL** | **71** | **100.0%** | **All issues `#8`–`#78` accounted for without gaps or duplicates.** |

### 3.2 Issues with Assignees (Re-audited Verification)

ACT-07 previously noted: *"Only 5 issues carry assignees (`#11`, `#16`, `#17`, `#18`, `#35`, `#47`)."*
Re-verification against live GitHub metadata confirms that **6 issues** have assignees, representing **6 distinct team members**:

| Issue | Title | GitHub State | Assignee(s) |
|---|---|---|---|
| **#11** | `SD-201`: Implement pricing schema, zones, pricing rules, and JPA repositories | CLOSED | `vishmibulathsinhala`, `AsiriJayakodi` |
| **#16** | `SD-205`: Implement secure tracking-number generator with collision retry | OPEN | `dhananjaya-hbc` |
| **#17** | `SD-204`: Implement Parcel aggregate, status history schema, and JPA repositories | OPEN | `dhananjaya-hbc` |
| **#18** | `SD-301`: Implement delivery status state machine and history transitions | OPEN | `dhananjaya-hbc` |
| **#35** | `SD-305`: Implement courier profile management and eligibility schema in dispatch | OPEN | `ShaliniBangamuwage` |
| **#47** | `SD-409`: Implement SSRF-safe HTTP client for webhook delivery | OPEN | `Ashankavinda277`, `ThulaniThehara` |

*65 of 71 issues (91.5%) currently have no assigned owner on GitHub.*

### 3.3 Verifiable Implementation Statistics (Replacing Subjective Percentages)

In place of an arbitrary single percentage, project progress is measured across three objective dimensions:

1. **Backlog Unit Progress:**
   - Fully Implemented: **19 / 71 issues (26.8%)** (1 closed + 18 close candidates).
   - Partially Implemented: **4 / 71 issues (5.6%)**.
   - Remaining Work: **48 / 71 issues (67.6%)** (5 needs refinement + 29 still valid + 14 blocked).

2. **Modular Monolith Implementation Depth:**
   - `identity`: **Implemented & Verified** (Email/password, JWT, Google OIDC, admin user management, staff onboarding, `UserDirectory` facade).
   - `pricing`: **Partially Implemented** (Zone & rule persistence, `ZonePairFeeCalculator` strategy, quote endpoint; admin CRUD endpoints and seed profile missing).
   - `delivery`: **Substantially Implemented** (`Parcel` aggregate, 6-state lifecycle machine, status history, 2-step POD, transactional outbox row persistence, customer/courier/public endpoints; outbox relay publisher and admin volume reports missing).
   - `tracking`: **Implemented** (Public SSE live stream, in-process event listener; `ADR 0007` still marked `Proposed`).
   - `dispatch`: **Scaffolded Only (0%)** (`package-info.java` only; no entities, migrations, or endpoints).
   - `merchant`: **Scaffolded Only (0%)** (`package-info.java` only; no entities, migrations, API key filter, or endpoints).
   - `notification`: **Scaffolded Only (0%)** (`package-info.java` only; no entities, migrations, or consumers).
   - `shared / messaging`: **Documented Only (0%)** (RabbitMQ broker running, but zero AMQP topology classes or outbox relays exist).

3. **End-to-End Business Flow Operational Readiness:**
   - **Flow 1 (Customer Booking & Live SSE Tracking):** **~80% Code Complete, but Operationally Blocked.** Blocked by: (a) zero zones/rules in fresh database, and (b) weight input HTML5 validation error on `/customer/new`.
   - **Flow 2 (Programmatic Merchant Order & Webhook):** **0% Complete.** Neither API key filter, order intake, nor webhook dispatch is implemented.
   - **Flow 3 (Courier Assignment & Proof Capture):** **~50% Code Complete, but Workflow Broken.** Courier mobile UI and proof capture work, but courier assignment cannot be executed via API/UI because `dispatch` is unstarted (courier must be assigned via manual database SQL update).
   - **Flow 4 (Asynchronous Customer Notifications):** **0% Complete.** Notification consumer and inbox are unstarted.

---

## 4. Complete Issue Classification (#8–#78)

The table below classifies every issue in the backlog based on direct inspection of merged code, tests, and database migrations.

| Issue # | Title | GitHub State | ACT-08 Reconciled Classification | Merged PR / Implementation Evidence | Remaining Work / Blocker |
|---|---|---|---|---|---|
| **#8** | `SD-004`: Configure measurable JaCoCo code coverage in Maven and CI | OPEN | **IMPLEMENTED — CLOSE CANDIDATE** | PR `#81`: `pom.xml:146-170`, `.github/workflows/ci.yml:56-62`, `ADR 0002:35` | None. Ready to close. |
| **#9** | `SD-107`: Perform and document real Google OAuth round-trip verification | OPEN | **STILL VALID** | Code merged in PR `#7` (SD-104); verification unrecorded | Live OIDC run + update `docs/architecture.md §9`. |
| **#10** | `SD-009`: Implement demo and seed data for local evaluation and profiles | OPEN | **NEEDS REFINEMENT** | No `@Profile("demo")` runner exists | Split into Phase 1 (pricing/users) and Phase 2 (couriers/merchants). |
| **#11** | `SD-201`: Implement pricing schema, zones, pricing rules, and JPA repositories | CLOSED | **IMPLEMENTED (ALREADY CLOSED)** | PR `#83`: `V202611021200`, `Zone`, `PricingRule` | Already closed on GitHub. |
| **#12** | `SD-202`: Implement FeeCalculator strategy service in the pricing module | OPEN | **IMPLEMENTED — CLOSE CANDIDATE** | PR `#84`: `ZonePairFeeCalculator`, `ZonePairFeeCalculatorTest` | None. Ready to close. |
| **#13** | `SD-203`: Implement public delivery price-quote REST endpoint | OPEN | **PARTIALLY IMPLEMENTED** | PR `#84`: `PriceQuoteController`, `POST /api/v1/price-quotes` | Endpoint requires auth; contract decision needed on public access. |
| **#14** | `SD-211`: Implement pricing administration REST endpoints for zones and rules | OPEN | **STILL VALID** | Only `GET /api/v1/zones` (active) exists | Admin list, create, edit, toggle endpoints for zones & rules. |
| **#15** | `SD-212`: Implement admin pricing and zone management UI pages | OPEN | **STILL VALID** | Placeholder page at `/admin/pricing` | Interactive UI for zone/rule management. Depends on #14. |
| **#16** | `SD-205`: Implement secure tracking-number generator with collision retry | OPEN | **NEEDS REFINEMENT** | PR `#85`: `TrackingNumberGenerator`, Crockford Base32 format | Update AC pattern from `SD-YYYYMMDD-XXXXXXXX` to D-05, then close. |
| **#17** | `SD-204`: Implement Parcel aggregate, status history schema, and JPA repositories | OPEN | **IMPLEMENTED — CLOSE CANDIDATE** | PR `#85`: `V202611031000..1010`, `Parcel`, `ParcelStatusHistory` | None. Ready to close. (Zone FK tracked in NEW-06). |
| **#18** | `SD-301`: Implement delivery status state machine and history transitions | OPEN | **IMPLEMENTED — CLOSE CANDIDATE** | PR `#85`: `ParcelLifecycle`, `ParcelStatusService`, unit/IT tests | None. Ready to close. |
| **#19** | `SD-302`: Implement delivery.api query and assignment contracts | OPEN | **IMPLEMENTED — CLOSE CANDIDATE** | PR `#85`: `ParcelQueries`, `ParcelAssignment`, `ParcelCreation` | None. Ready to close. |
| **#20** | `SD-303`: Implement transactional outbox recording for parcel status events | OPEN | **IMPLEMENTED — CLOSE CANDIDATE** | PR `#85`: `V202611031030`, `OutboxEvent`, `ParcelEventRecorder` | None. Ready to close. (Relay is SD-502; microsecond fix is NEW-01A). |
| **#21** | `SD-304`: Implement public parcel tracking lookup REST endpoint | OPEN | **IMPLEMENTED — CLOSE CANDIDATE** | PR `#85`: `TrackingController`, `GET /api/v1/tracked-parcels/{tn}` | None. Ready to close. |
| **#22** | `SD-309`: Implement courier daily deliveries list endpoint | OPEN | **IMPLEMENTED — CLOSE CANDIDATE** | PR `#85`: `CourierController.today`, `CourierParcelService` | None. Ready to close. |
| **#23** | `SD-310`: Implement courier status transition endpoint with delivery notes | OPEN | **IMPLEMENTED — CLOSE CANDIDATE** | PR `#85`: `CourierController.changeStatus`, note cap, 404/409 | None. Ready to close. |
| **#24** | `SD-311`: Implement proof of delivery capture and photo attachment | OPEN | **IMPLEMENTED — CLOSE CANDIDATE** | PR `#85`: `V202611031020`, `PUT /proof`, magic-byte sniffing | None. Ready to close. (Endpoint is HTTP PUT per ADR 0006). |
| **#25** | `SD-312`: Implement standardized failure reason recording for undeliverable parcels | OPEN | **IMPLEMENTED — CLOSE CANDIDATE** | PR `#85`: `FailureReason` (5 codes), SQL constraint | None. Ready to close. |
| **#26** | `SD-313`: Implement courier mobile portal UI for deliveries, proof, and status updates | OPEN | **IMPLEMENTED — CLOSE CANDIDATE** | PR `#85`: `app/(courier)/courier/page.tsx`, `parcels/[id]/page.tsx` | None. Ready to close. |
| **#27** | `SD-314`: Implement delivery performance and failure rate reporting endpoints | OPEN | **STILL VALID** | None. Zero report endpoints exist | Unblocked. Implement reporting endpoints in delivery module. |
| **#28** | `SD-315`: Implement administrative reports dashboard UI | OPEN | **STILL VALID** | Placeholder page at `/admin/reports` | Connect to SD-314 endpoints. |
| **#29** | `SD-316`: Implement parcel ownership and role authorization test suite (TST-2) | OPEN | **IMPLEMENTED — CLOSE CANDIDATE** | PR `#85`: `ParcelAuthorizationMatrixIT` (36 matrix checks) | None. Ready to close. |
| **#30** | `SD-206`: Implement customer parcel booking REST endpoint | OPEN | **IMPLEMENTED — CLOSE CANDIDATE** | PR `#85`: `ParcelController.create`, `ParcelBookingService` | None. Ready to close. |
| **#31** | `SD-207`: Implement customer delivery history and parcel detail endpoints | OPEN | **IMPLEMENTED — CLOSE CANDIDATE** | PR `#85`: `ParcelController.list`, `ParcelController.detail` | None. Ready to close. |
| **#32** | `SD-208`: Implement delivery receipt endpoint and printable receipt format | OPEN | **IMPLEMENTED — CLOSE CANDIDATE** | PR `#85`: `ParcelController.receipt`, `ReceiptResponse` | None. Ready to close. |
| **#33** | `SD-209`: Implement customer booking flow with live fee quote estimator UI | OPEN | **PARTIALLY IMPLEMENTED** | PR `#85`: `app/(customer)/customer/new/page.tsx` | Fix HTML5 `stepMismatch` attribute blocking submission (NEW-02). |
| **#34** | `SD-210`: Implement customer delivery history list and printable receipt pages UI | OPEN | **IMPLEMENTED — CLOSE CANDIDATE** | PR `#85`: `/customer/history`, `/parcels/[id]`, `/receipt` | None. Ready to close. |
| **#35** | `SD-305`: Implement courier profile management and eligibility schema in dispatch | OPEN | **STILL VALID** | `dispatch` module has only `package-info.java` | Create `couriers` table and admin profile endpoints. |
| **#36** | `SD-306`: Implement unassigned parcel listing and courier assignment workflow | OPEN | **STILL VALID** | `delivery.api` contracts ready; `dispatch` unstarted | Implement dispatch endpoints; depends on #35. |
| **#37** | `SD-307`: Implement active courier workload query endpoint | OPEN | **NEEDS REFINEMENT** | Unimplemented | Refine method call to `countActiveParcelsByCourier()`. |
| **#38** | `SD-308`: Implement dispatcher console UI for unassigned parcels and courier assignment | OPEN | **STILL VALID** | Placeholder page at `/dispatch` | Implement dispatch console UI. Depends on #35, #36, #37. |
| **#39** | `SD-401`: Implement merchant registration and pending merchant aggregate | OPEN | **STILL VALID** | `merchant` module has only `package-info.java` | Create `merchants` table and registration flow. |
| **#40** | `SD-402`: Implement admin merchant review, approval, rejection, and suspension | OPEN | **STILL VALID** | None | Admin approval endpoints. Depends on #39. |
| **#41** | `SD-403`: Implement merchant API key generation, hashing, and revocation | OPEN | **STILL VALID** | None | API key schema, generation, SHA-256 storage. Depends on #39. |
| **#42** | `SD-404`: Implement X-Api-Key authentication filter for merchant endpoints | OPEN | **STILL VALID** | None | Security filter for `/api/v1/merchant-orders/**`. Depends on #41. |
| **#43** | `SD-405`: Implement idempotent merchant delivery order intake REST API | OPEN | **STILL VALID** | `delivery.api.ParcelCreation` ready; `merchant` unstarted | Idempotent intake controller. Depends on #42. |
| **#44** | `SD-406`: Implement merchant portal dashboard orders query endpoint | OPEN | **STILL VALID** | `delivery.api.ParcelQueries` ready; `merchant` unstarted | Merchant portal orders query. Depends on #39. |
| **#45** | `SD-407`: Implement merchant registration, dashboard, and API-key UI pages | OPEN | **STILL VALID** | Placeholders at `/merchant/*` | Functional merchant UI. Depends on #39, #41, #44. |
| **#46** | `SD-408`: Implement webhook endpoint registration with encrypted secret storage | OPEN | **STILL VALID** | None | Webhook endpoint registration & AES-256-GCM secret encryption. |
| **#47** | `SD-409`: Implement SSRF-safe HTTP client for webhook delivery | OPEN | **STILL VALID** | None | Implement SSRF-safe Apache HttpClient5. |
| **#48** | `SD-410`: Implement RabbitMQ merchant event consumer and webhook delivery recording | OPEN | **BLOCKED** | None | Blocked by #46 (endpoints), #52 (topology), #54 (consumer). |
| **#49** | `SD-411`: Implement outbound webhook sender with HMAC signatures and exponential backoff | OPEN | **BLOCKED** | None | Blocked by #46, #47, #48. |
| **#50** | `SD-412`: Implement webhook delivery history and manual redelivery API and UI | OPEN | **BLOCKED** | Placeholder at `/merchant/webhooks` | Blocked by #48, #49. |
| **#51** | `SD-413`: Implement standalone demo webhook receiver and merchant integration guide | OPEN | **STILL VALID** | None | Standalone test tool; can be built in parallel. |
| **#52** | `SD-501`: Declare RabbitMQ topic exchange, queues, and dead-letter topology | OPEN | **STILL VALID** | None. Zero AMQP configuration classes exist | Declare exchange, queues, DLX, publisher confirms. |
| **#53** | `SD-502`: Implement scheduled transactional outbox relay publisher | OPEN | **BLOCKED** | Table `parcel_outbox_events` exists; relay unstarted | Blocked by #52 (RabbitMQ exchange & confirms). |
| **#54** | `SD-503`: Implement shared ordered event consumer support and sequence deduplication | OPEN | **NEEDS REFINEMENT** | None | Refine to prevent `shared` from importing `delivery.api`. |
| **#55** | `SD-504`: Implement notification consumer and mock SMS delivery store | OPEN | **BLOCKED** | `notification` module has only `package-info.java` | Blocked by #52, #54. |
| **#56** | `SD-505`: Implement delivery.api.ParcelEvents query for sequence gap reconciliation | OPEN | **PARTIALLY IMPLEMENTED** | PR `#85`: `ParcelEvents.eventsBetween` exists | Fails `DeliveryQueriesIT` due to nanosecond precision drift (NEW-01A). |
| **#57** | `SD-506`: Implement customer notification inbox API and frontend notification center | OPEN | **BLOCKED** | None | Blocked by #55. |
| **#58** | `SD-507`: Implement admin dead-letter queue inspection and audited replay endpoint | OPEN | **BLOCKED** | None | Blocked by #52. |
| **#59** | `SD-508`: Implement Server-Sent Events (SSE) live parcel tracking endpoint | OPEN | **PARTIALLY IMPLEMENTED** | PR `#85`: `TrackingStreamController`, `LiveTracking` | Refine path (`/events`), heartbeat (`25s`), accept ADR 0007. |
| **#60** | `SD-510`: Implement public live tracking web page with real-time SSE updates | OPEN | **IMPLEMENTED — CLOSE CANDIDATE** | PR `#85`: `app/track/page.tsx` with live `EventSource` | None. Ready to close. (Route is `/track?code=...`). |
| **#61** | `SD-603`: Complete responsive layout and accessibility audit across all frontend portals | OPEN | **STILL VALID** | Auth, customer, courier responsive; 8 portals pending | Defer to Wave 3 after all portal pages are functional. |
| **#62** | `SD-701`: Implement end-to-end event-flow integration test with RabbitMQ container (TST-1) | OPEN | **BLOCKED** | None | Blocked by #51, #52, #54, #55, #48. |
| **#63** | `SD-703`: Configure Surefire/Failsafe test results and coverage reporting in CI (TST-3) | OPEN | **NEEDS REFINEMENT** | JaCoCo upload done in #8; test XMLs not uploaded | Refine to add Surefire/Failsafe XML upload and `-Pintegration`. |
| **#64** | `SD-704`: Perform project-wide security, authorization, and personal-data privacy audit | OPEN | **STILL VALID** | Partial matrix tests exist; project-wide audit needed | Wave 3 final audit. |
| **#65** | `SD-801`: Create multi-stage production Dockerfile for backend service | OPEN | **STILL VALID** | None. `backend/Dockerfile` does not exist | Create multi-stage Temurin JRE 21 Dockerfile. |
| **#66** | `SD-802`: Create multi-stage production Dockerfile for Next.js frontend | OPEN | **STILL VALID** | None. `frontend/Dockerfile` does not exist | Create multi-stage standalone Next.js Dockerfile. |
| **#67** | `SD-803`: Configure full-stack application profile in compose.yaml | OPEN | **BLOCKED** | `compose.yaml` defines DB and broker only | Blocked by #65, #66. |
| **#68** | `SD-804`: Update root README with verified submission and demo setup guide | OPEN | **STILL VALID** | Outdated root README | Interim update now; final submission guide in Wave 3. |
| **#69** | `SD-901`: Create and commit system Use Case diagrams (DES-1) | OPEN | **STILL VALID** | `docs/design/use-cases/` does not exist | Author use case diagrams. |
| **#70** | `SD-902`: Create and commit complete Entity-Relationship (ER) diagram (DES-2) | OPEN | **BLOCKED** | `docs/design/er/` does not exist | Blocked until remaining schema migrations are designed. |
| **#71** | `SD-903`: Author architecture proposal and modular monolith design report (DES-3) | OPEN | **STILL VALID** | `docs/design/architecture-proposal.md` does not exist | Author architecture proposal document. |
| **#72** | `SD-904`: Author messaging, outbox, and event integration design document (DES-4) | OPEN | **BLOCKED** | `docs/design/integration-design.md` does not exist | Blocked by completion of messaging & outbox implementation. |
| **#73** | `SD-905`: Create sequence and activity diagrams for core business workflows (DES-5) | OPEN | **STILL VALID** | `docs/design/sequence/` does not exist | Author workflow sequence diagrams. |
| **#74** | `SD-906`: Author software design patterns application summary (DES-6) | OPEN | **STILL VALID** | `docs/design/design-patterns.md` does not exist | Document patterns across merged codebase. |
| **#75** | `SD-907`: Compile and assemble comprehensive system design documentation PDF (SUB-2) | OPEN | **BLOCKED** | None | Blocked by DES-1..6 completion. |
| **#76** | `SD-908`: Compile test report and automated test coverage summary (SUB-3) | OPEN | **BLOCKED** | None | Blocked by green `-Pintegration` and test metrics. |
| **#77** | `SD-909`: Compile individual team contribution tracking sheet (SUB-4) | OPEN | **STILL VALID** | None | Reconcile team contributions against PRs and commits. |
| **#78** | `SD-910`: Create scripted live demonstration walkthrough and rehearsal guide (DEM-1..6) | OPEN | **BLOCKED** | None | Blocked by all demo flows and Docker application profile. |

---

## 5. Close Candidates with Evidence

The following **18 OPEN issues** are fully implemented in merged code and are formally recommended for closure. For each issue, the satisfied acceptance criteria and verification locations are detailed below.

### 5.1 Quality & Tooling
- **#8 (SD-004): Configure measurable JaCoCo code coverage in Maven and CI**
  - *Satisfied Criteria:* JaCoCo plugin 0.8.12 configured in `backend/pom.xml`; XML and HTML reports generated on `verify`; CI workflow (`.github/workflows/ci.yml:56-62`) uploads `jacoco-coverage-report`; `ADR 0002` updated.
  - *Verification:* `backend/pom.xml:146-170`, `docs/adr/0002-technology-and-version-baseline.md:35`.
  - *Note:* Close candidate; distinguished from `-Pintegration` CI execution (covered in refined #63).

### 5.2 Pricing Engine
- **#12 (SD-202): Implement FeeCalculator strategy service in the pricing module**
  - *Satisfied Criteria:* `FeeCalculator` interface implemented by `ZonePairFeeCalculator`; base + weight + size surcharge logic with ceiling kg rounding; throws domain exception mapped to 409 Conflict.
  - *Verification:* `com.swiftdrop.pricing.application.ZonePairFeeCalculator`, `ZonePairFeeCalculatorTest`.
  - *Note:* Fully isolated and tested. Ready to close.

### 5.3 Delivery Domain, Aggregate & Lifecycle
- **#17 (SD-204): Implement Parcel aggregate, status history schema, and JPA repositories**
  - *Satisfied Criteria:* Flyway migrations `V202611031000` and `V202611031010`; `Parcel` aggregate with embedded addresses and XOR ownership; sequence tracking; optimistic locking with `@Version`.
  - *Verification:* `com.swiftdrop.delivery.domain.Parcel`, `ParcelRepository`, `DeliveryPersistenceIT`.
  - *Note:* Deferred foreign keys from `parcels` to `zones` tracked in proposed new issue NEW-06.

- **#18 (SD-301): Implement delivery status state machine and history transitions**
  - *Satisfied Criteria:* Strict state transitions per ADR 0006; mandatory `FailureReason` on `FAILED`; monotonic sequence numbers; atomic history persistence.
  - *Verification:* `com.swiftdrop.delivery.domain.ParcelLifecycle`, `ParcelLifecycleTest`, `ParcelStatusChangeIT`.

- **#19 (SD-302): Implement delivery.api query and assignment contracts**
  - *Satisfied Criteria:* Public contracts `ParcelQueries`, `ParcelAssignment`, `ParcelCreation`, `ParcelEvents` implemented in `com.swiftdrop.delivery.api`; strict isolation and no PII leakage.
  - *Verification:* `com.swiftdrop.delivery.api.*`, `DeliveryApiContractTest`.

- **#20 (SD-303): Implement transactional outbox recording for parcel status events**
  - *Satisfied Criteria:* `parcel_outbox_events` table created (`V202611031030`); atomic event recording in same transaction as status history; zero PII in payload.
  - *Verification:* `com.swiftdrop.delivery.domain.OutboxEvent`, `ParcelEventRecorder`, `ParcelEventSerializerTest`.
  - *Note:* Microsecond precision fix for gap queries tracked in NEW-01A.

- **#25 (SD-312): Implement standardized failure reason recording for undeliverable parcels**
  - *Satisfied Criteria:* 5 standardized failure reason codes; database check constraint; transition validation returns HTTP 400 when missing.
  - *Verification:* `com.swiftdrop.delivery.api.FailureReason`, `V202611031010`, `ParcelLifecycleTest`.

### 5.4 Delivery Operations & Endpoints
- **#21 (SD-304): Implement public parcel tracking lookup REST endpoint**
  - *Satisfied Criteria:* `GET /api/v1/tracked-parcels/{trackingNumber}` public lookup; returns cities, status, and transition timeline; zero PII; returns 404 for invalid number.
  - *Verification:* `com.swiftdrop.delivery.web.TrackingController`, `TrackingApiTest`.

- **#22 (SD-309): Implement courier daily deliveries list endpoint**
  - *Satisfied Criteria:* `GET /api/v1/courier/parcels` restricted to `COURIER` role; returns assigned active deliveries plus deliveries completed today in business timezone.
  - *Verification:* `com.swiftdrop.delivery.web.CourierController.today`, `CourierApiTest`.

- **#23 (SD-310): Implement courier status transition endpoint with delivery notes**
  - *Satisfied Criteria:* `POST /api/v1/parcels/{id}/status-changes` restricted to assigned courier; returns 404 for unassigned courier; caps note at 500 characters; returns 409 for invalid transitions.
  - *Verification:* `com.swiftdrop.delivery.web.CourierController.changeStatus`, `CourierApiTest`.

- **#24 (SD-311): Implement proof of delivery capture and photo attachment**
  - *Satisfied Criteria:* `delivery_proofs` table (`V202611031020`); multipart photo upload; magic-byte image validation (JPEG/PNG <= 2MB); required before `DELIVERED` transition.
  - *Verification:* `CourierController.captureProof`, `DeliveryProofService`, `PhotoTypeTest`.
  - *Clarification:* Merged endpoint uses HTTP `PUT /api/v1/parcels/{id}/proof` rather than draft `POST`.

- **#29 (SD-316): Implement parcel ownership and role authorization test suite (TST-2)**
  - *Satisfied Criteria:* Exhaustive matrix testing asserting 404 on unowned access for customers and unassigned couriers, 401 for anonymous, and 403 where forbidden.
  - *Verification:* `com.swiftdrop.delivery.ParcelAuthorizationMatrixIT` (36 test combinations).

- **#30 (SD-206): Implement customer parcel booking REST endpoint**
  - *Satisfied Criteria:* `POST /api/v1/parcels` restricted to `CUSTOMER`; server-recalculated fee via `FeeCalculator`; atomic creation of parcel, history sequence 1, and outbox event; returns 201 Created with Location.
  - *Verification:* `com.swiftdrop.delivery.web.ParcelController.create`, `ParcelBookingIT`.

- **#31 (SD-207): Implement customer delivery history and parcel detail endpoints**
  - *Satisfied Criteria:* `GET /api/v1/parcels` (paged) and `GET /api/v1/parcels/{id}`; non-owner receives 404; full status timeline returned.
  - *Verification:* `ParcelController.list`, `ParcelController.detail`, `ParcelReadServiceTest`.

- **#32 (SD-208): Implement delivery receipt endpoint and printable receipt format**
  - *Satisfied Criteria:* `GET /api/v1/parcels/{id}/receipt` available throughout parcel lifecycle; non-owner receives 404; returns immutable pricing snapshot.
  - *Verification:* `ParcelController.receipt`, `ParcelReadServiceTest`.

### 5.5 Frontend User Interfaces
- **#26 (SD-313): Implement courier mobile portal UI for deliveries, proof, and status updates**
  - *Satisfied Criteria:* Mobile-optimized view at `/courier` and `/courier/parcels/[id]`; status progression buttons; camera photo capture; failure modal with reason dropdown; lint and build pass.
  - *Verification:* `frontend/app/(courier)/courier/page.tsx`, `parcels/[id]/page.tsx`.

- **#34 (SD-210): Implement customer delivery history list and printable receipt pages UI**
  - *Satisfied Criteria:* Paged history at `/customer/history`; detail view at `/customer/parcels/[id]`; dedicated receipt view with `@media print` CSS at `/customer/parcels/[id]/receipt`.
  - *Verification:* `frontend/app/(customer)/customer/history/page.tsx`, `receipt/page.tsx`.

- **#60 (SD-510): Implement public live tracking web page with real-time SSE updates**
  - *Satisfied Criteria:* Public tracking page at `frontend/app/track/page.tsx`; visual milestone timeline; live updates via browser `EventSource`; unauthenticated and responsive at 360px.
  - *Verification:* `frontend/app/track/page.tsx`.
  - *Clarification:* Implemented using query parameter `/track?code=...` integrated with the landing page hero bar.

---

## 6. Issues Requiring Refinement

The following **5 issues** are valid in objective but require specific scope, acceptance criteria, or architectural refinements.

### 6.1 Issue #10 (SD-009): Implement demo and seed data for local evaluation and profiles
- **Current State:** OPEN | **Audited Classification:** NEEDS REFINEMENT
- **Why Refinement Is Needed:** The issue currently bundles delivery zones, pricing rules, courier profiles, and merchant accounts into a single monolithic task. Because the `dispatch` and `merchant` modules do not exist yet, this issue cannot be completed as written, leaving fresh environments with empty pricing tables and blocking parcel booking.
- **Proposed Refinements:**
  - *Revised Scope:* Split into two distinct phases. Phase 1 (Seed delivery zones, default pricing rules, and bootstrap admin/courier user accounts) can proceed immediately. Phase 2 (Courier vehicle profiles and merchant business accounts) executes after `SD-305` and `SD-401`.
  - *Acceptance Criteria:*
    - Seed runner activated exclusively under `spring.profiles.active=demo`.
    - Fully idempotent: multiple restarts do not produce duplicate zone codes or rules.
    - Zero sample/seed data placed in production Flyway migrations.
    - Unblocks customer parcel booking on fresh databases immediately.

### 6.2 Issue #16 (SD-205): Implement secure tracking-number generator with collision retry
- **Current State:** OPEN | **Audited Classification:** NEEDS REFINEMENT (Pre-Closure AC Alignment)
- **Why Refinement Is Needed:** The code in `com.swiftdrop.delivery.domain.TrackingNumberGenerator` is 100% written, unit-tested, and integrated into `ParcelBookingService` with bounded retry. However, its original issue description and acceptance criteria specify `Format: SD-YYYYMMDD-XXXXXXXX`. Decision D-05 (formally incorporated into ADR 0006) superseded this format with `'SD'` + 10 Crockford Base32 characters (`^SD[0-9A-HJKMNP-TV-Z]{10}$`).
- **Proposed Refinements:**
  - Update issue acceptance criteria to reference Decision D-05 and the Crockford Base32 pattern.
  - Once the description reflects the architectural decision, the issue can be immediately marked **CLOSED**.

### 6.3 Issue #37 (SD-307): Implement active courier workload query endpoint
- **Current State:** OPEN | **Audited Classification:** NEEDS REFINEMENT
- **Why Refinement Is Needed:** The issue description specifies calling `delivery.api.ParcelQueries.findActiveCountsByCourier()`. In merged PR `#85`, the contract method is actually named `countActiveParcelsByCourier()`, which returns a `Map<UUID, Long>` excluding zero-count couriers.
- **Proposed Refinements:**
  - Update issue description to reference `delivery.api.ParcelQueries.countActiveParcelsByCourier()`.
  - Clarify that `dispatch` is responsible for querying its own `couriers` table to include idle couriers (`count = 0`).

### 6.4 Issue #54 (SD-503): Implement shared ordered event consumer support and sequence deduplication
- **Current State:** OPEN | **Audited Classification:** NEEDS REFINEMENT
- **Why Refinement Is Needed:** The issue places sequence reconciliation in `com.swiftdrop.shared.messaging`. However, `ModuleBoundaryTest:81` strictly asserts that `shared` must not import any business module. If `shared.messaging` calls `delivery.api.ParcelEvents` directly, `ModuleBoundaryTest` will fail.
- **Proposed Refinements:**
  - Define a generic gap-resolver interface / callback in `com.swiftdrop.shared.messaging` (e.g., `SequenceGapLoader<T>`).
  - Business consumer modules (`notification`, `merchant`) provide the concrete adapter linking the callback to `delivery.api.ParcelEvents`.
  - Update issue scope and acceptance criteria to maintain architectural boundaries.

### 6.5 Issue #63 (SD-703): Configure Surefire/Failsafe test results and coverage reporting in CI (TST-3)
- **Current State:** OPEN | **Audited Classification:** NEEDS REFINEMENT
- **Why Refinement Is Needed:** The issue conflates JaCoCo coverage upload (which was already completed under `SD-004` / PR `#81`) with publishing test execution summaries. Crucially, the issue does not mandate executing `-Pintegration` in CI, which allowed two integration test failures to land on `main`.
- **Proposed Refinements:**
  - *Revised Scope:* Add execution of `./mvnw clean verify -Pintegration` to `.github/workflows/ci.yml`.
  - Upload Maven Surefire and Failsafe XML/HTML test reports as CI build artifacts.
  - Explicitly mark JaCoCo artifact upload as already completed by `SD-004`.

---

## 7. Proposed New Issues

To address defects, security vulnerabilities, schema integrity, and governance gaps discovered during the audit, the following **10 decoupled, single-responsibility issues** are proposed.

### NEW-01A: [P0 Bug] Truncate Outbox Event Timestamps to Microseconds
- **Type:** Bug | **Priority:** P0 | **Workstream:** Delivery / Messaging
- **Problem Statement:** `DeliveryQueriesIT.rebuiltEventsEqualTheOutboxPayloads` fails because `ParcelEventRecorder` serializes JDK 21 nanosecond `Instant`s into `parcel_outbox_events.payload`, whereas PostgreSQL `TIMESTAMPTZ` rounds `parcel_status_history.occurred_at` to microseconds. When `ParcelEvents.eventsBetween` reconstructs events from history, payload comparison fails on timestamp precision.
- **Scope:** Truncate `Instant` to `ChronoUnit.MICROS` across `ParcelBookingService`, `ParcelStatusService`, `MerchantParcelCreation`, and `ParcelEventRecorder`.
- **Acceptance Criteria:** `DeliveryQueriesIT.rebuiltEventsEqualTheOutboxPayloads` passes cleanly against PostgreSQL 18.
- **Dependencies:** None.

### NEW-01B: [P0 Bug] Isolate DatabaseMigrationIT from Host .env Bootstrap Credentials
- **Type:** Bug | **Priority:** P0 | **Workstream:** Identity / Testing
- **Problem Statement:** `DatabaseMigrationIT.flywayAppliesAllMigrationsAndHibernateValidationSucceeds` fails (`expected 0 users, was 1`) when a local `.env` file sets `SWIFTDROP_BOOTSTRAP_ADMIN_EMAIL` and password, because the test does not clear bootstrap properties.
- **Scope:** Add `@TestPropertySource(properties = {"swiftdrop.identity.bootstrap.admin-email=", "swiftdrop.identity.bootstrap.admin-password="})` to `DatabaseMigrationIT`.
- **Acceptance Criteria:** `DatabaseMigrationIT` passes 100% cleanly regardless of host `.env` contents.
- **Dependencies:** None.

### NEW-01C: [P0 Bug] Close HTTP SSE Stream in LiveTrackingIT to Prevent 30s Shutdown Hang
- **Type:** Bug | **Priority:** P0 | **Workstream:** Tracking / Testing
- **Problem Statement:** `LiveTrackingIT.rolledBackChangesAreNeverPushed` opens an HTTP SSE stream without closing it, causing Tomcat graceful shutdown to hang for 30 seconds until Maven Failsafe force-kills the forked JVM.
- **Scope:** Wrap the SSE `InputStream` in `try-with-resources` or explicitly close the response stream in `LiveTrackingIT`.
- **Acceptance Criteria:** `mvnw clean verify -Pintegration` terminates cleanly immediately after test completion with zero JVM force-kill warnings.
- **Dependencies:** None.

### NEW-02: [P0 Bug] Fix Customer Booking Form Weight Input Step & Validation
- **Type:** Bug | **Priority:** P0 | **Workstream:** Frontend (Customer)
- **Problem Statement:** In `frontend/app/(customer)/customer/new/page.tsx:169`, `<Input id="weight" min="0.001" step="0.1" />` sets the HTML5 step base to 0.001, making valid values `0.001, 0.101, ..., 1.001`. Standard integer weights (`1`, `2`, `5` kg) trigger native `stepMismatch` validation and block form submission.
- **Scope:** Update weight input to `step="0.001"` (or `min="0.1" step="0.1"`). Add client-side validation capping courier photo uploads at 2 MB.
- **Acceptance Criteria:** Customers can book parcels with weights `0.5`, `1`, `2`, `10` kg in browsers without validation errors; `npm run lint` and `npm run build` pass.
- **Dependencies:** None.

### NEW-03: [P0 Bug] Add Google OAuth Callback and Portals to Public Navigation Paths
- **Type:** Bug | **Priority:** P0 | **Workstream:** Frontend (Auth)
- **Problem Statement:** `publicPaths` in `frontend/lib/auth-context.tsx:48` omits `/auth/google/callback` and `/portals`. A 401 response during code exchange triggers a global redirect to `/login?expired=true`, hijacking the callback page's inline error message.
- **Scope:** Add `"/auth/google/callback"` and `"/portals"` to `publicPaths` in `auth-context.tsx`.
- **Acceptance Criteria:** Google OAuth exchange errors display error details directly on the callback screen without redirect loops.
- **Dependencies:** None.

### NEW-04: [P0 Security] Validate User Enabled Status on Active JWT Requests
- **Type:** Security | **Priority:** P0 | **Workstream:** Identity / Security
- **Problem Statement:** JWTs are validated statelessly for 15 minutes without checking `users.enabled`. A deactivated administrator, courier, or customer retains full mutating API access across `/api/v1/users/**`, `/api/v1/parcels/**`, and `/api/v1/courier/parcels` until token expiration (`SEC-01`).
- **Scope:** Implement a lightweight security filter or custom `JwtAuthenticationConverter` verifying that the principal's account is enabled via `identity.api.UserDirectory`.
- **Acceptance Criteria:** Deactivated user accounts immediately receive HTTP 401/403 on subsequent requests even with an unexpired JWT.
- **Dependencies:** None.

### NEW-05: [P0 Bug] Map Spring MVC 4xx Exceptions to RFC 9457 Problem Details
- **Type:** Bug | **Priority:** P0 | **Workstream:** Shared Web
- **Problem Statement:** `GlobalExceptionHandler` maps unhandled Spring MVC exceptions to HTTP 500. `MethodArgumentTypeMismatchException` (e.g. invalid UUIDs), `HttpRequestMethodNotSupportedException` (405), and `NoResourceFoundException` (404) return 500 outside `delivery.web` (`SEC-02`).
- **Scope:** Add explicit `@ExceptionHandler` methods in `GlobalExceptionHandler` mapping Spring MVC 4xx exceptions to `ProblemDetail`. Add `@Order(Ordered.HIGHEST_PRECEDENCE)` to `IdentityExceptionHandler`.
- **Acceptance Criteria:** Malformed UUIDs, unsupported HTTP methods, and invalid routes consistently return 400/404/405 RFC 9457 problem details.
- **Dependencies:** None.

### NEW-06: [P1 Database] Add Foreign Keys from Parcels to Zones and Pricing Rules
- **Type:** Database Integrity | **Priority:** P1 | **Workstream:** Delivery / Database
- **Problem Statement:** `V202611031000:19` omitted foreign keys from `parcels.pickup_zone_id`, `parcels.dropoff_zone_id`, and `parcels.pricing_rule_id` to `zones(id)` and `pricing_rules(id)`.
- **Scope:** Create Flyway migration `V202611031040__delivery_add_pricing_foreign_keys.sql` adding `REFERENCES zones(id)` and `REFERENCES pricing_rules(id)`.
- **Acceptance Criteria:** Migration applies cleanly against PostgreSQL 18; `DatabaseMigrationIT` and `MigrationConventionTest` pass.
- **Dependencies:** `SD-201` (merged).

### NEW-07: [P1 Security] Restrict Proof Photo Endpoint Access to Delivered Parcels
- **Type:** Security | **Priority:** P1 | **Workstream:** Delivery
- **Problem Statement:** `DeliveryProofService.photo()` exposes proof photos for parcels in `OUT_FOR_DELIVERY` or `FAILED` status (`SEC-05`), leaking photos for undelivered parcels.
- **Scope:** Update `DeliveryProofService.photo()` to verify that `parcel.getStatus() == ParcelStatus.DELIVERED`.
- **Acceptance Criteria:** `GET /api/v1/parcels/{id}/proof/photo` returns HTTP 404 unless parcel status is `DELIVERED`.
- **Dependencies:** None.

### NEW-08: [P1 Frontend] Implement Admin Overview Dashboard and Merchant Review Console
- **Type:** Feature | **Priority:** P1 | **Workstream:** Frontend (Admin)
- **Problem Statement:** `SD-402` defines backend endpoints for reviewing and approving merchants, but no GitHub issue or page exists for the Admin Merchant Review UI (`/admin/merchants`), and `/admin` remains a blank placeholder.
- **Scope:** Implement `/admin` dashboard overview with summary metrics and navigation cards. Implement `/admin/merchants` interface displaying pending merchant applications with Approve, Reject, and Suspend actions.
- **Acceptance Criteria:** Admin can review business registration details and approve/reject merchants with notes; responsive at 360px viewport; passes `npm run lint` and `npm run build`.
- **Dependencies:** `SD-402` (backend).

### NEW-09: [P1 Quality] Configure Frontend Automated Component Testing Framework
- **Type:** Quality / Tooling | **Priority:** P1 | **Workstream:** Frontend / Quality
- **Problem Statement:** The `frontend/` package contains zero automated tests (`package.json` has no test runner or `npm test` script), leaving UI regressions and form validation bugs detectable only via manual testing.
- **Scope:** Configure Vitest, React Testing Library, and `@testing-library/jest-dom`. Add `npm run test` script to `frontend/package.json` and `.github/workflows/ci.yml`. Author baseline tests for auth context, form validation, and tracking timeline.
- **Acceptance Criteria:** `npm run test` executes in CI and passes cleanly.
- **Dependencies:** None.

### NEW-10: [P0 Governance] Synchronize ADR Statuses, Update Monorepo Documentation & Close Resolved Issues
- **Type:** Governance / Documentation | **Priority:** P0 | **Workstream:** Architecture / Docs
- **Problem Statement:** Documentation and issue tracking have drifted: `ADR 0007` is marked `Proposed` despite full implementation on `main`; `ADR 0006` is marked `Proposed` in indexes; root README says "no business feature exists yet"; 18 completed issues remain open on GitHub.
- **Scope:** Formally accept `ADR 0007` and update `docs/adr/README.md`. Update `docs/adr/README.md` and `docs/architecture.md` to reflect `ADR 0006: Accepted`. Update root `README.md` and `AGENTS.md` to reflect pricing, delivery, and tracking progress. Review and close the 18 completed issues on GitHub.
- **Acceptance Criteria:** Zero status contradictions across ADR files and indexes; monorepo documentation accurately describes system capabilities; GitHub issue board reflects actual progress.
- **Dependencies:** None.

---

## 8. Blocked Issues and Unresolved Decisions

### 8.1 Blocked Issues Dependency Analysis
Fourteen issues cannot proceed without upstream deliverables or broker infrastructure:

```
[SD-501: RabbitMQ Topology]
       │
       ├──► [SD-502: Outbox Relay]
       ├──► [SD-508: Admin DLQ Replay]
       └──► [SD-503: Ordered Consumer Support]
                 │
                 ├──► [SD-504: Notification Consumer] ──► [SD-506: Customer Inbox]
                 └──► [SD-410: Merchant Consumer]
                           │
                           └──► [SD-411: Webhook Sender] (needs SD-408, SD-409)
                                     │
                                     └──► [SD-412: Webhook UI / Redelivery]
                                     └──► [SD-701: TST-1 E2E Test]
```

- **Messaging Pipeline Blockers:**
  - `SD-502` (Outbox Relay) is blocked by `SD-501` (RabbitMQ topology declaration).
  - `SD-504` (Notification Consumer) and `SD-506` (Customer Inbox) are blocked by `SD-501` and `SD-503`.
  - `SD-410` (Merchant Consumer), `SD-411` (Webhook Sender), and `SD-412` (Webhook Redelivery) are blocked by `SD-501`, `SD-503`, `SD-408` (Webhook Registration), and `SD-409` (SSRF Client).
  - `SD-701` (TST-1 Event Integration Test) is blocked by the entire event chain (`SD-501..504`, `SD-410`).

- **Infrastructure & Submission Blockers:**
  - `SD-803` (Full-Stack Compose Profile) is blocked by `SD-801` and `SD-802` (Dockerfiles).
  - `SD-902` (ER Diagram) is blocked until migrations for `dispatch`, `merchant`, and `notification` are defined.
  - `SD-907`, `SD-908`, and `SD-910` (Submission PDF, Test Report, Demo Guide) are blocked until Waves 1 and 2 are verified.

### 8.2 Critical Unresolved Architectural Decisions

1. **Decision D-14 / ADR 0007 Formal Acceptance:**
   - *Status:* `ADR 0007` (Public SSE Tracking Stream) is marked `Proposed`, yet full backend and frontend code is merged on `main`.
   - *Action:* The team must formally sign off on `ADR 0007` and update its status to `Accepted` in `docs/adr/README.md`.

2. **SD-203 Public vs. Authenticated Price Quotes:**
   - *Status:* Contract §4.2 specifies public access (`PUB`), but merged `PriceQuoteController` requires authentication.
   - *Action:* Team decision: should unauthenticated users calculate delivery quotes on the landing page? If yes, whitelist `/api/v1/price-quotes` and `/api/v1/zones` in `SecurityConfiguration`. If no, amend contract §4.2.

3. **Decision D-27 / Demo Profile Credentials:**
   - *Status:* How demo seed accounts (`demo` profile) are configured without hardcoding secrets in Git.
   - *Action:* Use environment-injected placeholders in `.env.example` with fallback defaults active only under `@Profile("demo")`.

4. **Master Key Storage for Webhook Encryption (`ADR 0004 §7.4`):**
   - *Status:* `SD-408` requires AES-256-GCM encryption of merchant webhook secrets at rest.
   - *Action:* Agree on property name `swiftdrop.merchant.webhook.master-key` and inject via environment variable.

---

## 9. Prioritized Execution Roadmap

The refined backlog is organized into four execution phases based on strict dependency ordering.

```
┌─────────────────────────────────────────────────────────────┐
│ P0: CRITICAL STABILIZATION                                  │
│ - Fix -Pintegration (NEW-01A/B/C)   - Fix Booking Form (NEW-02)│
│ - Fix OAuth Callback (NEW-03)       - Enforce JWT Check (NEW-04)│
│ - Fix 4xx Mapping (NEW-05)          - Sync ADRs & Docs (NEW-10) │
│ - Close 18 Candidates               - Seed Pricing Data (#10)   │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ P1: CORE OPERATIONAL WORKFLOWS                              │
│ - Pricing Admin CRUD (#14/#15)      - Courier Profiles (#35)    │
│ - Parcel Assignment (#36/#37)       - Dispatch Console (#38)    │
│ - Delivery Reports (#27/#28)        - Parcel FKs (NEW-06)       │
│ - Proof Gating (NEW-07)             - Frontend Tests (NEW-09)   │
│ - Dockerfiles (#65/#66)                                     │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ P2: MAJOR MISSING CAPABILITIES                              │
│ - RabbitMQ Topology (#52)           - Outbox Relay (#53)        │
│ - Event Consumer Coord (#54)        - Notifications (#55/#57)   │
│ - Merchant Intake & Keys (#39-#45)  - Webhooks (#46-#51)        │
│ - Admin Review Console (NEW-08)                                 │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ P3: PRODUCTION READINESS & SUBMISSION                       │
│ - CI -Pintegration (#63)            - E2E Event Test (#62)      │
│ - Full-Stack Compose (#67)          - Security Audit (#64)      │
│ - Accessibility Audit (#61)         - Design Docs (#69-#75)     │
│ - Test Report & Demo Guide (#76-#78)- Final README (#68)        │
└─────────────────────────────────────────────────────────────┘
```

### Phase P0 — Critical Stabilization (Immediate Unblocking)
*Objective: Eliminate active defects, restore 100% green integration tests, enable browser booking, and reconcile governance.*

1. **NEW-01A, NEW-01B, NEW-01C:** Fix `-Pintegration` suite failures (`DeliveryQueriesIT` timestamp microsecond truncation, `DatabaseMigrationIT` `.env` isolation, `LiveTrackingIT` stream cleanup).
2. **NEW-02:** Fix customer booking weight input `min="0.001" step="0.1"` bug.
3. **NEW-03:** Add `/auth/google/callback` and `/portals` to `publicPaths` in `auth-context.tsx`.
4. **NEW-04 & NEW-05:** Enforce disabled-user check on active JWTs and fix `GlobalExceptionHandler` 4xx exception mapping.
5. **NEW-10:** Formally accept `ADR 0007`, sync `ADR 0006` in index docs, update `README.md`, and close the 18 completed issues.
6. **#10 (SD-009 Phase 1):** Provide initial `demo` profile seed for zones and default pricing rules so fresh installations can book parcels.
7. **#13 (SD-203):** Resolve public vs. authenticated pricing decision.

### Phase P1 — Core Operational Workflows
*Objective: Unblock administrative pricing management, courier profile setup, manual dispatch assignment, and establish frontend component testing.*

1. **#14 (SD-211) & #15 (SD-212):** Implement Admin Pricing REST APIs and Admin Pricing UI.
2. **#35 (SD-305):** Implement Courier Profile schema and management endpoints in `dispatch`.
3. **#36 (SD-306), #37 (SD-307) & #38 (SD-308):** Implement Unassigned Parcel queue, Courier Assignment endpoint, Courier Workload query, and Dispatcher Console UI.
4. **#27 (SD-314) & #28 (SD-315):** Implement Delivery Performance/Failure Rate reporting endpoints and Admin Reports Dashboard UI.
5. **NEW-06 & NEW-07:** Add foreign keys from `parcels` to `zones`/`pricing_rules` (`V202611031040`) and gate proof photos to `DELIVERED` parcels.
6. **NEW-09:** Configure frontend automated testing framework (Vitest / RTL) to support ongoing portal UI development.
7. **#65 (SD-801) & #66 (SD-802):** Create production multi-stage Dockerfiles for backend and frontend.

### Phase P2 — Major Missing Capabilities
*Objective: Implement the asynchronous messaging backbone, merchant order intake, signed webhooks, and customer notifications.*

1. **#52 (SD-501) & #47 (SD-409):** Declare RabbitMQ exchange/queue topology and implement SSRF-safe HTTP client.
2. **#53 (SD-502) & #54 (SD-503):** Implement transactional `OutboxRelay` publisher and boundary-safe ordered event consumer coordinator.
3. **#55 (SD-504) & #57 (SD-506):** Implement Notification RabbitMQ consumer, mock SMS store, and customer notification inbox UI.
4. **#39–#45 (SD-401..407):** Implement Merchant registration, admin approval, API keys, `X-Api-Key` filter, idempotent order intake, and Merchant Portal UI.
5. **NEW-08:** Implement Admin Merchant Review and Approval Console UI (`/admin/merchants`).
6. **#46 (SD-408), #48–#51 (SD-410..413):** Implement webhook secret encryption, event consumer, HMAC sender, redelivery UI, and demo receiver tool.
7. **#58 (SD-507):** Implement admin dead-letter queue inspection and manual replay endpoint.

### Phase P3 — Production Readiness & Submission Pack
*Objective: Execute end-to-end integration tests, enforce full containerization, audit security and accessibility, and compile academic deliverables.*

1. **#63 (SD-703):** Configure CI workflow to run `-Pintegration` and upload Surefire/Failsafe test report artifacts.
2. **#62 (SD-701):** Implement mandatory `TST-1` end-to-end event-flow integration test with Testcontainers RabbitMQ.
3. **#67 (SD-803):** Configure full-stack `--profile app` in `compose.yaml`.
4. **#64 (SD-704) & #61 (SD-603):** Execute project-wide security/PII audit and cross-portal accessibility audit (WCAG AA / 360px).
5. **#9 (SD-107):** Execute live Google OAuth round-trip verification and document evidence in `docs/architecture.md §9`.
6. **#69–#75 (SD-901..907):** Author system design documentation (`DES-1..6`) and compile submission PDF (`SUB-2`).
7. **#76–#78 (SD-908..910):** Compile test report (`SUB-3`), contribution tracking sheet (`SUB-4`), and live demo runbook (`DEM-1..6`).
8. **#68 (SD-804):** Finalize root `README.md` with verified containerized evaluation instructions.

---

## 10. Recommended Next Actions for the Project Team

To immediately align the repository and backlog, the following concrete actions are recommended for the team's next sprint planning:

1. **Administrative Backlog Cleanup (No Code Required):**
   - Review and close the **18 Close Candidate issues** (`#8`, `#12`, `#17`, `#18`, `#19`, `#20`, `#21`, `#22`, `#23`, `#24`, `#25`, `#26`, `#29`, `#30`, `#31`, `#32`, `#34`, `#60`).
   - Update issue `#16` acceptance criteria to reference Decision D-05 and close it.
   - Assign owners to the remaining active issues (currently 91.5% unassigned).
2. **Execute Phase P0 Stabilization:**
   - File the **10 new issues** (`NEW-01A` through `NEW-10`).
   - Open a targeted P0 stabilization branch fixing the 3 `-Pintegration` bugs, the customer weight `stepMismatch` attribute, the OAuth callback redirect, the JWT disabled-user check, and the `GlobalExceptionHandler` 4xx mapping.
   - Update `docs/adr/README.md` to formally mark `ADR 0006` and `ADR 0007` as `Accepted`.
3. **Parallelize Phase P1 Workstreams:**
   - **Workstream A (Pricing Admin & Seed):** Implement `SD-211` and `SD-212`, seed pricing data via `SD-009` Phase 1.
   - **Workstream B (Dispatch & Reporting):** Implement `SD-305` (courier profiles), `SD-306..308` (assignment & console), and `SD-314..315` (reports).
   - **Workstream C (Messaging Infrastructure):** Declare RabbitMQ topology (`SD-501`), implement `OutboxRelay` (`SD-502`), ordered consumer coordinator (`SD-503`).
   - **Workstream D (Frontend Quality):** Configure Vitest & React Testing Library (`NEW-09`) to support all upcoming UI pages.
