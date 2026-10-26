# SwiftDrop architecture

This is the main description of SwiftDrop's architecture: what exists, what is required, decided
or proposed, and what is still open. Requirements come from the assignment brief, summarized in
[docs/project-requirements.md](project-requirements.md) (IDs such as `CUS-1` refer to it).
Decisions and their rationale live in the [ADRs](adr/README.md); versions live in
[ADR 0002](adr/0002-technology-and-version-baseline.md) and the package manifests. This document
links to them instead of repeating them.

## Status labels

| Label | Meaning |
|---|---|
| **Implemented** | Verified in the current code or configuration |
| **Required** | Stated by the assignment brief ([project requirements](project-requirements.md)); not necessarily implemented |
| **Decided** | Accepted in an ADR, or a binding rule in [docs/ai-agent-guidelines.md](ai-agent-guidelines.md), but not necessarily implemented |
| **Proposed** | Recommended, awaiting approval. Do not implement without an approved task. |
| **Open** | Unresolved; needs a decision |

When a change implements, decides or rejects something, update its label in the same change.

## 1. Purpose and boundaries

SwiftDrop is a parcel delivery and courier management system, built as a university Enterprise
Application Development group project for a courier company operating island-wide.

- **Required:** four roles (Customer, Merchant, Courier, Dispatcher/Admin); customer deliveries
  with fee estimates and real-time tracking; merchant registration, API orders and webhooks;
  courier status updates; dispatcher assignment, management and reports; asynchronous status
  events with a notification consumer; containerized deployment
  ([project requirements](project-requirements.md)).
- **Implemented:** project scaffolding, a frontend placeholder page, local PostgreSQL and RabbitMQ
  containers, and email/password authentication in `identity` (section 9). **No business feature
  exists yet.**
- **Decided:** one system with one backend and one database
  ([ADR 0001](adr/0001-modular-monolith-in-a-monorepo.md)).
- **Open:** production hosting, a real email or SMS provider (notifications use a mock SMS channel
  for now, [ADR 0004](adr/0004-messaging-and-integration-reliability.md)), and data retention.

## 2. Technology stack

| Area | Technology | Status |
|---|---|---|
| Frontend | Next.js App Router, React, TypeScript, Tailwind CSS, shadcn/ui on Base UI, npm | Implemented |
| Backend | Java 21, Spring Boot 4.1 (Spring MVC), Maven Wrapper | Implemented |
| Persistence | PostgreSQL 18, Spring Data JPA / Hibernate, Flyway | Implemented (identity tables only) |
| Messaging | RabbitMQ 4.3, Spring AMQP | Implemented (identity tables only) |
| Security | Spring Security; OAuth2 client and resource-server starters | Implemented (defaults only, see section 9) |
| API docs | springdoc-openapi | Implemented (default endpoints) |
| Local infrastructure | Docker Compose (`compose.yaml`) | Implemented (database and broker only, see section 13) |

Exact versions and compatibility evidence, including why Jackson 2 and Jackson 3 coexist, are in
[ADR 0002](adr/0002-technology-and-version-baseline.md).

## 3. Repository layout

**Implemented.** One Git monorepo ([ADR 0001](adr/0001-modular-monolith-in-a-monorepo.md)):

| Path | Contents |
|---|---|
| `frontend/` | Next.js application. Separate build (npm). |
| `backend/` | Spring Boot application, package root `com.swiftdrop`. Separate build (Maven Wrapper). |
| `compose.yaml`, `.env.example` | Local PostgreSQL and RabbitMQ; template for the git-ignored `.env` |
| `docs/` | This document, the requirements summary, the agent guidelines, the setup guide and the ADRs |

The frontend and backend share no code. They communicate only over HTTP.

## 4. Backend: modular monolith

- **Decided:** one Spring Boot application, structured as a modular monolith. No microservices,
  service mesh, Kubernetes, Redis or second message broker
  ([ADR 0001](adr/0001-modular-monolith-in-a-monorepo.md)).
- **Decided:** each business module exposes a small public API. Other modules use only that API and
  never reach into another module's internal classes, entities or repositories.
