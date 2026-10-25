# SwiftDrop backend

Spring Boot 4 / Java 21 modular monolith. It will own all business rules, authorization,
persistence and integrations; the seven module packages exist as an empty skeleton without
business code. See the root [README](../README.md),
[docs/architecture.md](../docs/architecture.md) and
[ADR 0003](../docs/adr/0003-backend-modules-and-boundaries.md) for the modules and their boundaries.

## Requirements

- Eclipse Temurin JDK 21 with `JAVA_HOME` pointing to it
  (see [development setup](../docs/development-setup.md)). Maven is not needed; the Maven Wrapper
  downloads the pinned version.
- Only to run the application: Docker Desktop with the local PostgreSQL and RabbitMQ from
  [`compose.yaml`](../compose.yaml), and a repository-root `.env` created from
  [`.env.example`](../.env.example).

## Commands

Run from this directory. On Linux/macOS (and Git Bash), use `./mvnw` instead of `.\mvnw.cmd`.

```powershell
.\mvnw.cmd clean verify       # compile and run the tests; no Docker needed (same as CI)
.\mvnw.cmd spring-boot:run    # run on http://localhost:8080; needs the containers healthy
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

There are no integration tests against PostgreSQL or RabbitMQ yet
(see [testing](../docs/architecture.md#12-testing)).
