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
- **Messaging:** RabbitMQ (via Spring AMQP) for asynchronous events, notifications and merchant
  webhook retries (planned; only the connection is configured).
- **Real-time tracking:** Server-Sent Events (SSE) (planned; not implemented).
- **Local infrastructure:** Docker Compose.

See [ADR 0001](docs/adr/0001-modular-monolith-in-a-monorepo.md) for the reasoning and
[docs/architecture.md](docs/architecture.md) for what is implemented, decided, proposed and open.

## Technology baseline

| Area | Choice |
|---|---|
| Backend | Java 21 (Temurin LTS), Spring Boot 4.1.x, Maven (via Maven Wrapper) |
| Persistence | Spring Data JPA, Hibernate ORM 7, Flyway, PostgreSQL 18 |
| Security | Spring Security 7 (default configuration). Planned, not yet configured: Google OAuth 2.0 / OpenID Connect sign-in and a JWT resource server |
| Messaging | Spring AMQP, RabbitMQ 4.3 |
| API docs | springdoc-openapi 3.2.0 |
| Backend tests | JUnit Jupiter, Mockito, AssertJ, Spring Boot Test (Boot-managed). Testcontainers is not declared; it is a candidate for future integration tests |
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
│   ├── architecture.md   Architecture and implementation status
│   ├── ai-agent-guidelines.md  Rules for AI agents and project rules for everyone
│   └── development-setup.md
├── compose.yaml          Local PostgreSQL and RabbitMQ (created in Stage 5)
├── .env.example          Template for the git-ignored local .env file
├── AGENTS.md             Entry point for AI coding agents
├── CLAUDE.md             Claude Code entry point (imports AGENTS.md and the guidelines)
└── CONTRIBUTING.md       Task workflow and team conventions
```

The setup stages are complete up to the local infrastructure. No business features exist yet.

## Getting started

1. Install the prerequisites in [docs/development-setup.md](docs/development-setup.md).
2. Read [CONTRIBUTING.md](CONTRIBUTING.md) and the
   [project rules](docs/ai-agent-guidelines.md#project-rules). AI agents start at
   [AGENTS.md](AGENTS.md).
3. Start the local infrastructure (from the repository root):

   ```powershell
   Copy-Item .env.example .env   # once; then change the passwords in .env
   docker compose up -d
   docker compose ps             # wait until both services are "healthy"
   ```

4. Build and test the backend: `cd backend`, then `.\mvnw.cmd clean verify`. The tests do not need
   the containers. Run the backend against them with `.\mvnw.cmd spring-boot:run`.
5. Frontend: see [frontend/README.md](frontend/README.md).

Details, troubleshooting and how to reset data are in
[docs/development-setup.md](docs/development-setup.md#6-local-infrastructure-postgresql-and-rabbitmq).

## Documentation

- [Architecture](docs/architecture.md)
- [Development setup (Windows)](docs/development-setup.md)
- [Contributing guide and task workflow](CONTRIBUTING.md)
- [AI agent guidelines and project rules](docs/ai-agent-guidelines.md)
- [Agent entry point](AGENTS.md) and [Claude Code entry point](CLAUDE.md)
- [Architecture decision records](docs/adr/README.md)
