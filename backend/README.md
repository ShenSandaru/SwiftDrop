# SwiftDrop backend

Spring Boot 4 / Java 21 modular monolith (`com.swiftdrop`). It owns all business rules,
authorization, persistence, messaging, and integrations across 10 modules:

- `identity`: User registration, JWT authentication, Google OAuth2 exchange, profiles, avatars, saved addresses, and admin user management.
- `pricing`: Zones, zone-pair pricing rules, fee calculation, and admin pricing management.
- `delivery`: Parcel booking, lifecycle state machine, hub network, delivery proofs, and transactional outbox.
- `dispatch`: Courier assignment, handoff history, hub queries, and concurrency control.
- `tracking`: Public parcel tracking and real-time Server-Sent Events (SSE) stream.
- `merchant`: Merchant registration/approval, API keys (`sdk_...`), order API, SSRF-safe webhooks, and delivery retries.
- `notification`: RabbitMQ event consumer (parcel + merchant status), in-app notifications, reconciliation, and mock SMS channel.
- `assistant`: Gemini-powered AI delivery assistant and MCP server (`track_parcel`, `estimate_fee`, `list_service_areas`).
- `config`: Spring Security filter chains, CORS, and authentication configuration.
- `shared`: Shared exception handling, pagination (`PageResponse`), and utilities.

See the root [README](../README.md), [docs/architecture.md](../docs/architecture.md), and
[ADR 0003](../docs/adr/0003-backend-modules-and-boundaries.md) for module boundaries.

## Requirements

- Eclipse Temurin JDK 21 with `JAVA_HOME` pointing to it
  (see [development setup](../docs/development-setup.md)). Maven is not needed; the Maven Wrapper
  downloads the pinned version.
- Docker Desktop with local PostgreSQL 18 and RabbitMQ 4.3 from [`compose.yaml`](../compose.yaml)
  (required to run the application locally and for `-Pintegration` Testcontainers tests), plus a
  repository-root `.env` created from [`.env.example`](../.env.example).

## Commands

Run from this directory (`backend/`). On Linux/macOS (and Git Bash), use `./mvnw` instead of `.\mvnw.cmd`.

```powershell
.\mvnw.cmd clean test-compile      # compile production and test sources
.\mvnw.cmd test                    # run all unit tests (*Test.java) via Surefire (no Docker needed)
.\mvnw.cmd verify -Pintegration    # run unit + integration tests (*IT.java) via Failsafe + Testcontainers (requires Docker)
.\mvnw.cmd spring-boot:run         # run on http://localhost:8080 (requires healthy containers)
```

## Configuration

[`application.properties`](src/main/resources/application.properties) reads the `SWIFTDROP_*`
variables from `../.env`, so start the backend from this directory (or set the working directory
of an IDE run configuration to `backend/`). Environment variables with the same names take
precedence. The variables are listed in [`.env.example`](../.env.example).

## Tests

- `SwiftdropBackendApplicationTests` checks that the application context starts without a database
  or broker.
- `ModuleBoundaryTest` checks the module dependency graph and the `api`-only rule of
  [ADR 0003](../docs/adr/0003-backend-modules-and-boundaries.md) by scanning `src/main/java`.
- `MigrationConventionTest` checks that each Flyway migration is in its module's folder
  (`db/migration/<module>/`), is named `V<yyyyMMddHHmm>__<module>_<description>.sql`, has a
  unique version, and is discovered by Flyway from the configured `spring.flyway.locations`
  without connecting to a database.
- `identity.domain.UserTest` unit-tests the `User` entity.
- `DatabaseMigrationIT` runs against an isolated PostgreSQL 18 container via Testcontainers under
  the `integration` Maven profile (`.\mvnw.cmd verify -Pintegration`), verifying Flyway migrations
  and Hibernate schema validation (`ddl-auto=validate`).

The default build (`.\mvnw.cmd clean verify` or `.\mvnw.cmd test`) executes only unit and slice tests
(`*Test.java`) and remains completely Docker-free.

There are no integration tests against RabbitMQ yet
(see [testing](../docs/architecture.md#12-testing)).
