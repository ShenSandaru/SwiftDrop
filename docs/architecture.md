# SwiftDrop architecture

This is the main description of SwiftDrop's architecture: what exists, what is decided, and what is
still open. Decisions and their rationale live in the [ADRs](adr/README.md); versions live in
[ADR 0002](adr/0002-technology-and-version-baseline.md) and the package manifests. This document
links to them instead of repeating them.

## Status labels

| Label | Meaning |
|---|---|
| **Implemented** | Verified in the current code or configuration |
| **Decided** | Accepted in an ADR, or a binding rule in [docs/ai-agent-guidelines.md](ai-agent-guidelines.md), but not necessarily implemented |
| **Proposed** | Recommended, awaiting approval. Do not implement without an approved task. |
| **Open** | Unresolved; needs a decision |

When a change implements, decides or rejects something, update its label in the same change.

## 1. Purpose and boundaries

SwiftDrop is a parcel delivery and courier management system, built as a university Enterprise
Application Development group project. The intended user groups are customers, merchants, couriers
and dispatchers/administrators (see the role table in the [README](../README.md#user-groups)).
A written requirements document does not exist yet; the role table is the current source.

- **Implemented:** project scaffolding only: a frontend placeholder page, a backend with no business
  code, and local PostgreSQL and RabbitMQ containers. **No business feature exists yet.**
- **Decided:** SwiftDrop is one system with one backend and one database. Merchant webhooks are
  delivered by the backend ([ADR 0001](adr/0001-modular-monolith-in-a-monorepo.md)). Merchant API
  access, Google sign-in and notifications are planned capabilities whose designs are still open
  (sections 7 and 9).
- **Open:** production hosting, external services beyond Google sign-in (for example email or SMS
  providers for notifications), and data retention.

## 2. Technology stack

| Area | Technology | Status |
|---|---|---|
| Frontend | Next.js App Router, React, TypeScript, Tailwind CSS, shadcn/ui on Base UI, npm | Implemented |
| Backend | Java 21, Spring Boot 4.1 (Spring MVC), Maven Wrapper | Implemented |
| Persistence | PostgreSQL 18, Spring Data JPA / Hibernate, Flyway | Implemented (connection only) |
| Messaging | RabbitMQ 4.3, Spring AMQP | Implemented (connection only) |
| Security | Spring Security; OAuth2 client and resource-server starters | Implemented (defaults only, see section 9) |
| API docs | springdoc-openapi | Implemented (default endpoints) |
| Local infrastructure | Docker Compose (`compose.yaml`) | Implemented |

Exact versions and compatibility evidence, including why Jackson 2 and Jackson 3 coexist, are in
[ADR 0002](adr/0002-technology-and-version-baseline.md).

## 3. Repository layout

**Implemented.** One Git monorepo ([ADR 0001](adr/0001-modular-monolith-in-a-monorepo.md)):

| Path | Contents |
|---|---|
| `frontend/` | Next.js application. Separate build (npm). |
| `backend/` | Spring Boot application, package root `com.swiftdrop`. Separate build (Maven Wrapper). |
| `compose.yaml`, `.env.example` | Local PostgreSQL and RabbitMQ; template for the git-ignored `.env` |
| `docs/` | This document, the agent guidelines, the setup guide and the ADRs |

The frontend and backend share no code. They communicate only over HTTP.

## 4. Backend: modular monolith

- **Decided:** the backend is one Spring Boot application, structured as a modular monolith. No
  microservices, service mesh, Kubernetes, Redis or second message broker
  ([ADR 0001](adr/0001-modular-monolith-in-a-monorepo.md)).
- **Decided:** each business module exposes a small public API. Other modules use only that API and
  never reach into another module's internal classes, entities or repositories.
- **Implemented:** only the application class `com.swiftdrop.SwiftdropBackendApplication` exists.
  There are no modules yet.
- **Open:** the module list and the exact boundary rules. They will be set by **ADR 0003**, which
  has not been written. Until ADR 0003 is accepted, the table below is only a proposal.

### Proposed business modules (pending ADR 0003)

**Proposed.** This table does **not** authorize implementing these modules or their features. It is
derived from the README role table and is the starting point for ADR 0003.

| Module | Responsibility | May depend on |
|---|---|---|
| `identity` | User accounts, roles, sign-in, issuing and validating the application's tokens | none |
| `pricing` | Fee estimates, pricing rules, delivery zones | none |
| `delivery` | Delivery requests, parcels, status lifecycle, history, proof of delivery, receipts | `identity`, `pricing` |
| `dispatch` | Couriers and the assignment of parcels to couriers | `identity`, `delivery` |
| `merchant` | Merchant accounts, merchant API credentials, order intake, webhook subscriptions and delivery | `identity`, `delivery` |
| `notification` | Notifications to users, triggered by events | events from other modules only |
| `tracking` | Real-time tracking streams (SSE) | events from `delivery` only |
| `reporting` | Read-only reports for administrators | public APIs of other modules (read only) |

Proposed dependency rules:

- Dependencies point one way and have no cycles. A module that is depended on never calls back
  into its dependants; it publishes an event instead.
- `notification` and `tracking` react to events; no module depends on them.
- Cross-cutting code (security configuration, error handling) stays small and holds no business
  rules.
- Each module owns its tables. Other modules do not read or write them directly.

**Open:** whether to verify these boundaries with a tool (for example Spring Modulith tests) or by
review only. A tool would be a new dependency and needs approval.

## 5. Frontend and API boundary

- **Decided:** the frontend only presents data and calls the backend REST API. Business rules,
  validation that matters, authorization, persistence and integrations live in the backend. Hiding
  a page or button is never a security control ([ADR 0001](adr/0001-modular-monolith-in-a-monorepo.md)).
- **Decided (project rule):** REST paths are plural nouns in kebab-case under `/api/v1/...`.
- **Implemented:** the frontend has a placeholder page and one shadcn/ui component. It does not call
  the backend yet. The backend has no business endpoints; springdoc serves its default OpenAPI
  endpoints (behind authentication, see section 9).
- **Open:** how the frontend calls the backend (directly from the browser, or through Next.js
  server code), CORS, the API error format and pagination conventions.

## 6. Database

- **Decided:** PostgreSQL is the single database. Flyway owns every schema change through versioned
  migrations (`V<n>__<description>.sql`, `snake_case` names). Merged migrations are never edited.
  Hibernate only validates the schema.
- **Implemented:** the backend connects to PostgreSQL through `SWIFTDROP_POSTGRES_*` settings,
  `spring.jpa.hibernate.ddl-auto=validate` is set, and Flyway runs at startup. There are **no
  migrations and no entities**. On first start, Flyway creates only its own
  `flyway_schema_history` table.
- **Proposed:** table ownership per module (section 4).

## 7. Messaging (RabbitMQ)

- **Decided:** RabbitMQ through Spring AMQP carries asynchronous work: domain events,
  notifications and merchant webhook delivery with retries. Classic or quorum queues only; classic
  mirrored queues were removed in RabbitMQ 4.
- **Implemented:** connection settings (`SWIFTDROP_RABBITMQ_*`) and the Actuator health check
  only. **No exchanges, queues, producers or consumers exist.** A connection smoke test against
  RabbitMQ 4.3 succeeded in Stage 5.
- **Open:** exchange and queue naming, the event format, the retry and dead-letter policy, and how
  database changes and published events stay consistent (for example a transactional outbox).

## 8. Real-time tracking (SSE)

- **Decided:** tracking updates go from backend to browser with Server-Sent Events (Spring MVC
  `SseEmitter`). Courier status updates use normal REST calls. WebSockets need a new ADR.
- **Implemented:** nothing.
- **Open:** how the browser authenticates the stream. The native `EventSource` API cannot send an
  `Authorization` header, so a cookie or a short-lived stream token is needed
  ([ADR 0001](adr/0001-modular-monolith-in-a-monorepo.md) open questions).

## 9. Authentication and authorization

- **Decided:** authorization is enforced on the backend for every protected operation, by role and
  by ownership ([ADR 0001](adr/0001-modular-monolith-in-a-monorepo.md)).
- **Implemented:** Spring Security with its **default configuration**. Every endpoint except
  `/actuator/health` requires authentication. No users exist and no login is possible.
  The OAuth2 client and resource-server starters are **installed but not configured**: there is no
  Google client registration, no JWT issuer or key configuration, and no roles.
- **Open:**
  - The sign-in flow. The planned direction is Google OAuth 2.0 / OpenID Connect sign-in, after
    which the backend issues its own JWTs and validates them as a resource server (README stack
    table, ADR 0001 open questions), but no ADR has decided it. This includes how Google accounts
    map to SwiftDrop accounts and roles. Whether the OAuth2 client starter stays depends on it.
  - Merchant API authentication (API keys or OAuth 2.0 client credentials) and webhook signing.
  - How roles are assigned (for example who creates courier and administrator accounts).

## 10. Security and personal data

**Decided (project rules):**

- Validate all external input on the backend. Return safe error messages without stack traces.
- Merchant API credentials and webhook secrets are stored hashed or encrypted, never in plain text.
- Never commit secrets, credentials, tokens or personal data.
- Keep personal data (names, addresses, phone numbers, delivery locations, proof-of-delivery data)
  and secrets out of logs, and expose personal data only to users entitled to it.

**Open:** retention and deletion of personal data, storage for proof-of-delivery files, and whether
springdoc and Actuator details are exposed outside local development.

## 11. Configuration and secrets

- **Implemented:**
  - Local settings live in the git-ignored root `.env`, created from the committed `.env.example`.
    Docker Compose reads it, and the backend imports it when started from `backend/`
    (`spring.config.import`). Environment variables take precedence.
  - Passwords have no defaults in `compose.yaml` or `application.properties`.
  - Local service ports are published on `127.0.0.1` only.

  Details are in [docs/development-setup.md](development-setup.md#6-local-infrastructure-postgresql-and-rabbitmq).
- **Decided (project rule):** configuration is environment-specific; secrets come from the
  environment, never from committed files.
- **Open:** Spring profiles (none exist yet) and secret handling outside local development.

## 12. Testing

- **Implemented:**
  - One context-load test (`SwiftdropBackendApplicationTests`) that runs without Docker, PostgreSQL
    or RabbitMQ.
  - The Boot-managed JUnit Jupiter, Mockito, AssertJ and Spring test starters are declared.
  - `.\mvnw.cmd clean verify` must pass without Docker.
  - The frontend has lint and type checking (`npm run lint`, `npm run build`) and no tests.
- **Decided (project rules):** new business logic comes with tests; logic is unit-tested with
  Mockito; H2 is not used as a PostgreSQL substitute.
- **Proposed:** Testcontainers for persistence and messaging integration tests. It is **not
  declared in the POM** (its version is Boot-managed, as listed in ADR 0002). It is evaluated when
  the first integration test needs it, because it makes those tests require Docker.
- **Open:** frontend test tooling, end-to-end tests and continuous integration (there is no CI
  configuration).

## 13. Deployment and observability

- **Implemented:** local development only (Docker Compose for infrastructure; frontend and backend
  run on the developer machine). Spring Boot Actuator health includes database and RabbitMQ
  checks.
- **Open:** production deployment, hosting, container images for the applications, logging and
  monitoring beyond Actuator. Kubernetes is excluded by [ADR 0001](adr/0001-modular-monolith-in-a-monorepo.md).

## 14. Open questions

| Question | Section |
|---|---|
| Business-module list and boundary rules (ADR 0003) | 4 |
| Tool-based boundary verification | 4 |
| Frontend-to-backend calling pattern, CORS, error format | 5 |
| RabbitMQ topology, event format, retries, outbox | 7 |
| SSE stream authentication | 8 |
| Google sign-in mapping, token issuance, role assignment | 9 |
| Merchant API authentication and webhook signing | 9 |
| Personal-data retention, proof-of-delivery storage, exposure of springdoc/Actuator | 10 |
| Spring profiles and non-local secret handling | 11 |
| Frontend tests, end-to-end tests, CI | 12 |
| Production deployment and monitoring | 13 |

## Related documents

- [ADR 0001: Modular monolith in a monorepo](adr/0001-modular-monolith-in-a-monorepo.md)
- [ADR 0002: Technology and version baseline](adr/0002-technology-and-version-baseline.md)
- [AI agent guidelines](ai-agent-guidelines.md) and [AGENTS.md](../AGENTS.md)
- [Contributing and task workflow](../CONTRIBUTING.md)
- [Development setup](development-setup.md)
