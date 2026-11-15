# SwiftDrop

SwiftDrop is a parcel delivery and courier management system, built as a university
Enterprise Application Development project by a team of 10 students. The assignment requirements
are summarized in [docs/project-requirements.md](docs/project-requirements.md).

## User groups and portals

| Role | Implemented capabilities & portals |
|---|---|
| **Customer** | Sign up and log in (email/password or Google OAuth2), manage profile/addresses, create delivery requests with fee estimates, track parcels in real time (SSE), view history and receipts (`/customer`, `/customer/new`, `/customer/history`, `/customer/profile`, `/track`) |
| **Merchant** | Register (with admin approval), manage API keys (`sdk_...`), create delivery orders via REST API, configure SSRF-safe webhook endpoints with HMAC signing and retries (`/merchant`, `/merchant/orders`, `/merchant/api-keys`, `/merchant/webhooks`, `/merchant/status`) |
| **Courier** | View assigned deliveries, update parcel status with notes or proof of delivery, mark failed deliveries (`/courier`, `/courier/parcels/[id]`) |
| **Dispatcher** | Assign unassigned parcels to couriers, manage hub handoffs and workloads (`/dispatch`) |
| **Admin** | Approve/suspend merchants, manage users, hubs, zones, and pricing rules, view reports (`/admin`, `/admin/merchants`, `/admin/users`, `/admin/hubs`, `/admin/pricing`, `/admin/reports`) |

## Architecture at a glance

- **Single Git monorepo** containing the frontend, backend, documentation, local infrastructure, and webhook test receiver.
- **Backend:** Spring Boot **modular monolith** (`com.swiftdrop`) with 10 modules (`identity`, `pricing`, `delivery`, `dispatch`, `tracking`, `merchant`, `notification`, `assistant`, `config`, `shared`).
- **Frontend:** Next.js App Router (`frontend/`). Presentation-only, communicating with the backend REST API and SSE streams.
- **Database:** PostgreSQL 18, with schema managed exclusively by **28 Flyway migrations**.
- **Messaging:** RabbitMQ 4.3 (via Spring AMQP) with a transactional outbox relay (`swiftdrop.events`) for asynchronous parcel/merchant notifications and webhook deliveries.
- **Real-time tracking:** Server-Sent Events (SSE) in `tracking` and `/track`.
- **AI Assistant:** Gemini-powered delivery assistant and MCP server (`assistant`).
- **Local infrastructure:** Docker Compose (`compose.yaml`) for PostgreSQL and RabbitMQ.

See [ADR 0001](docs/adr/0001-modular-monolith-in-a-monorepo.md), [docs/architecture.md](docs/architecture.md), and [docs/project-status.md](docs/project-status.md).

## Technology baseline

| Area | Choice |
|---|---|
| Backend | Java 21 (Temurin LTS), Spring Boot 4.1.x, Maven (via Maven Wrapper) |
| Persistence | Spring Data JPA, Hibernate ORM 7, Flyway, PostgreSQL 18 |
| Security | Spring Security 7, JWT resource server, Google OAuth2 / OIDC, Merchant API keys (`sdk_...`) |
| Messaging | Spring AMQP, RabbitMQ 4.3 (transactional outbox + consumers) |
| API docs | springdoc-openapi 3.2.0 |
| Backend tests | JUnit Jupiter, Mockito, AssertJ, Spring Boot Test, Testcontainers (PostgreSQL & RabbitMQ via `-Pintegration`) |
| Frontend | Node.js 24 LTS, npm, Next.js 16.4.x (Turbopack), React 19, TypeScript 6.0.x, Tailwind CSS 4, shadcn/ui |

Exact versions and rationale are in [ADR 0002](docs/adr/0002-technology-and-version-baseline.md).

## Repository layout

