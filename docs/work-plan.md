# SwiftDrop work plan and issue backlog

> **Status: Proposed — for team review. No GitHub issues, milestones or labels exist yet.**
> Contracts referenced here (`§n`, `D-nn`) are in [implementation-contract.md](implementation-contract.md).
> Issues that depend on an unapproved decision are marked **Blocked (D-nn)**. No deadlines are set
> because the submission date has not been provided.

Legend — Priority: **P0** foundation/blocks others, **P1** required feature, **P2** required
polish or hardening, **P3** optional. Size: **S** ≤ 1 day, **M** 2–3 days, **L** 4–5 days (one
reviewer can still review it in one sitting; anything bigger was split).

## 1. Team allocation (Members 1–10 are placeholders)

The ten proposed workstreams were kept, with three rebalancing changes found during inspection:

- **Identity is mostly done**, so Member 1 also owns the shared foundations (`shared` error
  format, integration-test setup, frontend app shell, migration verification).
- **Merchant work was too large for one person**: Member 4 owns registration, approval, dashboard
  and webhook *endpoints*; Member 5 owns API keys, order intake and webhook *delivery*.
- **Testing/quality cross-cutting tasks** (coverage, security review, test report, contribution
  sheet) go to Member 7, whose dispatch module is the smallest.

| # | Workstream | Owner | Owns (modules / packages / files) | Collaborates with |
|---|---|---|---|---|
| WS1 | Identity, access control, shared foundations | Member 1 | `identity`, `config`, `shared`, `frontend/lib/api`, `frontend/app/layout.tsx`, auth pages shell | everyone (reviews `config`/`shared`) |
| WS2 | Customer booking and pricing integration | Member 2 | `delivery.web.customer` package, `frontend/app/(customer)` | M3 (delivery owner), M8 (pricing api) |
| WS3 | Delivery lifecycle, history, public lookup | Member 3 | `delivery` module owner: domain, state machine, history, outbox write, `delivery.api` | M2, M6, M7, M8, M9 |
| WS4 | Merchant registration, approval, dashboard, webhook endpoints | Member 4 | `merchant` module owner: `merchants`, `webhook_endpoints`, dashboard, `frontend/app/(merchant)` | M5, M1 (identity api), M3 |
| WS5 | Merchant API keys, order intake, webhook delivery | Member 5 | `merchant` packages `apikey`, `orders`, `webhook.delivery`; demo receiver `tools/webhook-receiver` | M4 (module owner), M1 (`config`), M9 |
| WS6 | Courier operations and delivery proof | Member 6 | `delivery.web.courier`, `delivery_proofs`, `frontend/app/(courier)` | M3 |
| WS7 | Dispatcher assignment, workload; quality lead | Member 7 | `dispatch` module, `frontend/app/(dispatch)`, coverage config, CI coverage step | M3, M1 |
| WS8 | Pricing administration and reports | Member 8 | `pricing` module owner, `delivery.application.report` package, `frontend/app/(admin)/pricing`, `(admin)/reports` | M2, M3 |
| WS9 | RabbitMQ events and notifications | Member 9 | `notification` module, `delivery.infrastructure.outbox` relay, `shared.messaging` | M3, M5 |
| WS10 | SSE live tracking, containers, end-to-end demo | Member 10 | `tracking` module, `frontend/app/track`, Dockerfiles, `compose.yaml` app profile, demo script | everyone |

Shared files with a single owner: `SecurityConfiguration` (M1), `application.properties` (M1;
others add keys in their own section via PR reviewed by M1), `.env.example` (M1), `compose.yaml`
(M10), `.github/workflows/ci.yml` (M7), `docs/architecture.md` (M1 merges status updates),
`frontend/app/layout.tsx` and `frontend/lib/api/*` (M1).

### Workstream details

**WS1 — Member 1.** Deliverables: SD-002, 003, 005, 006, 007, 008, 101, 102, 103, 104, 106, 107.
Tests: identity unit + MockMvc, authorization matrix harness, integration profile smoke test.
Done when: staff accounts and admin user management work; `identity.api.UserDirectory` is
available; Google handoff works end to end in a browser; every module can add authorization-matrix
rows; error format and pagination are shared.

**WS2 — Member 2.** SD-105, 206, 207, 208, 209, 210, 901. Tests: MockMvc for parcel create/list/
detail/receipt incl. ownership; fee recalculated server-side. Done when a customer can sign up,
quote, book, see history and print a receipt in the UI.

**WS3 — Member 3.** SD-204, 205, 301, 302, 303, 304, 505, 902. Tests: state-machine unit tests
(every legal/illegal transition), sequence uniqueness, outbox row in same transaction, public view
leaks no PII. Done when `delivery.api` contracts in §8.1 are implemented and reviewed by consumers.

**WS4 — Member 4.** SD-401, 402, 406, 407, 408, 412, 907. Tests: registration, approval state
machine, merchant isolation (404), secret encryption failures (ADR 0004 §11). Done when an admin
approves a merchant who then sees their dashboard and registers a webhook endpoint.

**WS5 — Member 5.** SD-403, 404, 405, 409, 410, 411, 413, 905. Tests: key hashing and lookup,
idempotent orders, SSRF suite, retry/backoff, signature vectors (ADR 0004 §11). Done when DEM-2
works: an API order produces a signed webhook at the demo receiver.

**WS6 — Member 6.** SD-309, 310, 311, 312, 313, 316. Tests: courier-only and assigned-only
access, proof required for `DELIVERED`, failure reasons, upload limits. Done when DEM-3's courier
part works on a phone-sized screen.

**WS7 — Member 7.** SD-004, 305, 306, 307, 308, 703, 704, 903, 908, 909. Tests: eligibility,
assignment only in `CREATED`, workload counts. Done when DEM-3's dispatcher part works and coverage
is reported in CI.

**WS8 — Member 8.** SD-201, 202, 203, 211, 212, 314, 315, 906. Tests: fee formula (Strategy) with
boundary weights and rounding, admin-only writes, report definitions with a fixed clock/time zone.
Done when admins manage zones/rules and view both reports.

**WS9 — Member 9.** SD-501, 502, 503, 504, 506, 507, 701, 904. Tests: ADR 0004 §11 relay, ordering,
consumer and notification tests; TST-1 integration test. Done when DEM-4 works and the event-flow
integration test passes.

**WS10 — Member 10.** SD-009, 508, 510, 603, 801, 802, 803, 804, 910. Tests: SSE snapshot on
connect, push after commit, no PII in stream; `docker compose --profile app up` smoke check. Done
when DEM-1's live tracking works from containers and the demo script is rehearsed.

## 2. Backlog

### Epic E0 — Shared contracts and development readiness

