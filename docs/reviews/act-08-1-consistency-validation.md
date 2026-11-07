# ACT-08.1: Backlog Refinement Consistency Correction & Validation

- **Document ID:** ACT-08.1
- **Date:** 2026-11-04
- **Target Document Reviewed & Corrected:** `docs/reviews/act-08-github-issue-refinement-proposal.md`
- **Audited Commit / Branch:** `dev` at `681d019df6f0be88e21aed16e6e94d85a706adc1`
- **Mode:** Strictly READ-ONLY (documentation only; zero application code, migration, or GitHub issue modifications)

---

## 1. Original Inconsistencies Identified in ACT-08 Review

During review of the initial ACT-08 proposal, several mathematical and structural discrepancies were identified:

1. **Close Candidate Count Discrepancy:**
   - The text in the Executive Summary and Section 3 claimed there were **18 open close candidates**, but the list of issue IDs in Section 5 and the summary contained **19 issues** (`#8, #12, #16, #17, #18, #19, #20, #21, #22, #23, #24, #25, #26, #29, #30, #31, #32, #34, #60`).
2. **Issue #16 Status Ambiguity:**
   - Issue `#16 (SD-205)` was classified as `IMPLEMENTED — CLOSE CANDIDATE` in the main table and Section 5, yet appeared in the Executive Summary under issues needing refinement because its original pattern (`SD-YYYYMMDD-XXXXXXXX`) was superseded by Decision D-05 (Crockford Base32).
3. **Refinement Section Count Inconsistency:**
   - The Executive Summary listed 5 refinement issues (`#10, #13, #16, #37, #54, #63` — which is actually 6 issues!), while the Section 3 table reported 4, and Section 6 detailed 5 issues (`#10, #13, #37, #54, #63`), omitting `#16`.
4. **Implementation Status vs. Backlog Action Overlap:**
   - Issue `#13 (SD-203)` was classified as `PARTIALLY IMPLEMENTED` in the table, but was also discussed in the refinement section because its public endpoint requirement requires a team contract decision.
5. **Subjective Completion Metric:**
   - ACT-08 reported an audited completion of "44.8%" without providing the formula, category weights, individual issue scoring, or treatment of blocked issues.
6. **Bundled Proposed New Issues:**
   - Several proposed new issues bundled multiple unrelated responsibilities (e.g. `NEW-01` combining database migration test isolation, outbox timestamp nanosecond truncation, and SSE connection leaks; `NEW-03` combining JWT disabled-user checks with global 4xx exception mapping).
7. **CI/CD and Frontend Testing Placement:**
   - Frontend automated component testing was deferred to late Phase P2/P3 rather than being introduced early in Phase P1 to support ongoing portal development.

---

## 2. Findings from Independent Verification

