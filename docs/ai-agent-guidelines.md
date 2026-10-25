# AI agent guidelines

Rules for every AI coding agent working in this repository. The project rules also apply to human
contributors. These are written instructions: no hook, CI job or Git setting enforces them, so
reviewers must check that they were followed.

## Decision authority

- The approved task defines the permitted scope: what may be changed in this task.
- Accepted [ADRs](adr/README.md) remain authoritative for architectural decisions. A task does not
  override them.
- The mandatory rules below apply to every task.
- [docs/architecture.md](architecture.md) describes the system. If it disagrees with an accepted
  ADR, the ADR wins and the description is corrected.
- If a task would require violating an accepted ADR, changing the architecture or overriding a
  mandatory rule, stop and ask for explicit approval before making any change.
- An accepted decision changes only through a new or superseding ADR that is explicitly reviewed
  and accepted ([ADR process](adr/README.md#process)). Update the related documentation in the
  same change.

## Mandatory rules

**Before changing files**

1. Check `git status` and the current branch. If there are unexpected uncommitted changes, stop and report them.
2. Read these guidelines, [docs/architecture.md](architecture.md), the relevant ADRs and the docs for the area you touch. For frontend work, follow `frontend/AGENTS.md` (installed Next.js docs).
3. Inspect the existing code and reuse its patterns before introducing new ones.

**Scope**

4. Work only within the approved task or phase. No unrelated refactoring, renaming or reformatting.
5. Do not implement modules or planned features without an approved task. The modules decided in [ADR 0003](adr/0003-backend-modules-and-boundaries.md) and anything labeled **Proposed** are not an authorization to implement. Change only the modules the task names; changing another module's `api` package, the allowed module dependencies or another module's migrations needs explicit review, so stop and ask.
6. Do not silently change frameworks, versions, the package manager, module boundaries or the architecture.
7. Do not install, upgrade or remove dependencies that affect the stack without approval.
8. Ask before irreversible, broad or security-sensitive changes.

**Secrets, data and the machine**

9. Never hardcode, print or commit secrets, credentials, tokens or personal data. Do not read `.env` unless the task explicitly requires it and the user has authorized it. Never stage `.env`.
10. Do not run destructive commands (for example `docker compose down -v`, `docker volume prune`, `git reset --hard`, deleting data) unless the user explicitly requested that operation on that target.
11. Do not affect unrelated containers, networks, volumes, databases, projects or user data. Use Docker only when the task needs it.
12. Do not push, open pull requests, create remote branches or releases, or otherwise change remote Git state without explicit authorization. Commit only when the task authorizes it.

**Finishing**

13. Run the relevant validation (see [CONTRIBUTING.md](../CONTRIBUTING.md#task-workflow)). Never claim a build, test or check passed without running it.
14. Inspect the full diff and `git status`. Make sure no build output, local files or secrets are included.
15. When an approved change affects architecture, commands, versions or procedures, update the owning document (table below) in the same change.
16. Report the files changed, the commands run with their exact results, skipped checks, assumptions, open questions and known risks.

## Project rules

**Architecture** (see [docs/architecture.md](architecture.md) for the current status)

- Modular monolith: one Spring Boot backend split into business modules. No microservices, Kubernetes, Redis, extra message brokers or service meshes.
- The backend owns business rules, validation that matters, authorization, persistence and integrations. The frontend only presents data and calls the REST API.
- Authorization is enforced on the backend by role and ownership. Hiding UI is never a security control.
- A module exposes a small public API; other modules never use its internal classes or repositories. The modules, their allowed dependencies, table ownership and the package rules are decided in the accepted [ADR 0003](adr/0003-backend-modules-and-boundaries.md): import another module only through its `api` package, along the allowed dependency graph. Module dependencies change only through a reviewed ADR.
- Kubernetes stays excluded although the brief offers it as an optional bonus; it needs a separate, explicitly approved ADR.
- Asynchronous work (notifications, domain events, webhook delivery with retries) goes through RabbitMQ, using classic or quorum queues, not classic mirrored queues.
- Real-time tracking uses SSE (`SseEmitter`). WebSockets need a verified requirement and an ADR.

**Database**

- Flyway owns the schema: every change is a new versioned migration; never edit a merged migration.
- Keep `spring.jpa.hibernate.ddl-auto=validate`. Changing it needs approval.

**Simplicity and versions**

- Prefer the simplest design that meets the requirement. No speculative abstractions or generic frameworks.
- New dependencies need a clear need and an explanation in the pull request. No Lombok; use Java records for DTOs.
- Versions are owned by [ADR 0002](adr/0002-technology-and-version-baseline.md); a major or minor change updates it. Backend versions come from the Spring Boot BOM; only Spring Boot and springdoc-openapi are pinned.
- Frontend: npm only, commit `package-lock.json`, no other lockfiles.
- No nightly, canary, preview, beta or release-candidate versions.

**Spring Boot 4** (many tutorials target Boot 3)

- Use `spring-boot-starter-webmvc`, not `spring-boot-starter-web`. OAuth2 starters include `security-`, e.g. `spring-boot-starter-security-oauth2-resource-server`.
- Flyway needs `spring-boot-starter-flyway` **and** `org.flywaydb:flyway-database-postgresql`.
- Jackson 3 (`tools.jackson.*`) is the default; annotations stay in `com.fasterxml.jackson.annotation`. Do not import Jackson 2 classes such as `com.fasterxml.jackson.databind.ObjectMapper`. Jackson 2 is present only because springdoc needs it: do not exclude it or add `spring-boot-jackson2` without a compatibility review. When changing springdoc or Jackson versions, inspect `mvnw dependency:tree` and rerun the tests.
- In tests use `@MockitoBean` / `@MockitoSpyBean` (`@MockBean` was removed). Do not override the Boot-managed JUnit version.

**Security and privacy**

- Validate all external input on the backend. Return safe error messages without stack traces.
- Merchant API credentials and webhook secrets are stored hashed or encrypted, never in plain text.
- Keep secrets and personal data out of logs. Configuration comes from the environment; commit only `*.example` files with placeholders.

**Naming and style**

- Java: package root `com.swiftdrop`, 4-space indentation, standard Java naming. REST paths are plural kebab-case nouns under `/api/v1/...`.
- Database: `snake_case` tables and columns; migrations are `V<n>__<description>.sql`.
- TypeScript: 2-space indentation, `PascalCase` components, Next.js file conventions.
- `.editorconfig` defines whitespace and line endings (LF, except `*.cmd`, `*.bat`, `*.ps1`).

**Testing**

- New business logic comes with tests: unit tests with Mockito for logic, integration tests against real PostgreSQL and RabbitMQ for persistence and messaging. Never use H2 as a PostgreSQL substitute.
- Testcontainers is **not declared**. It is the candidate integration-test tool, to be evaluated when the first such test is needed; adding it needs approval because those tests then require Docker. `mvnw clean verify` must keep passing without Docker unless that is approved.

## Documentation owners

| Information | Owner |
|---|---|
| Accepted decisions and rationale | [docs/adr/](adr/README.md) |
| Versions | [ADR 0002](adr/0002-technology-and-version-baseline.md), `backend/pom.xml`, `frontend/package.json` |
| Current architecture and status | [docs/architecture.md](architecture.md) |
| Shared agent rules and project rules | this file |
| Agent entry point | [AGENTS.md](../AGENTS.md) |
| Task workflow, branches, commits, pull requests | [CONTRIBUTING.md](../CONTRIBUTING.md) |
| Setup and local operations | [docs/development-setup.md](development-setup.md), [frontend/README.md](../frontend/README.md) |

Link to the owner instead of copying its content.

## Recommended practices

- Split work into small phases and propose a plan before non-trivial changes.
- Write readable code that matches the surrounding style; comment why, not what.
- Write focused tests that cover the behavior you changed.
- Keep completion reports short: what changed, results, risks, next step.
- When unsure about scope or intent, ask instead of assuming.