#### SD-001 · Review and approve the implementation contract and decisions
- **Area:** team · **Owner:** all (facilitator Member 1) · **P0 · S** · **Depends on:** —
- **Objective:** turn the Proposed items in the contract into approved decisions so parallel work can start.
- **In scope:** walk through `D-01`…`D-32`; record each outcome; open ADR PRs for those marked "ADR?".
- **Out of scope:** implementation.
- **Acceptance criteria:** 1. Every `D-nn` has an outcome (approved / changed / deferred) recorded in the contract. 2. ADR drafts opened for D-01, D-02, D-03, D-06, D-11, D-14 (and D-21 if option b). 3. `architecture.md` §14 updated to match. 4. Member names replace placeholders in §1.
- **Tests:** n/a. **Docs:** contract status → Accepted (or revised), ADR index.

#### SD-002 · Project-wide problem-details error handler and pagination types
- **Area:** shared · **Owner:** Member 1 · **P0 · S** · **Depends on:** SD-001 (D-15)
- **Objective:** one error format and page shape for all modules (IMP-3).
- **In scope:** `shared.web` `@RestControllerAdvice` for validation, unreadable body, `@Version` conflicts, access denied, generic 500; `PageResponse<T>` record; page-size limit.
- **Out of scope:** module-specific exceptions (they stay in modules); changing identity responses.
- **Contract:** §3.3, §3.4.
- **Acceptance criteria:** 1. Validation errors return 400 with sorted `invalidFields` and no values. 2. Unknown exceptions return 500 `"Unexpected error"` without stack trace or message. 3. Optimistic-lock failure returns 409. 4. `size > 100` is clamped or rejected (decide, document). 5. Existing identity tests pass unchanged. 6. `ModuleBoundaryTest` passes (`shared` imports no module).
- **Tests:** MockMvc test with a fixture controller for each case. **Docs:** architecture §5.

#### SD-003 · Integration-test profile with real PostgreSQL and RabbitMQ
- **Area:** build/test · **Owner:** Member 1 · **P0 · M** · **Blocked (D-10)**
- **Objective:** allow persistence/messaging tests (TST-1) while `clean verify` stays Docker-free.
- **In scope:** add Testcontainers (Boot-managed) test dependencies; `-Pintegration` Failsafe profile running `*IT` classes; PostgreSQL 18 and RabbitMQ 4.3 containers matching `compose.yaml`; one smoke IT; CI job (Docker available on GitHub runners).
- **Out of scope:** module-specific ITs.
- **Acceptance criteria:** 1. `mvnw clean verify` runs no `*IT` and needs no Docker. 2. `mvnw -Pintegration verify` starts containers and runs ITs. 3. Smoke IT applies all migrations and Hibernate `validate` succeeds. 4. ADR 0002 and development-setup updated with the dependency and command. 5. CI runs the integration job.
- **Tests:** the smoke IT. **Docs:** ADR 0002 (dependency note), development-setup, CONTRIBUTING checks table.

#### SD-004 · Measurable code coverage
- **Area:** build/test · **Owner:** Member 7 · **P1 · S** · **Blocked (D-11)**
- **Acceptance criteria:** 1. Coverage report (HTML + XML) generated by `verify` (unit) and the integration profile (merged or separate, documented). 2. CI uploads it as an artifact. 3. ADR 0002 amended for the pinned plugin version. 4. Agreed threshold (if any) documented, not enforced until agreed.
- **Tests:** n/a. **Docs:** ADR 0002, CONTRIBUTING, architecture §12.

#### SD-005 · Verify identity migrations and Flyway module folders against PostgreSQL
- **Area:** identity/db · **Owner:** Member 1 · **P0 · S** · **Depends on:** SD-003 (or a documented manual run)
- **Objective:** close the outstanding verification: migrations have never run on a real database.
- **Acceptance criteria:** 1. Flyway discovers `db/migration/identity/*` (both migrations) — confirms `spring.flyway.locations` covers sub-folders or adds it. 2. Hibernate `validate` passes for `users`, `user_roles`, `external_identities`. 3. An IT registers a user and links a Google identity through the real repositories. 4. architecture §6 updated ("applied and validated").
- **Tests:** IT in the integration profile. **Docs:** architecture §6.

#### SD-006 · Confirm branch workflow and CI triggers
- **Area:** process · **Owner:** Member 1 · **P0 · S** · **Depends on:** SD-001 (D-26)
- **Acceptance criteria:** 1. Branch base (`main` or `dev`) decided and written in CONTRIBUTING (remove "proposed defaults" note). 2. CI triggers cover the integration branch. 3. Branch protection configured by a repo admin as CONTRIBUTING describes. 4. The unpushed `dev` commit is pushed or rebased by its author (with authorization).
- **Docs:** CONTRIBUTING.

#### SD-007 · Correct stale statements in architecture.md
- **Area:** docs · **Owner:** Member 1 · **P1 · S** · **Depends on:** —
- **Acceptance criteria:** 1. §12 reflects that CI exists. 2. §2 messaging row no longer says "identity tables only". 3. §1/§4 mention Google sign-in. 4. No other content changed.
- **Docs:** architecture.

#### SD-008 · Frontend app shell: API client, auth state, role-aware layout
- **Area:** frontend · **Owner:** Member 1 · **P0 · M** · **Blocked (D-01)**
- **In scope:** `lib/api` typed fetch client (base URL from env, bearer header, problem-details parsing); auth context (login, logout, token expiry, `GET /users/me`); route groups `(customer)`, `(merchant)`, `(courier)`, `(dispatch)`, `(admin)`, `track`; role-based navigation (UX only); shared UI primitives from shadcn/ui.
- **Out of scope:** feature pages.
- **Acceptance criteria:** 1. `NEXT_PUBLIC_API_BASE_URL` configurable, documented in `frontend/README.md`. 2. 401 clears the session and redirects to login. 3. Problem-details errors are shown as user-friendly messages without raw bodies. 4. Route groups exist with placeholder pages so workstreams do not edit the layout. 5. `npm run lint` and `npm run build` pass.
- **Tests:** lint/build; manual check documented. **Docs:** frontend/README.

#### SD-009 · Demo and seed data
- **Area:** ops · **Owner:** Member 10 · **P1 · S** · **Blocked (D-27)** · **Depends on:** SD-101, SD-201, SD-305, SD-401
- **Acceptance criteria:** 1. A `demo` profile seeds zones, pricing rules, one admin, two couriers, one approved merchant, idempotently. 2. No seed data in Flyway migrations. 3. Demo credentials come from env, not code. 4. Documented in development-setup.

### Epic E1 — Identity and access control

#### SD-101 · Bootstrap the first Dispatcher/Admin account
- **Area:** identity · **Owner:** Member 1 · **P0 · S** · **Blocked (D-04)**
- **Acceptance criteria:** 1. With both bootstrap env vars set and no admin present, startup creates one `DISPATCHER_ADMIN`. 2. If an admin exists, nothing changes (idempotent, also on restart). 3. Partial config fails startup with a clear message; password rules as registration. 4. Password never logged. 5. `.env.example` placeholders added.
- **Tests:** unit tests for the three cases. **Docs:** development-setup, architecture §9.