- **Implemented:** the application class `com.swiftdrop.SwiftdropBackendApplication` and an empty
  package skeleton: `com.swiftdrop.<module>` with an `api` sub-package for each of the seven modules
  below, plus `com.swiftdrop.config` and `com.swiftdrop.shared`. They contain only
  `package-info.java` files, except `identity`, whose `domain` package holds the `User` entity,
  the `Role` enum and `UserRepository`, with `application` (registration, login), `security` (JWT
  signing and verification), `web` (REST controllers) and `api` (`IdentityClaims`, the token claim
  names). `config` holds the security filter chain.
- **Decided:** the module layout, dependencies and package rules below
  ([ADR 0003](adr/0003-backend-modules-and-boundaries.md), Accepted). Only `identity` has code so
  far, and the decision is not an authorization to implement one: that needs an
  approved task.

### Modules (Decided, ADR 0003)

| Module | Responsibility | Owns |
|---|---|---|
| `identity` | Signup and login, Google login, JWT, roles, user management | users, roles, user–role links, external identity links |
| `pricing` | Fee calculation, zones, pricing configuration | zones, pricing rules |
| `delivery` | Parcels (main aggregate and entity: `Parcel`), status lifecycle and history, current courier assignment, notes, proof, failure reasons, receipts, tracking-number lookup, the two required reports, workload counts | parcels, status history, proof records |
| `dispatch` | Courier profiles; assignment workflow and courier eligibility; workload view | couriers |
| `merchant` | Registration and approval, API credentials, order intake, dashboard queries, webhook delivery and retries | merchants, API credentials, webhook endpoints, webhook delivery records |
| `notification` | Notifications triggered by status events | notification messages |
| `tracking` | Live SSE connections; no history of its own | nothing persistent |

`reporting` is deferred: the required reports (ADM-4) and workload counts (ADM-2) use data owned by
`delivery`. ADR 0003 lists what each module must not own.

### Dependencies (Decided, ADR 0003)

Allowed calls into another module's `api` package only; the graph is acyclic:

```text
identity     → none
pricing      → none
delivery     → identity, pricing
dispatch     → identity, delivery
merchant     → identity, delivery
notification → identity, delivery
tracking     → delivery
```

- No module depends on `notification` or `tracking`; `delivery` never calls `dispatch`,
  `merchant`, `notification` or `tracking`.
- Packages per module, only as needed: `api` (the only importable package), `web`, `application`,
  `domain`, `infrastructure`. `com.swiftdrop.config` is the composition root (imported by no
  module); `com.swiftdrop.shared` holds technical code only.
- **Implemented:** the dependency-free JUnit test `ModuleBoundaryTest` (`backend/src/test/java`)
  scans the main sources in every build and fails on any `com.swiftdrop.*` reference outside a
  module's own packages, its allowed modules' `api` packages and `shared`; on a module referencing
  `config`; on `shared` referencing a module; and on unknown top-level packages. It is textual and
  cannot see reflection. Spring Modulith and
  ArchUnit are not used: neither is managed by Spring Boot
  ([ADR 0002](adr/0002-technology-and-version-baseline.md) version policy).

## 5. Frontend and API boundary

- **Decided:** the frontend only presents data and calls the backend REST API. Business rules,
  validation that matters, authorization, persistence and integrations live in the backend. Hiding
  a page or button is never a security control ([ADR 0001](adr/0001-modular-monolith-in-a-monorepo.md)).
- **Required:** a responsive UI (IMP-1); a mobile-friendly public tracking page (CUS-6); versioned,
  validated APIs with centralized error handling (IMP-3).
- **Decided (project rule):** REST paths are plural nouns in kebab-case under `/api/v1/...`.
- **Implemented:** the frontend has a placeholder page and one shadcn/ui component. It does not call
  the backend yet. The backend has only the identity endpoints of section 9; springdoc serves its
  default OpenAPI endpoints (behind authentication). Identity errors use RFC 9457 problem details.
  CORS allows the origins in `SWIFTDROP_CORS_ALLOWED_ORIGINS` (default `http://localhost:3000`) on
  `/api/**`, without credentials.
- **Open:** how the frontend calls the backend (directly from the browser, or through Next.js
  server code acting as a BFF), CORS, the API error format and pagination conventions. The brief
  mentions "modular monolith with a BFF" only as an example (DES-3).

## 6. Database

- **Decided:** PostgreSQL is the single database. Flyway owns every schema change through versioned
  migrations (`V<n>__<description>.sql`, `snake_case` names). Merged migrations are never edited.
  Hibernate only validates the schema.