### 2.1 Verification of the 19 Potential Close Candidates
Each of the 19 candidates was re-evaluated against its original GitHub acceptance criteria:
- **18 Issues with Fully Satisfied Original Criteria:**
  - `#8 (SD-004)`: JaCoCo 0.8.12 configured in `pom.xml`, XML/HTML reports generated, CI uploads artifact, `ADR 0002` updated.
  - `#12 (SD-202)`: `ZonePairFeeCalculator` implements `FeeCalculator`, formula verified, 409 mapped.
  - `#17 (SD-204)`: Flyway migrations `V202611031000..1010`, `Parcel` aggregate, Hibernate validation clean.
  - `#18 (SD-301)`: `ParcelLifecycle` state machine, failure reason check, sequence numbers.
  - `#19 (SD-302)`: `ParcelQueries`, `ParcelAssignment`, `ParcelCreation`, `ParcelEvents` in `delivery.api`.
  - `#20 (SD-303)`: `parcel_outbox_events` table (`V202611031030`), atomic outbox write in same transaction.
  - `#21 (SD-304)`: `TrackingController` public lookup (`GET /api/v1/tracked-parcels/{tn}`), zero PII, 404 handling.
  - `#22 (SD-309)`: `CourierController.today` (`GET /api/v1/courier/parcels`), business timezone filtering.
  - `#23 (SD-310)`: `CourierController.changeStatus`, unassigned 404 check, 500-char note cap, 409 illegal transitions.
  - `#24 (SD-311)`: `DeliveryProofService`, magic-byte JPEG/PNG validation <= 2MB, required before `DELIVERED` (`PUT /proof` per ADR 0006).
  - `#25 (SD-312)`: `FailureReason` enum (5 codes), check constraints, 400 when missing.
  - `#26 (SD-313)`: Mobile courier UI at `/courier` and `/courier/parcels/[id]`, lint/build clean.
  - `#29 (SD-316)`: `ParcelAuthorizationMatrixIT` (36 matrix checks passing).
  - `#30 (SD-206)`: `ParcelController.create` (`POST /api/v1/parcels`), server-side fee recalculation, atomic outbox.
  - `#31 (SD-207)`: `ParcelController.list` and `detail`, 404 isolation, status timeline.
  - `#32 (SD-208)`: `ParcelController.receipt`, immutable snapshot, 404 isolation.
  - `#34 (SD-210)`: Customer history list, detail view, print receipt CSS (`@media print`), lint/build clean.
  - `#60 (SD-510)`: Public tracking UI at `/track` (`/track?code=...`), live `EventSource` updates, lint/build clean.
- **The Case of Issue #16 (SD-205):**
  - `TrackingNumberGenerator` is 100% implemented, unit-tested, and integrated into `ParcelBookingService` with bounded retry.
  - However, the original issue description demanded format `SD-YYYYMMDD-XXXXXXXX`. Decision D-05 (incorporated into accepted `ADR 0006`) formally chose `'SD'` + 10 Crockford Base32 characters (`^SD[0-9A-HJKMNP-TV-Z]{10}$`).
  - Because its original acceptance criteria do not match the merged code until the issue description is formally aligned with Decision D-05, `#16` is rigorously classified under **NEEDS REFINEMENT (Pre-Closure AC Alignment)**.

### 2.2 Verification of Partially Implemented vs. Needs Refinement
- **Partially Implemented (4 Issues):**
  - `#13 (SD-203)`: `PriceQuoteController` exists, but returns 401 when unauthenticated, failing the public endpoint requirement.
  - `#33 (SD-209)`: `/customer/new` UI exists, but native HTML5 `min="0.001" step="0.1"` on `#weight` triggers `stepMismatch` and blocks form submission.
  - `#56 (SD-505)`: `ParcelEvents.eventsBetween` exists, but `DeliveryQueriesIT` fails due to `Instant` nanosecond vs PostgreSQL microsecond precision drift.
  - `#59 (SD-508)`: Live tracking SSE exists, but uses path `/events` and 25s heartbeat, `LiveTrackingIT` leaks an open stream hanging Tomcat shutdown, and `ADR 0007` is still `Proposed`.
- **Needs Refinement (5 Issues):**
  - `#10 (SD-009)`: Monolithic seed requirement blocks basic zone/pricing seed data; needs splitting into Phase 1 / Phase 2.
  - `#16 (SD-205)`: Format requirement (`SD-YYYYMMDD-XXXXXXXX`) must be updated to Decision D-05 (Crockford Base32) before closure.
  - `#37 (SD-307)`: Issue text specifies `findActiveCountsByCourier()`; actual contract method is `countActiveParcelsByCourier()`.
  - `#54 (SD-503)`: Direct import of `delivery.api` into `shared.messaging` would violate `ModuleBoundaryTest:81`; requires callback adapter design.
  - `#63 (SD-703)`: JaCoCo upload is already done in `#8`; scope must focus on Surefire/Failsafe XML archiving and executing `-Pintegration` in CI.

---

## 3. Corrections Made to ACT-08 Report