```text
.
├── .github/workflows/    GitHub Actions CI
├── backend/              Spring Boot modular monolith
├── frontend/             Next.js App Router application
├── tools/                Local development tools (e.g., webhook-receiver)
├── docs/                 Architecture, ADRs, contracts, requirements, status & reviews
├── compose.yaml          Local PostgreSQL and RabbitMQ
├── .env.example          Template for the git-ignored local .env file
├── AGENTS.md             Entry point for AI coding agents
├── CLAUDE.md             Claude Code entry point
└── CONTRIBUTING.md       Task workflow and team conventions
```

Each application has its own README: [backend/README.md](backend/README.md) and
[frontend/README.md](frontend/README.md).

## Current status

Core backend modules, database migrations, RabbitMQ eventing, and frontend portals are implemented and verified (see [docs/project-status.md](docs/project-status.md) for details). External SMS/email delivery currently uses a mock channel, and production hosting is not yet configured.

### Running tests

- **Backend unit tests:** `cd backend && .\mvnw.cmd test`
- **Backend integration tests (requires Docker):** `cd backend && .\mvnw.cmd verify -Pintegration`
- **Frontend checks:** `cd frontend && npm ci && npx tsc --noEmit && npm run lint && npm run build`

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

Edit `.env`, replace the two `change-me-...` passwords with your own local values and set
`SWIFTDROP_JWT_SECRET` (the file explains how to generate one). `.env` is
git-ignored; never commit it. It is read by Docker Compose and by the backend:

| Variable | Default in `.env.example` | Purpose |
|---|---|---|
| `SWIFTDROP_POSTGRES_DB` / `_USER` | `swiftdrop` | Database name and user |
| `SWIFTDROP_POSTGRES_PASSWORD` | `change-me-local-postgres` | Set your own; required |
| `SWIFTDROP_POSTGRES_PORT` | `5432` | Host port; change if 5432 is taken |
| `SWIFTDROP_RABBITMQ_USER` | `swiftdrop` | Broker user |
| `SWIFTDROP_RABBITMQ_PASSWORD` | `change-me-local-rabbitmq` | Set your own; required |
| `SWIFTDROP_RABBITMQ_PORT` / `_MANAGEMENT_PORT` | `5672` / `15672` | Host ports |
| `SWIFTDROP_JWT_SECRET` | empty | Token signing key, at least 32 bytes; required by the backend |
| `SWIFTDROP_JWT_ISSUER` / `_ACCESS_TOKEN_TTL` | `swiftdrop` / `PT15M` | Optional token settings |
| `SWIFTDROP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Optional; browser origins for `/api/**` |

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

Only the identity endpoints listed under [Current status](#current-status) exist under `/api/v1/...`.
The springdoc API docs (`/swagger-ui.html`, `/v3/api-docs`) require a bearer token.

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

1. `git switch dev`, `git pull`, then `git switch -c feat/<short-description>` (or `feature/`,
   `fix/`, `docs/`, `chore/`).
2. Make a small change within the approved task.
3. Run the checks above and `git diff --check`.
4. Commit with [Conventional Commits](https://www.conventionalcommits.org/), push the branch and
   open a pull request into `dev` (`main` is the stable release branch, updated through pull
   requests from `dev`).
5. Wait for the `frontend` and `backend` CI checks and an approving review before merging.

Details are in [CONTRIBUTING.md](CONTRIBUTING.md), including the
[CI checks and branch protection setup](CONTRIBUTING.md#continuous-integration). AI agents start at
[AGENTS.md](AGENTS.md).

## Documentation

- [Project requirements](docs/project-requirements.md)
- [Architecture](docs/architecture.md)
- [Development setup (Windows)](docs/development-setup.md)
- [Contributing guide and task workflow](CONTRIBUTING.md)
- [AI agent guidelines and project rules](docs/ai-agent-guidelines.md)
- [Agent entry point](AGENTS.md) and [Claude Code entry point](CLAUDE.md)
- [Architecture decision records](docs/adr/README.md)