- **Implemented:** the backend connects to PostgreSQL through `SWIFTDROP_POSTGRES_*` settings,
  `spring.jpa.hibernate.ddl-auto=validate` is set, and Flyway runs at startup. The first migration,
  `db/migration/identity/V202610251600__identity_create_users_and_roles.sql`, creates `roles`
  (seeded with `CUSTOMER`, `MERCHANT`, `COURIER`, `DISPATCHER_ADMIN`), `users` and `user_roles`.
  `MigrationConventionTest` checks the folder, naming and unique versions without a database.
  Applying the migration and Hibernate validation against PostgreSQL have not yet been run.
- **Decided (ADR 0003):**
  - One shared `public` schema, chosen pragmatically for the team (not a universal best practice).
  - Each table has one owning module (table in section 4). Only the owner writes its migrations and
    accesses it.
  - Cross-module references by ID, never JPA associations. Cross-module foreign keys when justified
    by referential integrity; no cascading deletes across modules.
  - Migrations organized per module (for example `db/migration/<module>/`) with versions that do
    not collide between branches (for example timestamps); Flyway's discovery of module folders is
    verified before the first business migration.
  - Neither one schema nor separate schemas isolate access while the backend uses one privileged
    database account; ownership is enforced by module rules, review and the boundary test.
- **Required:** the ER diagram must cover users, roles, merchants, parcels, status history,
  couriers, zones and pricing (DES-2).
- **Decided (team, milestone 2; no ADR yet):** entity IDs are UUIDs (`uuid` columns, generated by
  Hibernate). Reference data may use a natural key, as `roles.name` does.
- **Open:** the tracking-number format.

## 7. Messaging (RabbitMQ) and status events

- **Required:** at least one asynchronous integration pattern for status-change events, which
  trigger notifications and merchant webhooks (SYS-4); a notification service consuming status
  events (SYS-5); merchant webhooks with retry on failure (IMP-4).
- **Decided:** RabbitMQ through Spring AMQP carries asynchronous work. Classic or quorum queues only;
  classic mirrored queues were removed in RabbitMQ 4.
- **Implemented:** connection settings (`SWIFTDROP_RABBITMQ_*`) and the Actuator health check
  only. **No exchanges, queues, producers or consumers exist.** A connection smoke test against
  RabbitMQ 4.3 succeeded in Stage 5.
- **Decided (ADR 0003):** `delivery` is the only publisher of status-change events;
  `notification` and `merchant` consume them through RabbitMQ; `tracking` may receive them
  in-process. Side effects never block or roll back the status change.
- **Decided ([ADR 0004](adr/0004-messaging-and-integration-reliability.md), Accepted).**
  **None of it is implemented yet**, and the decision is not an authorization to implement it
  without an approved task:
  - **Guarantee:** at-least-once delivery of committed status changes; no exactly-once promise.
    Duplicates are normal and absorbed by event IDs and per-parcel sequence numbers.
  - **Event:** `parcel.status-changed`, a versioned JSON envelope with event ID and per-parcel
    `sequence`, and no personal data.
  - **Publishing:** a transactional outbox owned by `delivery` and a single relay (PostgreSQL
    advisory lock) with publisher confirms and the mandatory flag. A failing row blocks only its own
    parcel; unpublished rows are never deleted.
  - **Topology:** topic exchange `swiftdrop.events` (owned by `delivery`) and one durable quorum
    queue per consumer (`notification.parcel-status-changed`, `merchant.parcel-status-changed`),
    each with an explicit delivery limit, at-least-once dead-lettering (needs the `stream_queue`
    feature flag and `reject-publish` overflow), a maximum length and a `<queue>.dlq`.
  - **Consumers:** acknowledge after commit; deduplicate by event ID; apply each parcel's events in
    sequence order, hold early events as pending and reconcile gaps from `delivery`'s history;
    bounded in-process retries, never requeue, dead-letter malformed messages at once; audited
    replays.
  - **Webhooks:** database-driven delivery records per event and endpoint with bounded exponential
    backoff; HMAC-SHA256 signatures with timestamp; secrets encrypted with AES-256-GCM and
    environment-supplied keys; SSRF protection with checks before every attempt and Apache
    HttpClient 5 with a validating DNS resolver (approved dependency, not yet added); merchants see
    only their own records; audited, bounded manual redelivery.
  - **Notifications:** a mock SMS channel stored by `notification`; the owning customer of a
    customer-created parcel is notified on `PICKED_UP`, `OUT_FOR_DELIVERY`, `DELIVERED` and
    `FAILED`; merchant-created parcels are reported to the merchant by webhook only.
  - **Tables added by the decision:** `delivery` owns the outbox; `notification` and `merchant` own
    their processed-event, last-applied-sequence and pending-event tables; `merchant` also owns the
    webhook attempt history. Initial operational values are in ADR 0004, section 9.
