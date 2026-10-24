# SwiftDrop coding instructions

These instructions apply to every contributor, human or AI assistant. Read [README.md](README.md)
for the project overview and [CONTRIBUTING.md](CONTRIBUTING.md) for the team workflow.

## Architecture rules

- **Modular monolith.** The backend is one Spring Boot application split into business modules.
  Do not introduce microservices, Kubernetes, Redis, extra message brokers or service meshes.
- **The backend owns the business.** Business rules, validation that matters, authorization,
  persistence and integrations (RabbitMQ, webhooks, Google sign-in) live in Spring Boot.
  The frontend only presents data and calls the REST API.
- **Authorization is enforced on the backend.** Hiding a page or button in the frontend is a
  usability feature, never a security control. Every protected endpoint checks the caller's role
  and ownership on the server.
- **Flyway owns the schema.** All schema changes are new versioned migrations. Never edit a
  migration that has already been merged to `main`. Hibernate runs with `ddl-auto=validate`.
- **Module boundaries.** A module exposes a small public API. Other modules must not reach into its
  internal classes or repositories. The concrete module layout is decided in Stage 5 and recorded
  in an ADR.
- **Asynchronous work goes through RabbitMQ.** Use it for notifications, domain events and merchant
  webhook delivery with retries. Use classic or quorum queues, not classic mirrored queues
  (removed in RabbitMQ 4).
- **Real-time tracking uses SSE** (Spring MVC `SseEmitter`). Do not add WebSockets unless a
  verified requirement needs them and an ADR records the change.

## Keep it simple

- Prefer the simplest design that meets the requirement. Do not add speculative abstractions,
  generic frameworks or "future-proof" layers.
- Do not add a dependency without a clear need. New dependencies require a PR description entry
  explaining why. Lombok is intentionally not used; use Java records for DTOs.
- Do not create business functionality during setup stages unless it is explicitly requested.

## Versions

- The version baseline is [ADR 0002](docs/adr/0002-technology-and-version-baseline.md).
  Changing a major or minor version requires updating that ADR.
- Backend: rely on the Spring Boot BOM. Do not pin a version that Boot already manages. The only
  backend versions pinned explicitly are Spring Boot itself and springdoc-openapi.
- Frontend: use npm only and commit `package-lock.json`. Do not commit lockfiles from other
  package managers.
- Do not use nightly, canary, preview, beta or release-candidate versions.

## Spring Boot 4 notes

Many tutorials target Spring Boot 3. In this project:

- Use `spring-boot-starter-webmvc` (not `spring-boot-starter-web`).
- OAuth2 starters include `security-`, e.g. `spring-boot-starter-security-oauth2-resource-server`.
- Flyway needs `spring-boot-starter-flyway` **and** `org.flywaydb:flyway-database-postgresql`.
- Jackson 3 is the default: packages are `tools.jackson.*` (annotations stay in
  `com.fasterxml.jackson.annotation`).
- In tests, use `@MockitoBean` / `@MockitoSpyBean` (`@MockBean` was removed). JUnit Jupiter is the
  Boot-managed version; do not override it.

## Security rules

- Never commit secrets, real credentials, private keys, tokens or production data. Use
  environment variables and committed `*.example` files containing placeholders only.
- Configuration is environment-specific (Spring profiles, `.env` files that are git-ignored).
- Validate all external input on the backend. Return safe error messages; do not leak stack traces.
- Merchant API credentials and webhook secrets are stored hashed or encrypted, never in plain text.

## Naming and style

- Java: package root `com.swiftdrop` (proposed, to be confirmed in Stage 4), 4-space indentation,
  standard Java naming. REST paths are plural nouns in kebab-case under `/api/v1/...`.
- Database: `snake_case` tables and columns. Migrations are `V<n>__<description>.sql`.
- TypeScript: 2-space indentation, components in `PascalCase`, files follow Next.js conventions.
- `.editorconfig` defines whitespace and line endings. Line endings are LF, except Windows
  `*.cmd` / `*.bat` / `*.ps1` scripts.

## Testing and honesty

- New business logic comes with tests. Use unit tests with Mockito for logic, and integration tests
  with Testcontainers for persistence and messaging. Do not use H2 as a PostgreSQL substitute.
- Run the relevant build, lint and test commands before claiming work is done. Report actual
  results, including failures. Never claim a build, test or security check passed without running it.

## Working protocol for AI assistants

- Inspect existing files before changing them. Do not delete, overwrite or restructure existing
  work without explaining why and getting approval.
- Do not install dependencies, generate applications or make large changes without approval.
- Work in the approved stage only, then report changed files, commands run, actual results,
  remaining issues and the proposed next step.
- Record meaningful architectural decisions as ADRs in [docs/adr](docs/adr/README.md).

## Commands

Build, test and run commands will be added here as the frontend (Stage 3), backend (Stage 4) and
Docker Compose setup (Stage 6) are created.