#### SD-102 · Admin user management (list, create staff, enable/disable)
- **Area:** identity · **Owner:** Member 1 · **P1 · M** · **Depends on:** SD-002, SD-101
- **Contract:** `GET /api/v1/users`, `POST /api/v1/staff-users`, `PATCH /api/v1/users/{id}` (§4.2).
- **Acceptance criteria:** 1. Only `DISPATCHER_ADMIN` can call these (others 403, anonymous 401). 2. Staff creation allows only `COURIER` or `DISPATCHER_ADMIN`; `MERCHANT`/`CUSTOMER` rejected. 3. Disabled users cannot log in and their existing tokens are rejected on `/users/me` (enabled check). 4. An admin cannot disable themself. 5. List is paged and never returns password hashes.
- **Tests:** MockMvc incl. role matrix. **Docs:** architecture §9.

#### SD-103 · `identity.api.UserDirectory` for other modules
- **Area:** identity api · **Owner:** Member 1 · **P0 · S** · **Depends on:** —
- **Contract:** §8.1.
- **Acceptance criteria:** 1. `findUser` returns `UserSummary` record (no password hash). 2. `createMerchantUser` creates a `MERCHANT` user with password rules and duplicate handling as registration. 3. Only `api` types are exposed; boundary test passes. 4. Reviewed by Members 4, 7, 9.
- **Tests:** unit tests.

#### SD-104 · Google sign-in handoff to the frontend
- **Area:** identity · **Owner:** Member 1 · **P1 · M** · **Blocked (D-02)** · **Depends on:** SD-008
- **Acceptance criteria:** 1. Callback no longer returns the JWT in the page; it redirects to the configured frontend URL with a one-time code. 2. Code is hashed at rest, single use, expires in 60 s. 3. `POST /api/v1/auth/google/exchanges` returns the standard token body; reused/expired codes return the same 401. 4. Rejections (409/403) redirect with an error code, not a code. 5. Existing Google tests updated; frontend callback page stores the token.
- **Tests:** MockMvc + existing OIDC test harness. **Docs:** architecture §9, development-setup.

#### SD-105 · Frontend sign-up, login and "Sign in with Google"
- **Area:** frontend · **Owner:** Member 2 · **P1 · M** · **Depends on:** SD-008 (SD-104 for Google button)
- **Acceptance criteria:** 1. Sign-up and login forms with client-side hints; server errors shown from problem details. 2. Google button shown only when the backend has Google configured (config endpoint or env flag). 3. 409 "account exists" message explained. 4. Responsive at 360 px width. 5. lint/build pass.
- **Tests:** lint/build; manual checklist in PR.

#### SD-106 · Authorization test matrix harness (TST-2)
- **Area:** test · **Owner:** Member 1 · **P0 · S** · **Depends on:** SD-002
- **Objective:** one reusable way to assert role × endpoint × ownership outcomes.
- **Acceptance criteria:** 1. Helper to mint test JWTs per role/user and API keys per merchant. 2. Table-driven test base where each module adds rows (method, path, principal, expected status). 3. Example rows for identity endpoints. 4. Documented in CONTRIBUTING (every endpoint PR adds rows).
- **Tests:** itself.

