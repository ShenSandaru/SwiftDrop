# 0003. Backend business modules and boundaries

- Status: Accepted
- Date: 2026-10-25

## Context

[ADR 0001](0001-modular-monolith-in-a-monorepo.md) chose a modular monolith with explicit module
APIs and left the business-module layout open. The backend contains no business code yet. Ten
developers will work in parallel, so ownership and boundaries are needed before the first feature.

The requirements come from the official assignment brief, summarized in
[docs/project-requirements.md](../project-requirements.md). Relevant to this decision, the brief
requires:

- Four roles: Customer, Merchant, Courier, Dispatcher/Admin (SYS-2).
- Customer signup and login including Google login, and JWT-based authentication (CUS-1, SYS-3).
- Delivery requests with pickup and drop-off addresses and parcel size/weight, a fee estimate
  before confirming, history, downloadable receipts and a public tracking page (CUS-2 to CUS-6).
- The status sequence Created → Picked Up → In Transit → Out for Delivery → Delivered / Failed
  (CUS-4), courier status updates with notes or proof and failure reasons (COU-3, COU-4).
- Assignment of unassigned parcels and courier workload monitoring (ADM-1, ADM-2); management of
  users, merchants, zones and pricing (ADM-3); reports of deliveries per day and success/failure
  rate (ADM-4).
- Merchant registration with admin approval, API orders, a dashboard and status webhooks with
  retry on failure (MER-1 to MER-4, IMP-4).
- Asynchronous status events that trigger a notification consumer and merchant webhooks, and
  real-time updates (SYS-1, SYS-4, SYS-5).

The brief does not prescribe a module list, a database schema strategy or a messaging reliability
mechanism; those are the team's choices below.

## Decision

### 1. Modules

| Module | Responsibility | Owns (tables) | Must not own |
|---|---|---|---|
| `identity` | Signup and login (hashed passwords), Google login, JWT issuance and validation, roles, user management | users, roles, user–role links, external identity links | merchant approval, courier operational data |
| `pricing` | Fee calculation, zones, pricing configuration | zones, pricing rules | parcel data, fees stored on parcels |
| `delivery` | Parcels, status lifecycle and status history, current courier assignment record, notes, proof, failure reasons, receipts, tracking-number lookup, the two required reports, courier workload counts | parcels, status history, proof records | courier profiles, merchant credentials or webhooks, sending notifications, live connections, pricing rules |
| `dispatch` | Courier profiles; the assignment workflow, including courier eligibility checks; the workload view | couriers | parcel status, parcel tables |
| `merchant` | Registration and approval, API credentials, order intake, dashboard queries, webhook delivery records and retry handling | merchants, API credentials, webhook endpoints, webhook delivery records | the parcel lifecycle, copies of parcel history, user passwords |
| `notification` | Notifications triggered by status events | notification messages | business decisions, status changes |
| `tracking` | Live SSE connections; sends the current state from `delivery` on connect, then pushes changes | nothing persistent | persisted history, status changes |

The main delivery aggregate and entity is named `Parcel`, matching the brief's ER list (DES-2); it
belongs to the `delivery` module, which keeps its name.

There is no `reporting` module. The required reports and the workload counts use data owned by
`delivery`. A `reporting` module can be proposed later if a required report needs data owned by
several modules.

**Assignment boundary:** `dispatch` decides an assignment and validates that the courier is
eligible; `delivery` enforces and records it on the parcel. Neither uses the other's repositories
or entities.

### 2. Dependencies

Allowed calls, only into the target module's `api` package:

```text
identity     → none
pricing      → none
delivery     → identity, pricing
dispatch     → identity, delivery
merchant     → identity, delivery
notification → identity, delivery
tracking     → delivery
```

Every other dependency between modules is forbidden. The graph is acyclic, no module depends on
`notification` or `tracking`, and `delivery` never calls `dispatch`, `merchant`, `notification` or
`tracking`.

### 3. Packages and public contracts

- Each module is `com.swiftdrop.<module>`, with only the sub-packages it needs: `api` (the only
  package other modules may import: a facade interface, records, event records), `web` (REST
  controllers; the SSE endpoint in `tracking`), `application` (use cases and `@Transactional`
  boundaries), `domain` (entities, rules, repositories) and `infrastructure` (RabbitMQ, outbound
  HTTP, external providers).
- A module never imports another module's entities, repositories, controllers or implementation
  packages.