- **Open (implementation questions listed in ADR 0004):** the dead-letter replay mechanism, queue
  arguments versus RabbitMQ policies, repair of stuck parcels and unpublishable outbox rows,
  suppressing superseded notifications, failure-reason codes, notifications for recipients of
  merchant parcels, endpoints per merchant and secret rotation, the demo receiver, encryption-key
  variable names, and the final tuning values. ADR 0004 also lists broker and client behavior
  that must be verified during implementation, including the five mandatory checks for the
  webhook HTTP client.

## 8. Delivery lifecycle and real-time tracking

- **Required:** the statuses **Created → Picked Up → In Transit → Out for Delivery → Delivered /
  Failed** (CUS-4); status updates at each stage with a note or proof, and a reason for failures
  (COU-3, COU-4); real-time updates (SYS-1, IMP-5); public tracking by tracking number (CUS-6).
- **Decided:** real-time updates use Server-Sent Events (Spring MVC `SseEmitter`); WebSockets need
  a new ADR ([ADR 0001](adr/0001-modular-monolith-in-a-monorepo.md)).
- **Decided (ADR 0003):** `delivery` alone changes a `Parcel`'s status (`CREATED`, `PICKED_UP`, `IN_TRANSIT`,
  `OUT_FOR_DELIVERY`, `DELIVERED`, `FAILED`) and records history. Courier assignment is a separate
  field, not a status. Initial rules: forward one step at a time; `DELIVERED` and `FAILED` are
  terminal; only the assigned courier makes ordinary updates; a courier must be assigned before
  `PICKED_UP`; each change records time and actor, `FAILED` requires a reason, notes and proof are
  recorded as applicable. `tracking` sends the current state on connect and never keeps its own
  history.
- **Implemented:** nothing.
- **Open:** the stages from which `FAILED` is allowed, reassignment, cancellation, retries after
  failure, dispatcher/admin overrides, whether proof is mandatory for `DELIVERED`; what the public
  tracking page may show (proposal: status and timestamps only, no personal data); how the browser
  authenticates non-public streams (the native `EventSource` API cannot send an `Authorization`
  header).

## 9. Authentication and authorization

- **Required:** role-based access control for Customer, Merchant, Courier and Dispatcher/Admin
  (SYS-2); JWT-based authentication for the application, plus OAuth 2.0 for social login or
  merchant API access (SYS-3); customer signup and login including Google login (CUS-1); merchant
  API orders using API keys or OAuth 2.0 client credentials (MER-2); hashed passwords (SYS-7).
- **Decided:** authorization is enforced on the backend for every protected operation, by role and
  by ownership ([ADR 0001](adr/0001-modular-monolith-in-a-monorepo.md)).
- **Implemented:** email/password authentication with stateless JWT access tokens:
  - `POST /api/v1/users` (public) registers a user with the `CUSTOMER` role only; the request has
    no role field. Email is trimmed and lower-cased; duplicates return 409, with the unique
    constraint as the final check. Passwords (8-72 characters, at most 72 bytes) are hashed with
    BCrypt through Spring Security's delegating encoder.
  - `POST /api/v1/auth/tokens` (public) returns an HS256 access token (`sub` = user ID, `roles`,
    `iss`, `iat`, `exp`; default lifetime 15 minutes). Every failure returns the same 401.
  - `GET /api/v1/users/me` (authenticated) returns the caller's account.
  - The resource server checks signature, expiry, issuer and the `sub`/`roles` claims; roles
    become `ROLE_<name>` authorities, and `@PreAuthorize` is enabled. CSRF is disabled because
    tokens are sent only in the `Authorization` header and there are no sessions. The key comes
    from `SWIFTDROP_JWT_SECRET`; startup fails without one of at least 32 bytes.
  - Not implemented: refresh tokens and logout, linking Google to an existing account, merchant
    API credentials. No role-restricted business endpoint exists yet.
