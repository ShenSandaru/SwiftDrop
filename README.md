# SwiftDrop

SwiftDrop is a parcel delivery and courier management system, built as a university
Enterprise Application Development project by a team of 10 students. The assignment requirements
are summarized in [docs/project-requirements.md](docs/project-requirements.md).

## User groups

Required capabilities per role, from the assignment. **None of them is implemented yet.**

| Role | Required capabilities |
|---|---|
| Customer | Sign up and log in (including Google login), create delivery requests, see a fee estimate before confirming, track parcels in real time, view history and download receipts; anyone can track by public tracking number |
| Merchant | Register (with admin approval), create delivery orders through a secured API, receive status webhooks, view a dashboard of orders |
| Courier | Log in, view the day's assigned deliveries, update status with a note or proof, mark failed deliveries with a reason |
| Dispatcher/Admin | Assign unassigned parcels, monitor courier workloads, manage users, merchants, zones and pricing, view reports (deliveries per day, success/failure rate) |

## Architecture at a glance

- **Single Git monorepo** containing the frontend, backend, documentation and local infrastructure.
- **Backend:** Spring Boot **modular monolith** (not microservices). It owns all business rules,
  authorization, persistence and integrations. The business modules are decided in
  [ADR 0003](docs/adr/0003-backend-modules-and-boundaries.md); none of them is implemented yet.
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
├── .github/workflows/    GitHub Actions CI
├── backend/              Spring Boot application (created in Stage 4)
├── frontend/             Next.js application (created in Stage 3)
├── docs/
│   ├── adr/              Architecture decision records
│   ├── architecture.md   Architecture and implementation status
│   ├── project-requirements.md  Summary of the assignment requirements
│   ├── ai-agent-guidelines.md  Rules for AI agents and project rules for everyone
│   └── development-setup.md
├── compose.yaml          Local PostgreSQL and RabbitMQ (created in Stage 5)
├── .env.example          Template for the git-ignored local .env file
├── AGENTS.md             Entry point for AI coding agents
├── CLAUDE.md             Claude Code entry point (imports AGENTS.md and the guidelines)
└── CONTRIBUTING.md       Task workflow and team conventions
```

Each application has its own README: [backend/README.md](backend/README.md) and
[frontend/README.md](frontend/README.md).

## Current status

**Works today:** the project skeleton. The frontend builds and serves a placeholder start page, the
backend starts and connects to PostgreSQL and RabbitMQ, Flyway runs (no migrations yet), the
Actuator health endpoint reports both services, and CI runs lint, build and tests.

**Not implemented yet:** every business feature, including accounts and login, roles, parcels and
delivery requests, pricing, tracking, courier assignment, notifications, merchant API and webhooks,
and reports. Spring Security runs with its default configuration, so every endpoint except
`/actuator/health` requires authentication and no login is possible.

## Getting started

### 1. Prerequisites

| Tool | Version | Source of truth |
|---|---|---|
| Eclipse Temurin JDK | 21 | `java.version` in `backend/pom.xml` |
| Node.js / npm | 24 LTS / npm 11 | `.nvmrc`, `engines` in `frontend/package.json` |
| Docker Desktop (Compose) | current 4.x | only needed to run the backend locally |
| Git | any current | |

Maven is not installed separately; the Maven Wrapper pins it. Installation steps (Windows) and a
version check are in [docs/development-setup.md](docs/development-setup.md).

### 2. Clone and configure

```powershell
git clone <repository-url>
cd SwiftDrop
Copy-Item .env.example .env      # Linux/macOS: cp .env.example .env
```

Edit `.env` and replace the two `change-me-...` passwords with your own local values. `.env` is
git-ignored; never commit it. It is read by Docker Compose and by the backend:

| Variable | Default in `.env.example` | Purpose |
|---|---|---|
| `SWIFTDROP_POSTGRES_DB` / `_USER` | `swiftdrop` | Database name and user |
| `SWIFTDROP_POSTGRES_PASSWORD` | `change-me-local-postgres` | Set your own; required |
| `SWIFTDROP_POSTGRES_PORT` | `5432` | Host port; change if 5432 is taken |
| `SWIFTDROP_RABBITMQ_USER` | `swiftdrop` | Broker user |
| `SWIFTDROP_RABBITMQ_PASSWORD` | `change-me-local-rabbitmq` | Set your own; required |
| `SWIFTDROP_RABBITMQ_PORT` / `_MANAGEMENT_PORT` | `5672` / `15672` | Host ports |

Credentials are applied only when the Docker volumes are first created; see
[resetting data](docs/development-setup.md#reset-data-deletes-everything).

### 3. Start and stop the local infrastructure (needs Docker)

From the repository root:

```powershell
docker compose up -d     # start PostgreSQL and RabbitMQ
docker compose ps        # wait until both show "(healthy)"
docker compose stop      # stop when done; data is kept
```

### 4. Run the applications

```powershell
cd backend
.\mvnw.cmd spring-boot:run       # Linux/macOS: ./mvnw spring-boot:run (needs step 3)
```

```bash
cd frontend
npm ci                           # first time and after package-lock.json changes
npm run dev
```

| What | URL |
|---|---|
| Frontend (dev server) | <http://localhost:3000> |
| Backend health | <http://localhost:8080/actuator/health> (`{"status":"UP"}` when both services are reachable) |
| RabbitMQ management UI | <http://localhost:15672> (user and password from `.env`) |

The backend API under `/api/v1/...` and the springdoc API docs (`/swagger-ui.html`,
`/v3/api-docs`) do not exist in usable form yet: there are no endpoints, and the default security
configuration requires authentication for them.

### 5. Checks (no Docker needed)

These are the same checks CI runs:

```powershell
cd frontend; npm ci; npm run lint; npm run build
cd ../backend; .\mvnw.cmd clean verify       # Linux/macOS: ./mvnw clean verify
```

### Troubleshooting

- **`java -version` is not 21:** `JAVA_HOME` or `PATH` points to another JDK.
- **Port already allocated:** another service uses 5432, 5672 or 15672. Do not stop other projects'
  containers; set a free port in `.env` (e.g. `SWIFTDROP_POSTGRES_PORT=5433`) and run
  `docker compose up -d` again.
- **`required variable SWIFTDROP_..._PASSWORD is missing a value`:** `.env` is missing; create it
  from `.env.example`.
- **`password authentication failed`:** the volume was created with other credentials than the
  current `.env`.
- **Backend cannot find its settings when started from an IDE:** set the working directory to
  `backend/`.

More cases are in [development setup](docs/development-setup.md#troubleshooting) and
[frontend/README.md](frontend/README.md#known-npm-warnings).

## Git workflow

1. `git switch main`, `git pull`, then `git switch -c feature/<short-description>`.
2. Make a small change within the approved task.
3. Run the checks above and `git diff --check`.
4. Commit with [Conventional Commits](https://www.conventionalcommits.org/), push the branch and
   open a pull request to `main`.
5. Wait for the `frontend` and `backend` CI checks and an approving review before merging.

Details are in [CONTRIBUTING.md](CONTRIBUTING.md), including the
[CI checks and branch protection](CONTRIBUTING.md#continuous-integration). AI agents start at
[AGENTS.md](AGENTS.md).

## Documentation

- [Project requirements](docs/project-requirements.md)
- [Architecture](docs/architecture.md)
- [Development setup (Windows)](docs/development-setup.md)
- [Contributing guide and task workflow](CONTRIBUTING.md)
- [AI agent guidelines and project rules](docs/ai-agent-guidelines.md)
- [Agent entry point](AGENTS.md) and [Claude Code entry point](CLAUDE.md)
- [Architecture decision records](docs/adr/README.md)