1. **Reconciled Classification Arithmetic (Exact Sum = 71):**
   - Corrected the close candidate count to exactly **18 issues**.
   - Placed `#16` into **NEEDS REFINEMENT** with a clear explanation of its D-05 implementation status and pre-closure AC alignment requirement.
   - Updated all tables and summary text so that:
     `18 (Close Candidates) + 1 (Closed) + 4 (Partially Implemented) + 5 (Needs Refinement) + 29 (Still Valid) + 14 (Blocked) = 71 Total`.
2. **Removed Unsupported Completion Percentage:**
   - Removed the 44.8% figure.
   - Replaced it with Section 3.3 containing verifiable implementation statistics: Backlog Unit completion (26.8% closed/ready), Modular Monolith depth by module, and End-to-End Business Flow operational readiness.
3. **Decoupled and Refined Proposed New Issues:**
   - Replaced the 7 bundled issues with **10 single-responsibility issues** (`NEW-01A` through `NEW-10`), separating test suite bug fixes, frontend form validation, auth context routing, security filters, database migrations, and documentation synchronization.
4. **Early Introduction of Frontend Testing:**
   - Moved `NEW-09` (Frontend Automated Component Testing) from late Phase P2 into **Phase P1** so that Vitest and React Testing Library are available during dispatch, merchant, and admin UI development.
5. **Clear Separation of Backlog Administration from Code/Doc Changes:**
   - Separated the administrative act of closing resolved issues on GitHub from the Git commit updating ADRs and READMEs.

---

## 4. Final Classification Counts (Reconciled Backlog)

Across all **71 issues (`#8` through `#78`)**:

| Classification | Count | Issue Numbers |
|---|---:|---|
| **IMPLEMENTED — CLOSE CANDIDATE** | **18** | `#8`, `#12`, `#17`, `#18`, `#19`, `#20`, `#21`, `#22`, `#23`, `#24`, `#25`, `#26`, `#29`, `#30`, `#31`, `#32`, `#34`, `#60` |
| **IMPLEMENTED (ALREADY CLOSED)** | **1** | `#11` (`SD-201`) |
| **PARTIALLY IMPLEMENTED** | **4** | `#13`, `#33`, `#56`, `#59` |
| **NEEDS REFINEMENT** | **5** | `#10`, `#16`, `#37`, `#54`, `#63` |
| **STILL VALID** | **29** | `#9`, `#14`, `#15`, `#27`, `#28`, `#35`, `#36`, `#38`, `#39`, `#40`, `#41`, `#42`, `#43`, `#44`, `#45`, `#46`, `#47`, `#51`, `#52`, `#61`, `#64`, `#65`, `#66`, `#68`, `#69`, `#71`, `#73`, `#74`, `#77` |
| **BLOCKED** | **14** | `#48`, `#49`, `#50`, `#53`, `#55`, `#57`, `#58`, `#62`, `#67`, `#70`, `#72`, `#75`, `#76`, `#78` |
| **DUPLICATE / OVERLAPPING** | **0** | *(Scope overlap between `#13` and `#14` on `GET /api/v1/zones` documented)* |
| **OUTDATED** | **0** | *(Stale notes on 9 issues superseded by PR `#81`/`#85`)* |
| **TOTAL** | **71** | **Sum matches total backlog range `#8`–`#78` exactly.** |

---

## 5. Final Proposed New Issue Count

Exactly **10 decoupled new issues** are proposed:

| Issue ID | Type | Priority | Workstream | Summary |
|---|---|---|---|---|
| **NEW-01A** | Bug | P0 | Delivery / Messaging | Truncate outbox event timestamps to microseconds in `ParcelEventRecorder` to fix `DeliveryQueriesIT`. |
| **NEW-01B** | Bug | P0 | Identity / Testing | Isolate `DatabaseMigrationIT` from host `.env` bootstrap credentials. |
| **NEW-01C** | Bug | P0 | Tracking / Testing | Close HTTP SSE stream in `LiveTrackingIT` to eliminate 30s Tomcat shutdown hang. |
| **NEW-02** | Bug | P0 | Frontend (Customer) | Fix customer booking form weight input step (`min="0.001" step="0.1"` causing `stepMismatch`). |
| **NEW-03** | Bug | P0 | Frontend (Auth) | Add `/auth/google/callback` and `/portals` to `publicPaths` in `auth-context.tsx`. |
| **NEW-04** | Security | P0 | Identity / Security | Validate user enabled status on active JWT requests (`SEC-01`). |
| **NEW-05** | Bug | P0 | Shared Web | Map Spring MVC 4xx exceptions to RFC 9457 Problem Details in `GlobalExceptionHandler` (`SEC-02`). |
| **NEW-06** | Database | P1 | Delivery / Database | Add deferred foreign keys from `parcels` to `zones` and `pricing_rules` (`V202611031040`). |
| **NEW-07** | Security | P1 | Delivery | Restrict proof photo endpoint access to `DELIVERED` parcels (`SEC-05`). |
| **NEW-08** | Feature | P1 | Frontend (Admin) | Implement Admin Overview Dashboard (`/admin`) and Admin Merchant Review UI (`/admin/merchants`). |
| **NEW-09** | Quality | P1 | Frontend / Quality | Configure frontend automated component testing framework (Vitest & RTL) early in Phase P1. |
| **NEW-10** | Governance | P0 | Architecture / Docs | Synchronize ADR 0006/0007 acceptance in index docs, update monorepo READMEs, and close 18 resolved issues. |

---

## 6. Completion Metric Decision & Rationale

**Decision:** The asserted "44.8%" project completion percentage was **removed**.

**Rationale:**
1. A single percentage oversimplifies a multi-tiered modular monolith project. Weighting backlog issues equally ignores variance in complexity (e.g. creating a Dockerfile vs. implementing the entire transactional outbox and event consumer pipeline).
2. Weighting by code artifacts (e.g. lines of code or classes) is distorted by boilerplate and generated files.
3. Crucially, a project can have high code completion in core modules while having 0% operational readiness in end-to-end user flows due to single-line defects (such as empty seed tables or broken form validation step attributes).
4. Replacing the percentage with three distinct, measurable tables (Backlog Units, Module Implementation Depth, and End-to-End Business Flow Readiness) provides an objective and actionable assessment for sprint planning.

---

## 7. Remaining Uncertainties Requiring Team Approval

The following four items cannot be resolved by an automated audit and require formal human team review:

1. **Decision D-14 / ADR 0007 Formal Acceptance:**
   - `ADR 0007` (Public SSE Tracking Stream) must be formally signed off by the team and marked `Accepted` in `docs/adr/README.md`.
2. **SD-203 Public vs. Authenticated Quote Endpoint:**
   - Contract §4.2 specifies public access (`PUB`), but `PriceQuoteController` requires authentication. The team must decide whether landing-page users can estimate delivery fees anonymously.
3. **Decision D-27 / Demo Seed Credentials:**
   - Agree on non-secret defaults for `spring.profiles.active=demo` via `.env.example` placeholders to unblock evaluator container startup.
4. **Master Key Storage for Webhook Encryption (`ADR 0004 §7.4`):**
   - Agree on the configuration property name (`swiftdrop.merchant.webhook.master-key`) for AES-256-GCM webhook secret encryption.

---

## 8. Final Readiness Recommendation

With the corrections applied in `act-08-github-issue-refinement-proposal.md` and validated in this report:
- The backlog arithmetic is 100% consistent across all sections (71 total issues).
- The 18 verified close candidates can be immediately closed on GitHub to clear backlog clutter.
- The 10 proposed new issues provide a clear, decoupled defect-repair and stabilization backlog for Phase P0.
- The team has an accurate, prioritized roadmap to complete the remaining Dispatch, Merchant, Messaging, and Submission milestones.

