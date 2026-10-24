# SwiftDrop

SwiftDrop is a parcel delivery and courier management system, built as a university
Enterprise Application Development project by a team of 10 students.

## User groups

| Role | Main capabilities |
|---|---|
| Customer | Create delivery requests, estimate fees, track parcels, view delivery history and receipts |
| Merchant | Create parcel orders through a secured API, monitor orders, receive webhook events |
| Courier | View assigned deliveries, update delivery status with proof or failure reasons |
| Dispatcher / Administrator | Assign parcels; manage users, couriers, pricing and delivery zones; view reports |

## Architecture at a glance

- **Single Git monorepo** containing the frontend, backend, documentation and local infrastructure.
- **Backend:** Spring Boot **modular monolith** (not microservices). It owns all business rules,
  authorization, persistence and integrations.
- **Frontend:** Next.js App Router. It handles presentation only and calls the backend REST API.
- **Database:** PostgreSQL, with the schema managed exclusively by Flyway migrations.
- **Messaging:** RabbitMQ (via Spring AMQP) for asynchronous events, notifications and merchant webhook retries.
- **Real-time tracking:** Server-Sent Events (SSE).
- **Local infrastructure:** Docker Compose.

See [ADR 0001](docs/adr/0001-modular-monolith-in-a-monorepo.md) for the reasoning.

## Technology baseline

| Area | Choice |
|---|---|
| Backend | Java 21 (Temurin LTS), Spring Boot 4.1.x, Maven (via Maven Wrapper) |
| Persistence | Spring Data JPA, Hibernate ORM 7, Flyway, PostgreSQL 18 |
| Security | Spring Security 7, JWT resource server, Google OAuth 2.0 / OpenID Connect |
| Messaging | Spring AMQP, RabbitMQ 4.3 |
| API docs | springdoc-openapi 3.2.0 |
| Backend tests | JUnit Jupiter, Mockito, AssertJ, Spring Boot Test, Testcontainers (all Boot-managed) |
| Frontend | Node.js 24 LTS, npm, Next.js 16.4.x, React 19, TypeScript 6.0.x, Tailwind CSS 4, shadcn/ui |

Exact versions and the evidence for them are in
[ADR 0002](docs/adr/0002-technology-and-version-baseline.md).

## Repository layout

```text
.
├── backend/              Spring Boot application (created in Stage 4)
├── frontend/             Next.js application (created in Stage 3)
├── docs/
│   ├── adr/              Architecture decision records
│   └── development-setup.md
├── docker-compose.yml    Local PostgreSQL and RabbitMQ (created in Stage 6)
├── CLAUDE.md             Coding instructions for contributors and AI assistants
└── CONTRIBUTING.md       Team workflow
```

Only the documentation and repository configuration exist so far. The applications and
infrastructure are added in later setup stages.

## Getting started

1. Install the prerequisites in [docs/development-setup.md](docs/development-setup.md).
2. Read [CONTRIBUTING.md](CONTRIBUTING.md) and [CLAUDE.md](CLAUDE.md).
3. Build and run instructions will be added here once the frontend, backend and Docker Compose
   setup exist.

## Documentation

- [Development setup (Windows)](docs/development-setup.md)
- [Contributing guide](CONTRIBUTING.md)
- [Coding instructions](CLAUDE.md)
- [Architecture decision records](docs/adr/README.md)