- **Implemented:** Google sign-in (CUS-1) with OpenID Connect through Spring Security's
  `oauth2Login`, a backend redirect flow. It is active only when `SWIFTDROP_GOOGLE_CLIENT_ID` (and
  then `SWIFTDROP_GOOGLE_CLIENT_SECRET`) is set; otherwise its endpoints do not exist.
  - `GET /oauth2/authorization/google` redirects to Google; `GET /login/oauth2/code/google`
    handles the callback. Spring Security checks the state, exchanges the code and validates the
    ID token (Google's signing keys, issuer, audience, expiry, nonce). The identity module adds a
    separate filter chain for these two paths; an HTTP session holds only the pending
    authorization request and is invalidated when the callback completes.
  - The account is found by Google's `sub` claim in `external_identities` (unique per provider
    and subject), never by email. A first-time user with `email_verified = true` gets a new
    passwordless `CUSTOMER` account and link. An unverified email returns 403; an email that
    belongs to an existing unlinked account returns 409 and nothing is linked. Roles come only
    from the stored account.
  - Decided ([ADR 0005](adr/0005-google-oauth-token-handoff.md)): Google sign-in token handoff
    redirects to the frontend callback with a short-lived (60 s), single-use hashed exchange code
    stored in `google_login_codes`, exchanged via `POST /api/v1/auth/google/exchanges`. Tokens are
    never placed in URLs.
- **Decided (ADR 0003):** `identity` owns accounts, roles, local and Google login and the
  application's JWTs; `merchant` owns merchant API credentials; `config` wires both into the
  security filter chain.
- **Open:**
  - An authenticated flow for linking Google to an existing account.
  - Merchant API authentication: API keys (no new dependency) or OAuth 2.0 client credentials
    (needs an authorization server); key format and rotation. Webhook signing is decided in
    [ADR 0004](adr/0004-messaging-and-integration-reliability.md).
  - How courier and dispatcher/admin accounts are created.

## 10. Security and personal data

**Required:** secure handling of personal data (addresses, phone numbers): input validation, hashed
passwords, no sensitive data in error messages or logs (SYS-7); no hard-coded secrets (IMP-6).

**Decided (project rules):**

- Validate all external input on the backend. Return safe error messages without stack traces.
- Merchant API credentials and webhook secrets are stored hashed or encrypted, never in plain text.
- Never commit secrets, credentials, tokens or personal data.
- Keep personal data (names, addresses, phone numbers, delivery locations, proof-of-delivery data)
  and secrets out of logs, and expose personal data only to users entitled to it.

**Decided ([ADR 0004](adr/0004-messaging-and-integration-reliability.md)):**
events and webhook payloads carry no personal data; webhook secrets are encrypted with AES-256-GCM
(keys from the environment, rotation by key ID); outbound webhook requests are restricted to
validated public destinations (not yet implemented).

**Open:** retention and deletion of personal data, storage for proof-of-delivery photos, and
whether springdoc and Actuator details are exposed outside local development.

## 11. Configuration and secrets