- `com.swiftdrop.config` is the composition root (for example the security filter chain that
  combines user and merchant authentication). It may use module APIs; no module imports it.
- `com.swiftdrop.shared` contains technical code only, such as the centralized error format. No
  business types.
- **Implementation requirement:** a dependency-free JUnit test in the normal build checks the
  allowed graph and the `api`-only rule at package level. It is written with the first module code,
  not by this ADR.

### 4. Delivery lifecycle

`delivery` alone changes a `Parcel`'s status and records status history. The statuses are exactly those
of the brief: `CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED / FAILED`.
Courier assignment is a separate field on the parcel, not a status, and does not emit a status
event.

Initial transition rules (a team decision; the brief does not state them):

1. Ordinary transitions move forward one step at a time.
2. `DELIVERED` and `FAILED` are terminal in the initial workflow.
3. Only the assigned courier may make ordinary post-creation status updates.
4. A courier must be assigned before `PICKED_UP`.
5. Each status change records its time and actor; `FAILED` requires a failure reason; notes and
   proof are recorded as applicable.

### 5. Data

- One PostgreSQL database and the shared `public` schema. This is a pragmatic initial choice for
  the team, not a universal best practice.
- Each table has exactly one owning module, recorded in
  [docs/architecture.md](../architecture.md#4-backend-modular-monolith). Only the owning module writes migrations
  for its tables and accesses them.
- Cross-module references use IDs, not JPA associations. Cross-module foreign keys may be used when
  justified by referential integrity; there are no cascading deletes across modules.
- Migrations are organized per module (for example `db/migration/<module>/`) with versions that do
  not collide between branches (for example timestamps). Flyway's discovery of module folders is
  verified before the first business migration is accepted.
- A shared schema gives no access isolation, and neither would separate schemas while the backend
  uses one privileged database account. Ownership is enforced by the module rules, migration
  ownership, review and the boundary test.

### 6. Events

- `delivery` is the only publisher of delivery status-change events. `notification` and
  `merchant` react to them through RabbitMQ; `tracking` may receive them in-process.
- Side effects never block or roll back the status change.
- Publishing after the database commit does **not** guarantee delivery: a crash or broker outage
  between the commit and the publish loses the event. Event contracts, RabbitMQ topology, the
  delivery guarantee (for example a transactional outbox), idempotency, retry policies,
  dead-lettering, webhook signing and failure recovery are decided in a separate ADR 0004 before
  the first messaging feature.

## Alternatives considered

- **The earlier eight-module list including `reporting`:** the required reports need only
  delivery data, so the module would only forward calls.
- **Layer-first packages (`controller`, `service`, `repository`):** no module boundaries.
- **One module per entity:** too many contracts for the team.
- **A separate webhook module:** duplicates merchant data and relationships.
- **Tracking inside `delivery`:** viable, but mixes connection handling into the core module.
- **A shared business `common` package:** becomes a coupling point.
- **Spring Modulith or ArchUnit for boundary checks now:** not managed by Spring Boot, so a pinned
  version and a change to [ADR 0002](0002-technology-and-version-baseline.md)'s version policy
  would be needed.
- **One Maven module per business module:** stronger compile-time boundaries, but more build
  complexity than the team needs now.
- **One PostgreSQL schema per module:** ownership visible in table names, but extra JPA mapping,
  `CREATE SCHEMA` permissions and no real access isolation while one database account is used.
- **Microservices:** excluded by ADR 0001.

## Consequences

- Module owners can work in parallel against small contracts. Changing a module's `api` requires
  review by its owner; changing the allowed dependencies requires a new ADR.
- Some reads go through `api` methods instead of database joins.
- With one schema and one database account, ownership relies on conventions, review and the
  boundary test, not on database permissions.
- Until ADR 0004 is accepted, no messaging feature may assume that events are never lost.

### Unresolved decisions

Not decided by this ADR:

- Lifecycle: the stages from which `FAILED` is allowed, reassignment, cancellation, retries after
  failure, dispatcher/admin overrides, and whether proof is mandatory for `DELIVERED`.
- UUID or numeric IDs, and the tracking-number format.
- Google login flow (and whether the OAuth2 client starter stays), merchant API authentication
  (API keys or OAuth 2.0 client credentials), and SSE authentication for non-public streams.
- Receipt format and photo storage.
- All messaging decisions listed for ADR 0004.
- Testing tools for measurable coverage and integration tests (none is added by this ADR).