#### SD-107 · Real Google round-trip verification
- **Area:** identity · **Owner:** Member 1 · **P2 · S** · **Depends on:** SD-104
- **Acceptance criteria:** 1. A team Google OAuth client exists (credentials only in members' `.env`). 2. First sign-in creates a customer; second resolves to the same account; existing-email case shows the 409 message. 3. Result recorded in architecture §9 ("verified with real Google on <date>").

### Epic E2 — Pricing and customer delivery creation

#### SD-201 · Pricing schema: zones and pricing rules
- **Area:** pricing · **Owner:** Member 8 · **P0 · M** · **Blocked (D-07, D-24)**
- **Contract:** §5.2.
- **Acceptance criteria:** 1. Migration `V…__pricing_create_zones_and_pricing_rules.sql` with constraints in §5.2. 2. Entities and repositories in `pricing.domain`; `ddl-auto=validate` compatible. 3. Unique zone-pair rule; amounts ≥ 0. 4. `MigrationConventionTest` passes.
- **Tests:** entity/validation unit tests; IT in the integration profile once SD-003 exists.

#### SD-202 · `pricing.api.FeeCalculator` (Strategy)
- **Area:** pricing · **Owner:** Member 8 · **P0 · M** · **Depends on:** SD-201
- **Acceptance criteria:** 1. Formula in §5.2, `BigDecimal`, `HALF_UP`, 2 decimals. 2. Weight rounding up to whole kg (1 g → 1 kg; 1000 g → 1 kg; 1001 g → 2 kg). 3. Unknown/inactive zone or missing rule → domain exception mapped to 409. 4. Pricing strategy is an interface with one implementation (DES-6) — no extra abstractions. 5. Reviewed by Member 3.
- **Tests:** parameterized unit tests for boundaries and each size.

#### SD-203 · Public price-quote endpoint
- **Area:** pricing · **Owner:** Member 8 · **P1 · S** · **Depends on:** SD-202, SD-002
- **Acceptance criteria:** 1. `POST /api/v1/price-quotes` per §4.3, public. 2. Validation of size/weight/zones → 400. 3. Response includes breakdown and currency. 4. Authorization rows added.
- **Tests:** MockMvc.

#### SD-204 · Parcel aggregate and schema
- **Area:** delivery · **Owner:** Member 3 · **P0 · L** · **Blocked (D-23, D-24)** · **Depends on:** SD-205
- **Contract:** §5.3 `parcels`, `parcel_status_history`.
- **Acceptance criteria:** 1. Migration for `parcels` and `parcel_status_history` with all constraints. 2. `Parcel` entity with embedded addresses, `@Version`, owner check (customer xor merchant). 3. `delivery.api` enums `ParcelStatus`, `ParcelSize`, `ParcelSource`. 4. FKs to `users` and `zones` only; no FK to `merchants`. 5. PII fields excluded from `toString`.
- **Tests:** unit tests for invariants; IT for constraints (integration profile).

#### SD-205 · Tracking-number generator
- **Area:** delivery · **Owner:** Member 3 · **P0 · S** · **Blocked (D-05)**
- **Acceptance criteria:** 1. Format per D-05 using `SecureRandom`. 2. Collision retry (bounded) relying on the unique constraint. 3. Case-insensitive lookup normalizes input.
- **Tests:** unit tests for format, alphabet, retry.

#### SD-206 · Customer creates a delivery request
- **Area:** delivery (customer web) · **Owner:** Member 2 · **P1 · M** · **Depends on:** SD-202, SD-204, SD-301 (initial `CREATED` history), SD-002
- **Contract:** `POST /api/v1/parcels` §4.3; `delivery.api.ParcelCreation` §8.1 (shared with SD-405).
- **Acceptance criteria:** 1. Only `CUSTOMER` may call it; owner = caller. 2. Fee calculated by `FeeCalculator`; client fee fields ignored. 3. Status `CREATED`, sequence 1, history row and outbox row written in one transaction. 4. 201 with `Location`. 5. Validation errors list field paths only. 6. `ParcelCreation` api reviewed by Members 3 and 5.
- **Tests:** MockMvc (roles, validation, fee ignored), unit test for service.

#### SD-207 · Customer delivery history and detail
- **Area:** delivery (customer web) · **Owner:** Member 2 · **P1 · M** · **Depends on:** SD-206
- **Acceptance criteria:** 1. `GET /api/v1/parcels` returns only the caller's parcels for customers, paged, newest first. 2. Admin sees all with status filter. 3. Detail includes status history (status, time, failure reason code) but not other users' data. 4. Another customer's parcel → 404.
- **Tests:** MockMvc ownership rows.

#### SD-208 · Delivery receipt
- **Area:** delivery (customer web) · **Owner:** Member 2 · **P1 · S** · **Blocked (D-13)** · **Depends on:** SD-207
- **Acceptance criteria:** 1. Receipt endpoint returns tracking number, dates, addresses, size, weight, fee, status for owner/admin. 2. Printable page in the UI with print styles. 3. Non-owner → 404.
- **Tests:** MockMvc.

#### SD-209 · Frontend booking flow with fee estimate
- **Area:** frontend · **Owner:** Member 2 · **P1 · M** · **Depends on:** SD-008, SD-203, SD-206
- **Acceptance criteria:** 1. Form for pickup/drop-off (zone select from `/zones`), size, weight. 2. "Get estimate" shows the quote before "Confirm". 3. Confirmation shows tracking number and link to tracking page. 4. Mobile-friendly. 5. lint/build pass.

#### SD-210 · Frontend history and receipt pages
- **Area:** frontend · **Owner:** Member 2 · **P1 · S** · **Depends on:** SD-207, SD-208, SD-008
- **Acceptance criteria:** 1. Paged history list with status badges. 2. Detail with timeline. 3. Receipt print/save as PDF via browser.

#### SD-211 · Pricing administration API (ADM-3)
- **Area:** pricing · **Owner:** Member 8 · **P1 · M** · **Depends on:** SD-201, SD-002
- **Acceptance criteria:** 1. Admin-only create/update/deactivate zones and rules; public read of active zones. 2. Duplicate code or zone pair → 409. 3. `@Version` conflicts → 409. 4. Rule changes do not change existing parcel fees (verified with a test using SD-206 data or a unit test on snapshot semantics).
- **Tests:** MockMvc incl. role matrix.

#### SD-212 · Pricing administration UI
- **Area:** frontend · **Owner:** Member 8 · **P2 · M** · **Depends on:** SD-211, SD-008
- **Acceptance criteria:** 1. Lists and forms for zones and rules. 2. Validation messages from problem details. 3. lint/build pass.

### Epic E3 — Parcel lifecycle, dispatch and courier operations

#### SD-301 · Status state machine, history and sequence
- **Area:** delivery · **Owner:** Member 3 · **P0 · M** · **Blocked (D-06, D-19)** · **Depends on:** SD-204
- **Acceptance criteria:** 1. Transition table §7 enforced in the domain; illegal transitions → 409. 2. `FAILED` requires a reason code; `DELIVERED` requires proof (hook for SD-311). 3. Each change increments `last_sequence`, writes history with actor, event ID and sequence, under a lock. 4. In-process `ParcelStatusChanged` published **after commit** (`@TransactionalEventListener`-ready). 5. Concurrent updates cannot produce duplicate sequences (unique constraint + version).
- **Tests:** unit tests for every pair of statuses; concurrency test in IT.

#### SD-302 · `delivery.api` assignment and query contracts
- **Area:** delivery api · **Owner:** Member 3 · **P0 · M** · **Depends on:** SD-301
- **Contract:** `ParcelAssignment`, `ParcelQueries` §8.1.
- **Acceptance criteria:** 1. `assign` allowed only in `CREATED`; reassignment replaces the courier and is recorded. 2. `unassigned`, `activeCountsByCourier`, `forMerchant`, `byMerchantReference` implemented with indexes. 3. Summaries contain no PII unless the method's consumer needs it (documented per method). 4. Reviewed by Members 5 and 7.
- **Tests:** unit tests; IT for queries.

#### SD-303 · Outbox row written with every status change
- **Area:** delivery · **Owner:** Member 3 · **P0 · S** · **Depends on:** SD-301
- **Contract:** ADR 0004 §2–3, §5.3 `parcel_outbox_events`.
- **Acceptance criteria:** 1. Migration for the outbox table. 2. Event JSON per §4.3 webhook example, schemaVersion 1, no PII. 3. Written in the same transaction as the history row; rollback leaves no row. 4. Event can be rebuilt identically from history (same ID, data).
- **Tests:** unit test for serialization; rollback test (IT).

#### SD-304 · Public tracking lookup
- **Area:** delivery · **Owner:** Member 3 · **P1 · S** · **Blocked (D-16)** · **Depends on:** SD-301, SD-205
- **Acceptance criteria:** 1. `GET /api/v1/tracked-parcels/{n}` public, returns only §4.3 fields. 2. Unknown number → 404 with constant-time-ish behavior (no enumeration hints). 3. A test asserts names, phones and address lines are absent. 4. Basic rate limiting is **out of scope** (note as risk).
- **Tests:** MockMvc.

#### SD-305 · Courier profiles (dispatch)
- **Area:** dispatch · **Owner:** Member 7 · **P1 · M** · **Blocked (D-28)** · **Depends on:** SD-103, SD-102
- **Acceptance criteria:** 1. Migration `couriers` §5.4. 2. Admin CRUD/activate/deactivate; creating a profile requires an existing enabled user with `COURIER` role (via `UserDirectory`). 3. Duplicate profile → 409.
- **Tests:** MockMvc + unit.

#### SD-306 · Unassigned parcels and assignment
- **Area:** dispatch · **Owner:** Member 7 · **P1 · M** · **Depends on:** SD-302, SD-305
- **Acceptance criteria:** 1. `GET /api/v1/unassigned-parcels` admin-only, paged. 2. `POST /api/v1/parcel-assignments` checks eligibility (active profile, enabled, `COURIER`), then `ParcelAssignment.assign`. 3. Ineligible courier → 409; parcel not `CREATED` → 409. 4. No dispatch code touches delivery tables.
- **Tests:** unit with mocked `delivery.api`; MockMvc.

#### SD-307 · Courier workload view
- **Area:** dispatch · **Owner:** Member 7 · **P1 · S** · **Depends on:** SD-302, SD-305
- **Acceptance criteria:** 1. Each active courier with count of non-terminal assigned parcels, including zero. 2. Admin-only.
- **Tests:** unit + MockMvc.

#### SD-308 · Dispatcher UI
- **Area:** frontend · **Owner:** Member 7 · **P1 · M** · **Depends on:** SD-306, SD-307, SD-008
- **Acceptance criteria:** 1. Unassigned list with assign action and courier picker showing workload. 2. Courier profile management screens. 3. lint/build pass.

#### SD-309 · Courier "deliveries for the day"
- **Area:** delivery (courier web) · **Owner:** Member 6 · **P1 · S** · **Blocked (D-09)** · **Depends on:** SD-302
- **Acceptance criteria:** 1. `GET /api/v1/courier/parcels` returns only parcels assigned to the caller per D-09. 2. Includes pickup/drop-off contact data needed for delivery (courier is entitled). 3. Non-courier → 403.
- **Tests:** MockMvc; fixed clock for day boundary.

#### SD-310 · Courier status updates with note
- **Area:** delivery (courier web) · **Owner:** Member 6 · **P1 · M** · **Depends on:** SD-301
- **Acceptance criteria:** 1. `POST /api/v1/parcels/{id}/status-changes` only by the **currently assigned** courier; others → 404. 2. Legal transitions only (409 otherwise). 3. Note ≤ 500 chars stored in history. 4. Response returns updated detail.
- **Tests:** MockMvc incl. "courier B cannot update courier A's parcel" (TST-2 example from the brief).

#### SD-311 · Proof of delivery (recipient name, photo)
- **Area:** delivery · **Owner:** Member 6 · **P1 · M** · **Blocked (D-12)** · **Depends on:** SD-310
- **Acceptance criteria:** 1. Migration `delivery_proofs`. 2. Multipart upload by assigned courier; JPEG/PNG only by content sniffing; size ≤ configured max → else 400. 3. `DELIVERED` rejected without proof (409). 4. Photo readable only by owner, assigned courier, admin. 5. Photo bytes never logged.
- **Tests:** MockMvc for type/size/ownership.

#### SD-312 · Failed delivery with reason
- **Area:** delivery · **Owner:** Member 6 · **P1 · S** · **Depends on:** SD-310
- **Acceptance criteria:** 1. `FAILED` requires one of the D-19 codes; missing/unknown → 400. 2. Code appears in history and event; note does not appear in the event.
- **Tests:** unit + MockMvc.

#### SD-313 · Courier mobile UI
- **Area:** frontend · **Owner:** Member 6 · **P1 · M** · **Depends on:** SD-309–312, SD-008
- **Acceptance criteria:** 1. Today's list, detail, next-status button, failure reason picker, proof capture (camera input on mobile). 2. Usable at 360 px. 3. lint/build pass.

#### SD-314 · Reports: deliveries per day, success/failure rate
- **Area:** delivery (report package) · **Owner:** Member 8 · **P1 · M** · **Blocked (D-08)** · **Depends on:** SD-301
- **Acceptance criteria:** 1. Both endpoints admin-only with `from`/`to` (max 92 days). 2. Definitions exactly as D-08; days with zero included. 3. Time-zone boundary handled with configurable zone. 4. Read-only queries on delivery tables, reviewed by Member 3.
- **Tests:** unit with fixed clock; IT for SQL.

#### SD-315 · Reports UI
- **Area:** frontend · **Owner:** Member 8 · **P2 · S** · **Depends on:** SD-314, SD-008
- **Acceptance criteria:** 1. Date-range picker, table and simple chart. 2. lint/build pass.

#### SD-316 · Parcel ownership authorization suite
- **Area:** test · **Owner:** Member 6 (with Member 3) · **P1 · S** · **Depends on:** SD-106, SD-207, SD-310, SD-311
- **Acceptance criteria:** 1. Matrix rows for every parcel endpoint × {owner customer, other customer, assigned courier, other courier, admin, merchant, anonymous}. 2. All "not owned" cases return 404.

### Epic E4 — Merchant integration and webhooks

#### SD-401 · Merchant registration (pending)
- **Area:** merchant · **Owner:** Member 4 · **P1 · M** · **Blocked (D-22)** · **Depends on:** SD-103, SD-002
- **Acceptance criteria:** 1. Migration `merchants`. 2. `POST /api/v1/merchants` creates user (`MERCHANT`) + `PENDING` merchant atomically. 3. Duplicate email → 409. 4. `GET /merchants/me` shows status.
- **Tests:** unit + MockMvc.

#### SD-402 · Admin approval and rejection
- **Area:** merchant · **Owner:** Member 4 · **P1 · S** · **Depends on:** SD-401
- **Acceptance criteria:** 1. Admin lists by status, approves or rejects with reason; reviewer and time stored. 2. Only `PENDING` can be reviewed (409 otherwise). 3. Non-approved merchants get 403 on merchant features. 4. Suspend supported (ADM-3).
- **Tests:** MockMvc role matrix, state tests.

#### SD-403 · Merchant API keys
- **Area:** merchant · **Owner:** Member 5 · **P1 · M** · **Blocked (D-03)** · **Depends on:** SD-402
- **Acceptance criteria:** 1. Migration `merchant_api_keys`. 2. Approved merchant creates (key shown once), lists (prefix, label, dates only), revokes keys. 3. Only hash stored; secret from `SecureRandom` ≥ 32 bytes. 4. Other merchant's key → 404.
- **Tests:** unit for generation/hash; MockMvc.

#### SD-404 · API-key authentication in the security chain
- **Area:** merchant + config · **Owner:** Member 5 (review Member 1) · **P1 · M** · **Depends on:** SD-403
- **Acceptance criteria:** 1. A filter chain for `/api/v1/merchant-orders/**` accepts only `X-Api-Key`; JWTs rejected there and keys rejected elsewhere. 2. Constant-time comparison; same 401 for unknown/revoked/malformed keys and non-approved merchants. 3. `config` imports only `merchant.api`. 4. `last_used_at` updated without per-request write storms (e.g. at most once/minute).
- **Tests:** MockMvc for each failure case; boundary test.

#### SD-405 · Merchant order intake API
- **Area:** merchant · **Owner:** Member 5 · **P1 · M** · **Depends on:** SD-404, SD-206 (`ParcelCreation`)
- **Acceptance criteria:** 1. `POST /api/v1/merchant-orders` creates a `MERCHANT` parcel with the authenticated merchant as owner (never from body). 2. Idempotent per D-25 (no second event). 3. `GET /merchant-orders/{ref}` returns own order only. 4. Validation as customer booking.
- **Tests:** MockMvc incl. idempotency and isolation.

#### SD-406 · Merchant dashboard API
- **Area:** merchant · **Owner:** Member 4 · **P1 · S** · **Depends on:** SD-302, SD-402
- **Acceptance criteria:** 1. `GET /merchants/me/orders` paged with status filter via `ParcelQueries.forMerchant`. 2. Counts per status. 3. Own orders only.
- **Tests:** unit with mocked api; MockMvc.

#### SD-407 · Merchant registration and dashboard UI
- **Area:** frontend · **Owner:** Member 4 · **P1 · M** · **Depends on:** SD-401, SD-406, SD-403, SD-008
- **Acceptance criteria:** 1. Registration form, pending/rejected state screens. 2. Orders table with states. 3. API-key management screen (show once, copy). 4. lint/build pass.

#### SD-408 · Webhook endpoint registration with encrypted secret
- **Area:** merchant · **Owner:** Member 4 · **P1 · M** · **Blocked (D-17, D-18)** · **Depends on:** SD-402, SD-409 (URL validation)
- **Contract:** ADR 0004 §7.4, §7.5.
- **Acceptance criteria:** 1. Migration `webhook_endpoints`. 2. AES-256-GCM with random nonce, endpoint ID + key ID as AAD. 3. Secret shown once; never returned again or logged. 4. Missing/malformed active key prevents startup when webhooks enabled. 5. URL validated by SD-409 on registration. 6. All ADR 0004 §11 "secret encryption failures" tests.
- **Tests:** unit for crypto failure cases; MockMvc.

#### SD-409 · SSRF-safe webhook HTTP client
- **Area:** merchant · **Owner:** Member 5 · **P1 · L** · **Depends on:** —
- **Contract:** ADR 0004 §7.3 (option E, Apache HttpClient 5, Boot-managed).
- **Acceptance criteria:** 1. URL rules and prohibited ranges exactly as ADR 0004. 2. Validating `DnsResolver` is the only address source. 3. Redirects disabled; no proxy; TLS verification standard. 4. Local flag allows only loopback `http`, logs a warning. 5. The five mandatory checks have passing tests. 6. ADR 0002 version documentation updated.
- **Tests:** full ADR 0004 §11 SSRF list.

#### SD-410 · Merchant event consumer → webhook delivery records
- **Area:** merchant · **Owner:** Member 5 · **P1 · M** · **Depends on:** SD-501, SD-503, SD-408
- **Acceptance criteria:** 1. Queue `merchant.parcel-status-changed` declared with §5.1 arguments. 2. Merchant parcels create one record per active endpoint (unique per event/endpoint); customer parcels none. 3. Ordering/dedupe via `shared.messaging`. 4. Ack after commit.
- **Tests:** ADR 0004 consumer tests for merchant.

#### SD-411 · Webhook sender: signing, retries, backoff
- **Area:** merchant · **Owner:** Member 5 · **P1 · L** · **Depends on:** SD-409, SD-410
- **Acceptance criteria:** 1. `SKIP LOCKED` claim with lease. 2. Retry classification per ADR 0004 §7.1. 3. Exponential backoff with jitter and cap from configuration. 4. Signature headers per §7.2 with fresh timestamp. 5. `webhook_attempts` recorded without bodies/secrets. 6. Decrypt failure → `SECRET_UNAVAILABLE`, no send.
- **Tests:** ADR 0004 §11 webhook retry and signature tests with `MockRestServiceServer`/stub and fixed `Clock`.

#### SD-412 · Webhook history and manual redelivery
- **Area:** merchant · **Owner:** Member 4 · **P2 · M** · **Depends on:** SD-411
- **Acceptance criteria:** 1. Merchant sees own records and attempts (outcome categories only); admin sees all. 2. Redelivery rules per ADR 0004 §7.5 (failed only, cap, audit). 3. UI table in merchant dashboard. 4. Isolation → 404.
- **Tests:** MockMvc isolation + redelivery rules.

#### SD-413 · Demo webhook receiver and merchant integration guide
- **Area:** tools/docs · **Owner:** Member 5 · **P1 · S** · **Blocked (D-21)** · **Depends on:** SD-411
- **Acceptance criteria:** 1. Minimal receiver that verifies signature (constant time), timestamp tolerance and dedupe by event ID, and shows received events. 2. Merchant guide: auth header, order API, webhook verification, "apply highest sequence". 3. Works in the D-21 chosen setup.
- **Tests:** reference verifier tests from ADR 0004 §11.

### Epic E5 — RabbitMQ notifications and live tracking

#### SD-501 · RabbitMQ topology and broker verification
- **Area:** delivery/notification/merchant config · **Owner:** Member 9 · **P0 · M** · **Depends on:** SD-003
- **Acceptance criteria:** 1. `swiftdrop.events` declared by delivery; DLX and queues declared by consumers with §5.1 arguments. 2. IT verifies `stream_queue` flag, argument acceptance and at-least-once dead-lettering on RabbitMQ 4.3. 3. Publisher confirms and returns configured. 4. ADR 0004 "to validate" items recorded as verified.
- **Tests:** IT. **Docs:** architecture §7.

#### SD-502 · Outbox relay
- **Area:** delivery infrastructure · **Owner:** Member 9 (review Member 3) · **P0 · L** · **Depends on:** SD-303, SD-501
- **Acceptance criteria:** ADR 0004 §3–4.2 behavior: advisory lock, ordered per parcel, per-row backoff, poll stops on broker failure, metrics and alert log lines, retention cleanup.
- **Tests:** ADR 0004 §11 outbox crash windows and relay ordering (mocked `RabbitTemplate`).

#### SD-503 · Shared ordered-consumer support
- **Area:** shared · **Owner:** Member 9 · **P0 · M** · **Blocked (D-29)** · **Depends on:** SD-505
- **Acceptance criteria:** 1. Dedupe, sequence check, pending storage and reconciliation scheduling per ADR 0004 §4.3, independent of business types. 2. Bounded reconciliation, "stuck" marking with metric. 3. Used by both consumers without copy-paste.
- **Tests:** ADR 0004 consumer ordering tests.

#### SD-504 · Notification consumer and mock SMS store
- **Area:** notification · **Owner:** Member 9 · **P1 · M** · **Depends on:** SD-501, SD-503
- **Acceptance criteria:** 1. Migrations for `notifications` and state tables. 2. Policy table ADR 0004 §8. 3. Message text contains tracking number and status, no addresses; content never logged. 4. Duplicate event creates no second message.
- **Tests:** ADR 0004 notification tests.

#### SD-505 · `delivery.api.ParcelEvents` for reconciliation
- **Area:** delivery api · **Owner:** Member 3 · **P1 · S** · **Depends on:** SD-303
- **Acceptance criteria:** 1. Returns events for a sequence range rebuilt from history with original IDs. 2. Identical to outbox payloads (test).
- **Tests:** unit.

#### SD-506 · Customer notifications inbox
- **Area:** notification + frontend · **Owner:** Member 9 · **P1 · S** · **Depends on:** SD-504, SD-008
- **Acceptance criteria:** 1. `GET /api/v1/notifications` own only, paged; mark read. 2. UI inbox with unread badge. 3. Ownership rows added.
- **Tests:** MockMvc.

#### SD-507 · Dead-letter replay (audited)
- **Area:** notification/merchant · **Owner:** Member 9 · **P2 · M** · **Blocked (D-20)** · **Depends on:** SD-504, SD-410
- **Acceptance criteria:** ADR 0004 §6 recovery steps 4–5 via an admin-only endpoint; replay audit recorded.
- **Tests:** IT replay processed once.

#### SD-508 · SSE tracking stream
- **Area:** tracking · **Owner:** Member 10 · **P1 · M** · **Blocked (D-14)** · **Depends on:** SD-304, SD-301
- **Acceptance criteria:** 1. Snapshot on connect from `ParcelQueries.byTrackingNumber`. 2. Push on in-process after-commit event only for matching tracking number. 3. Heartbeat, timeout, cleanup on disconnect/error; connection cap. 4. No PII in stream. 5. Security chain permits the public stream path only.
- **Tests:** unit for registry; MockMvc async test for snapshot + push.

#### SD-510 · Public mobile tracking page with live updates
- **Area:** frontend · **Owner:** Member 10 · **P1 · M** · **Depends on:** SD-508, SD-008
- **Acceptance criteria:** 1. `/track/[trackingNumber]` with search box, timeline and live updates via `EventSource`; reconnect handling. 2. Works without login, 360 px. 3. lint/build pass.

### Epic E6 — Frontend integration

#### SD-603 · Responsive and accessibility pass across all pages
- **Area:** frontend · **Owner:** Member 10 · **P2 · S** · **Depends on:** SD-209, SD-313, SD-308, SD-407, SD-510
- **Acceptance criteria:** 1. All pages usable at 360 px and keyboard-navigable. 2. Consistent error/empty/loading states. 3. Findings fixed by page owners via their own issues.

(Feature pages are part of each workstream's issues: SD-105, 209, 210, 212, 308, 313, 315, 407, 506, 510.)

### Epic E7 — Automated testing and security verification

#### SD-701 · Event-flow integration test (TST-1)
- **Area:** test · **Owner:** Member 9 (with Member 10) · **P0 · M** · **Depends on:** SD-003, SD-502, SD-504, SD-410
- **Acceptance criteria:** ADR 0004 §11 "Integration" bullet: status change → outbox → RabbitMQ → notification stored and webhook record created; malformed message reaches DLQ with death headers; delivery limit backstop; replayed dead letter processed once.

#### SD-703 · Coverage and test results in CI and submission (TST-3)
- **Area:** CI · **Owner:** Member 7 · **P1 · S** · **Depends on:** SD-004, SD-003
- **Acceptance criteria:** 1. CI publishes Surefire/Failsafe reports and coverage artifacts. 2. Summary numbers copied into the test report (SD-908).

#### SD-704 · Security and personal-data review (SYS-7, IMP-6)
- **Area:** security · **Owner:** Member 7 · **P1 · M** · **Depends on:** most E2–E5 issues
- **Acceptance criteria:** 1. Checklist run over every endpoint: validation, 404-on-not-owned, no PII/secrets in logs and errors, no hard-coded secrets (`git grep` patterns), `.env.example` placeholders only. 2. Exposure of springdoc and Actuator decided and configured (architecture §10 open). 3. Findings filed as issues to the owners.

### Epic E8 — Containerization and deployment

#### SD-801 · Backend Dockerfile
- **Area:** infra · **Owner:** Member 10 · **P1 · S** · **Blocked (D-30)**
- **Acceptance criteria:** 1. Multi-stage build with JDK 21 build / JRE 21 runtime, non-root user. 2. Config only through env. 3. Healthcheck on `/actuator/health`. 4. No secrets in image layers.

#### SD-802 · Frontend Dockerfile
- **Area:** infra · **Owner:** Member 10 · **P1 · S** · **Blocked (D-30)**
- **Acceptance criteria:** 1. Next.js standalone output, Node 24, non-root. 2. API base URL configurable at build/run as documented. 3. `npm ci` from lockfile.

#### SD-803 · Full-stack Compose profile
- **Area:** infra · **Owner:** Member 10 · **P1 · M** · **Depends on:** SD-801, SD-802
- **Acceptance criteria:** 1. `docker compose --profile app up -d` starts frontend, backend, PostgreSQL, RabbitMQ; default `up` unchanged. 2. Backend waits for healthy services. 3. Ports bound to `127.0.0.1`. 4. `docker compose config --quiet` passes. 5. development-setup updated (never `down -v` advice beyond existing warning).

#### SD-804 · README setup instructions for submission (SUB-1)
- **Area:** docs · **Owner:** Member 10 · **P1 · S** · **Depends on:** SD-803
- **Acceptance criteria:** 1. Root README: prerequisites, one-command start, demo accounts setup, test commands, links to docs. 2. A teammate follows it on a clean machine successfully.

### Epic E9 — Design documentation and submission

| ID | Title (owner, priority/size) | Acceptance criteria | Depends on |
|---|---|---|---|
| SD-901 | Use case diagrams — DES-1 (Member 2, P1/S) | Four actors, all CUS/MER/COU/ADM requirements shown; sources (e.g. PlantUML/Mermaid) committed in `docs/design/` | SD-001 |
| SD-902 | ER diagram — DES-2 (Member 3, P1/S) | Covers users, roles, merchants, parcels, status history, couriers, zones, pricing; matches migrations; module ownership colored | SD-001 (update after E2–E4 merges) |
| SD-903 | Architecture proposal — DES-3 (Member 7, P1/S) | Modular monolith justification, presentation/business/data layers, links ADR 0001/0003 | SD-001 |
| SD-904 | Integration design — DES-4 (Member 9, P1/S) | REST request/reply, pub/sub via RabbitMQ, outbox, webhooks, SSE, each with why | SD-001 |
| SD-905 | Sequence/activity diagrams — DES-5 (Member 5, P1/M) | Create delivery; merchant API order; courier status update → notification + webhook; OAuth 2.0/OIDC login | SD-104, SD-405 |
| SD-906 | Design patterns note — DES-6 (Member 8, P1/S) | Repository, Strategy (pricing), Observer/pub-sub (events), Outbox, SOLID/DRY examples from real code | SD-202, SD-502 |
| SD-907 | Design documentation PDF — SUB-2 (Member 4, P1/S) | DES-1…6 assembled, consistent with code at submission | SD-901–906 |
| SD-908 | Test report and coverage summary — SUB-3 (Member 7, P1/S) | Test counts, coverage numbers, TST-1/TST-2 evidence | SD-703, SD-701, SD-316 |
| SD-909 | Contribution sheet — SUB-4 (Member 7, P1/S) | Every member's issues/modules from GitHub history; reviewed by all members | all |
| SD-910 | Demo script and rehearsal — DEM-1…6 (Member 10, P1/M) | Scripted flows for DEM-1–4, architecture walkthrough (DEM-5), each member's 1-minute part (DEM-6); rehearsed from containers; fallback plan for Google/network | SD-803, SD-413, SD-504, SD-510 |
| SD-911 | Bonus evaluation — BON-1/BON-2 (unassigned, P3/S) | Decision recorded; ADR drafted only if pursued | SD-001 |

## 3. Requirement-to-issue traceability

| Req. | Issues |
|---|---|
| CUS-1 | existing identity; SD-104, SD-105, SD-107 |
| CUS-2 | SD-204, SD-206, SD-209 |
| CUS-3 | SD-201, SD-202, SD-203, SD-209 |
| CUS-4 | SD-301, SD-207, SD-508, SD-510 |
| CUS-5 | SD-207, SD-208, SD-210 |
| CUS-6 | SD-205, SD-304, SD-510 |
| MER-1 | SD-401, SD-402, SD-407 |
| MER-2 | SD-403, SD-404, SD-405 |
| MER-3 | SD-408, SD-409, SD-410, SD-411, SD-412, SD-413 |
| MER-4 | SD-406, SD-407 |
| COU-1 | existing identity; SD-101, SD-102 |
| COU-2 | SD-309, SD-313 |
| COU-3 | SD-310, SD-311, SD-313 |
| COU-4 | SD-312, SD-313 |
| ADM-1 | SD-302, SD-305, SD-306, SD-308 |
| ADM-2 | SD-302, SD-307, SD-308 |
| ADM-3 | SD-102 (users), SD-402 (merchants), SD-211, SD-212 (zones/pricing) |
| ADM-4 | SD-314, SD-315 |
| SYS-1 | SD-508, SD-510 |
| SYS-2 | existing identity; SD-102, SD-106, SD-316 |
| SYS-3 | existing identity; SD-104, SD-403, SD-404 |
| SYS-4 | SD-303, SD-501, SD-502, SD-503, SD-410 |
| SYS-5 | SD-504, SD-506 |
| SYS-6 | SD-801, SD-802, SD-803 |
| SYS-7 | SD-002, SD-304, SD-704 (and PII rules in every issue) |
| DES-1…6 | SD-901…SD-906 |
| IMP-1 | SD-008 and all frontend issues, SD-603 |
| IMP-2 | all backend issues |
| IMP-3 | SD-002, SD-106 |
| IMP-4 | SD-501–504, SD-410, SD-411 |
| IMP-5 | SD-508, SD-510 |
| IMP-6 | SD-002, SD-503, SD-704 |
| IMP-7 | SD-801–803 |
| TST-1 | SD-003, SD-701 |
| TST-2 | SD-106, SD-316 (+ rows in every endpoint issue) |
| TST-3 | SD-004, SD-703, SD-908 |
| DEM-1 | SD-105, SD-209, SD-510, SD-910 |
| DEM-2 | SD-405, SD-413, SD-910 |
| DEM-3 | SD-308, SD-313, SD-910 |
| DEM-4 | SD-504, SD-506, SD-910 |
| DEM-5, DEM-6 | SD-910, SD-909 |
| SUB-1 | SD-804 |
| SUB-2 | SD-907 |
| SUB-3 | SD-908 |
| SUB-4 | SD-909 |
| BON-1, BON-2 | SD-911 (optional) |

Every brief requirement maps to at least one issue.

## 4. Plan validation

- **Duplicated work:** none found. Pages are owned with their feature; the consumer algorithm is
  written once (SD-503); `ParcelCreation` is shared by customer and merchant intake (SD-206, SD-405).
- **Ownership conflicts:** every module and shared file has one primary owner (§1). The three
  modules with contributors (`delivery`, `merchant`, `pricing` via reports in delivery) name the
  package each contributor owns and require module-owner review.
- **Dependencies:** all explicit in each issue; the graph was checked and is acyclic. The longest
  chain is SD-001 → SD-003 → SD-501 → SD-502 → SD-701, plus SD-204 → SD-301 → SD-303 → SD-502.
- **Size:** no issue is larger than L; L issues (SD-204, SD-409, SD-411, SD-502) are self-contained.
- **Parallelism:** after Wave 1, pricing (M8), delivery core (M3), dispatch profiles (M7),
  merchant registration (M4), SSRF client (M5), topology (M9), app shell (M1) and Dockerfiles (M10)
  can run in parallel.
- **Blocked issues:** 25 issues are blocked on decisions from SD-001; the rest wait only on other
  issues.

## 5. Suggested implementation sequence

| Wave | Goal | Issues |
|---|---|---|
| 0 | Decisions and readiness | SD-001, SD-006, SD-007 |
| 1 | Foundations | SD-002, SD-003, SD-005, SD-103, SD-106, SD-008, SD-101, SD-201, SD-205, SD-409, SD-801, SD-802, SD-901, SD-903, SD-904 |
| 2 | Core domain | SD-202, SD-204, SD-301, SD-102, SD-305, SD-401, SD-402, SD-501, SD-004 |
| 3 | First vertical slice (customer books, dispatcher assigns, courier delivers) | SD-203, SD-206, SD-302, SD-303, SD-306, SD-307, SD-309, SD-310, SD-312, SD-209, SD-105, SD-104 |
| 4 | Integration | SD-502, SD-505, SD-503, SD-504, SD-304, SD-508, SD-403, SD-404, SD-405, SD-408, SD-410, SD-411, SD-311 |
| 5 | Completion | SD-207, SD-208, SD-210, SD-211, SD-212, SD-308, SD-313, SD-314, SD-315, SD-406, SD-407, SD-412, SD-413, SD-506, SD-510, SD-009, SD-803 |
| 6 | Verification and submission | SD-316, SD-701, SD-703, SD-704, SD-507, SD-603, SD-107, SD-804, SD-902, SD-905–SD-910 |

## 6. Recommended first issues after approval

1. **SD-001** — hold the decision review first; every Wave 1 issue depends on it in some way.
2. **SD-003 + SD-005** (Member 1) — real-database verification is the largest outstanding risk.
3. **SD-002, SD-103, SD-106** (Member 1) — small shared contracts that unblock all modules.
4. **SD-201 → SD-202** (Member 8) and **SD-205 → SD-204 → SD-301** (Member 3) — the critical path.
5. **SD-409** (Member 5) and **SD-501** (Member 9) — independent, high-risk technical spikes.
6. **SD-008** (Member 1) and **SD-801/802** (Member 10) — unblock frontend and container work.