- **Implemented:**
  - Local settings live in the git-ignored root `.env`, created from the committed `.env.example`.
    Docker Compose reads it, and the backend imports it when started from `backend/`
    (`spring.config.import`). Environment variables take precedence.
  - Passwords have no defaults in `compose.yaml` or `application.properties`.
  - Local service ports are published on `127.0.0.1` only.

  Details are in [docs/development-setup.md](development-setup.md#6-local-infrastructure-postgresql-and-rabbitmq).
- **Required:** configuration via environment variables, no hard-coded secrets (IMP-6).
- **Decided (project rule):** configuration is environment-specific; secrets come from the
  environment, never from committed files.
- **Open:** Spring profiles (none exist yet), secret handling outside local development, and the
  names of the webhook encryption-key variables (ADR 0004).

## 12. Testing

- **Required:** unit and integration tests for backend services, including at least one test for
  the event/integration flow (TST-1); authorization tests, for example that a courier cannot access
  another courier's deliveries (TST-2); measurable code coverage with test results in the
  submission (TST-3).
- **Implemented:**
  - One context-load test (`SwiftdropBackendApplicationTests`) that runs without Docker, PostgreSQL
    or RabbitMQ.
  - The Boot-managed JUnit Jupiter, Mockito, AssertJ and Spring test starters are declared.
  - `.\mvnw.cmd clean verify` must pass without Docker.
  - The frontend has lint and type checking (`npm run lint`, `npm run build`) and no tests.
  - CI (`.github/workflows/ci.yml`) runs the frontend lint/build and the Docker-free backend
    `clean verify` on pull requests targeting `dev` or `main` and on pushes to them
    ([CONTRIBUTING](../CONTRIBUTING.md#continuous-integration)).
- **Decided (project rules):** new business logic comes with tests; logic is unit-tested with
  Mockito; H2 is not used as a PostgreSQL substitute.
- **Decided ([ADR 0004](adr/0004-messaging-and-integration-reliability.md)):** the messaging
  and webhook test requirements (outbox crash windows, ordering and gaps, dead-letter recovery,
  SSRF, signatures, secret encryption, retries and merchant isolation). Unit and component tests
  stay Docker-free.
- **Proposed:** Testcontainers for persistence and messaging integration tests. It is **not
  declared in the POM** (its version is Boot-managed, as listed in ADR 0002). It is evaluated when
  the first integration test needs it, because it makes those tests require Docker.
- **Open (before the related implementation):** the coverage tool (JaCoCo, for example, is not
  managed by Spring Boot, so it would need a change to ADR 0002's version policy); how
  integration tests run while the default `clean verify` stays Docker-free; frontend test tooling,
  end-to-end tests, and running integration tests in CI.

## 13. Deployment and observability

- **Required:** containerized deployment of frontend, backend, database and message broker
  (SYS-6); Dockerize all services and use docker-compose for local orchestration (IMP-7).
- **Implemented:** Docker Compose runs PostgreSQL and RabbitMQ only; the frontend and backend run on
  the developer machine. Spring Boot Actuator health includes database and RabbitMQ checks.
- **Decided ([ADR 0004](adr/0004-messaging-and-integration-reliability.md)):** messaging
  metrics through Micrometer (outbox backlog, consumer failures, pending and stuck parcels, dead
  letters, webhook outcomes) and log lines without personal data or secrets. Not implemented yet.
- **Open:** Dockerfiles and Compose services for the frontend and backend; production deployment,
  hosting, logging and monitoring beyond Actuator.
- **Decided:** Kubernetes is excluded by the project rules and was rejected in
  [ADR 0001](adr/0001-modular-monolith-in-a-monorepo.md)'s alternatives. The brief offers
  Kubernetes only as an optional bonus (BON-1); pursuing it needs
  a separate, explicitly reviewed ADR. The AI assistant bonus (BON-2) likewise needs its own
  decision.

## 14. Open questions

| Question | Section |
|---|---|
| Frontend-to-backend calling pattern (BFF or not), CORS, error format | 5 |
| Tracking-number format | 6 |
| Open messaging implementation questions ([ADR 0004](adr/0004-messaging-and-integration-reliability.md)) | 7 |
| Unresolved lifecycle rules, public tracking content, non-public SSE authentication | 8 |
| Account linking to existing accounts, merchant API authentication, creation of courier and admin accounts | 9 |
| Personal-data retention, proof-of-delivery photo storage, receipt format, exposure of springdoc/Actuator | 10 |
| Spring profiles and non-local secret handling | 11 |
| Coverage tool, integration-test setup, frontend tests, CI | 12 |
| Application Dockerfiles and Compose services; production deployment; optional bonuses | 13 |
| Report definitions, "deliveries for the day", fee formula and zones ([not specified by the brief](project-requirements.md#not-specified-by-the-brief)) | - |

## Related documents

- [Project requirements](project-requirements.md)
- [ADR 0001: Modular monolith in a monorepo](adr/0001-modular-monolith-in-a-monorepo.md)
- [ADR 0002: Technology and version baseline](adr/0002-technology-and-version-baseline.md)
- [ADR 0003: Backend business modules and boundaries](adr/0003-backend-modules-and-boundaries.md)
- [ADR 0004: Messaging and integration reliability](adr/0004-messaging-and-integration-reliability.md)
- [AI agent guidelines](ai-agent-guidelines.md) and [AGENTS.md](../AGENTS.md)
- [Contributing and task workflow](../CONTRIBUTING.md)
- [Development setup](development-setup.md)
