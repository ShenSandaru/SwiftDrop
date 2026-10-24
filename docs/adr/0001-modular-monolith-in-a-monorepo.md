# 0001. Modular monolith in a monorepo

- Status: Accepted
- Date: 2026-10-24

## Context

SwiftDrop is a university project built by 10 students. It must support four user groups
(customers, merchants, couriers, dispatchers/administrators), role-based authorization, secure
REST APIs, real-time parcel tracking, asynchronous events, notifications, merchant webhooks with
retries, automated tests and containerized local development.

The team has limited time and operational experience. The architecture must be easy to run locally,
easy to test, and still show clear separation of concerns.

## Decision

1. **Single Git monorepo** containing `frontend/`, `backend/`, `docs/` and the Docker Compose setup.
2. **Backend: one Spring Boot application structured as a modular monolith.** The code is split
   into business modules with explicit public APIs. The concrete module list and boundary rules are
   decided in Stage 5 and recorded in a separate ADR.
3. **Frontend: a Next.js App Router application** responsible only for presentation. It calls the
   backend REST API. Business rules, authorization, persistence and integrations belong to the
   backend.
4. **PostgreSQL** is the single database. **Flyway** owns all schema changes.
5. **RabbitMQ** (via Spring AMQP) carries asynchronous work: domain events, notifications and
   merchant webhook delivery with retries.
6. **Server-Sent Events (SSE)** deliver real-time tracking updates from backend to browser. SSE is
   one-directional (server to client), which matches tracking; status updates from couriers use
   normal REST calls.
7. **Docker Compose** runs local infrastructure (PostgreSQL, RabbitMQ).
8. **Authorization is enforced on the backend** for every protected operation, regardless of what
   the frontend shows.

## Alternatives considered

- **Microservices:** separate deployments, network calls, distributed data and much more
  infrastructure. The operational cost is out of proportion for a 10-person student project.
- **Separate repositories for frontend and backend:** harder to keep API changes, documentation and
  local setup in sync.
- **WebSockets for tracking:** bidirectional, which the requirement does not need. More complex
  to secure and scale than SSE. Can be reconsidered by a new ADR if a verified requirement needs it.
- **Business logic in Next.js server code:** would split rules and authorization between two
  runtimes. Rejected; Next.js stays a presentation layer.
- **Kubernetes, Redis or a second message broker:** no current requirement justifies them.

## Consequences

- One backend build, one deployment and one database keep local development and testing simple.
- Module boundaries are enforced by package structure and code review rather than by the network,
  so the team must respect them deliberately.
- Real-time updates and asynchronous work are contained in one process plus RabbitMQ, which is
  sufficient for the expected load of a coursework system.

## Open questions (to resolve in later stages)

- How the browser authenticates the SSE stream. The native `EventSource` API cannot send an
  `Authorization` header, so a cookie-based session token or a short-lived stream token is needed.
- How Google sign-in maps to SwiftDrop accounts and roles, and how the backend issues its own JWTs
  after a successful login.
- Merchant API authentication (API keys versus OAuth 2.0 client credentials) and webhook signing.
