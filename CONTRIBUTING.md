# Contributing to SwiftDrop

This guide describes how the 10-person team works in this repository, for human contributors and
AI agents alike. The project rules (architecture, versions, security, naming, testing) are in
[docs/ai-agent-guidelines.md](docs/ai-agent-guidelines.md#project-rules) and apply to everyone. The
current architecture is described in [docs/architecture.md](docs/architecture.md).

## Before you start

1. Install the tools in [docs/development-setup.md](docs/development-setup.md).
2. Verify your setup with the commands at the end of that document.
3. Never commit `.env` files, credentials or keys. Copy the committed `*.example` files instead.

## Task workflow

1. **Check the repository state:** `git status` and `git branch --show-current`. Do not start on
   top of unexpected uncommitted changes.
2. **Read** the applicable instructions ([AGENTS.md](AGENTS.md) and the
   [agent guidelines](docs/ai-agent-guidelines.md) for AI agents),
   [docs/architecture.md](docs/architecture.md) and the relevant [ADRs](docs/adr/README.md).
3. **Confirm the scope and acceptance criteria** of the task. If the task needs a new architectural
   decision or conflicts with an ADR, raise it first.
4. **Plan non-trivial changes** and agree on the plan before implementing.
5. **Implement one approved phase at a time.**
6. **Run the relevant checks** (below).
7. **Inspect the full diff and `git status`.** No build output, local files or secrets.
8. **Report** what changed, check results, assumptions, risks and follow-up work.
9. **Commit only when the task authorizes it**, using the commit convention below.
10. **Never push without explicit authorization.**

Checks by area (commands verified in this repository):

| Area changed | Run |
|---|---|
| Backend | `.\mvnw.cmd clean verify` from `backend/` (passes without Docker) |
| Frontend | `npm run lint` and `npm run build` from `frontend/` |
| `compose.yaml` | `docker compose config --quiet`; start the services only if the task needs it (see [development setup](docs/development-setup.md#6-local-infrastructure-postgresql-and-rabbitmq)) |
| Any change | `git diff --check`; update the owning documentation ([owners](docs/ai-agent-guidelines.md#documentation-owners)) |

## Team conventions

> **Status:** the branch and pull-request rules below are **proposed defaults** that the team has
> not yet confirmed. So far, the setup commits were made locally on `main` and have not been
> pushed; how they are published is still to be decided. The commit format is already used by
> every commit in the history.

### Branches

- `main` is always buildable. Nobody pushes to it directly; changes arrive through pull requests.
- Create one short-lived branch per task from the latest `main`:
  - `feature/<short-description>`, e.g. `feature/parcel-tracking-sse`
  - `fix/<short-description>`
  - `docs/<short-description>`
  - `chore/<short-description>` (build, configuration, dependencies)
- Keep branches small and rebase or merge `main` into them often to avoid large conflicts.

### Commits

Use [Conventional Commits](https://www.conventionalcommits.org/):

```text
<type>(<optional scope>): <summary in the imperative mood>
```

Types: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `build`, `ci`.
Scopes: `frontend`, `backend`, `infra`, `docs`, or a backend module name.

Examples:

```text
feat(backend): add parcel status transition validation
fix(frontend): show tracking error when the stream disconnects
docs: add ADR for webhook retry policy
```

### Pull requests

Each pull request should:

- Address one task and stay reasonably small.
- Describe what changed, why, and how it was tested.
- Pass the build, lint and tests locally before review is requested.
- Include tests for new backend business logic.
- Include a new Flyway migration for any schema change (never edit a merged migration).
- Explain any new dependency and keep [ADR 0002](docs/adr/0002-technology-and-version-baseline.md)
  up to date if a version baseline changes.
- Receive at least one approving review from another team member before merging.

### Reviews

Reviewers check correctness, backend authorization, tests, naming and simplicity. Ask questions
rather than assume. Authors respond to every comment before merging.

### Module ownership and contracts

These rules follow the accepted [ADR 0003](docs/adr/0003-backend-modules-and-boundaries.md).
Module owners are not assigned yet.

- Each backend module gets one or two owners from the team. The owners are also recorded in the
  contribution sheet required by the assignment.
- Changes to a module's `api` package (its public interface, records and event types) need a review
  from that module's owner. Keep changes additive where possible; an incompatible change is
  coordinated with the modules that use it in the same pull request.
- Adding or removing an allowed dependency between modules needs a new or superseding ADR.
- Never use another module's entities, repositories or implementation classes; use its `api`.

### Database migrations

Table ownership and migration ownership follow the accepted
[ADR 0003](docs/adr/0003-backend-modules-and-boundaries.md).

- Each table has exactly one owning module ([ownership](docs/architecture.md#4-backend-modular-monolith)).
  Only the owning module adds migrations for its tables, and its owner reviews them.
- File names follow `V<n>__<description>.sql`. To avoid version collisions between parallel
  branches, use a timestamp version with the module first in the description, for example
  `V202610251430__delivery_create_parcels.sql`, in a per-module folder (`db/migration/<module>/`).
  Flyway's discovery of these folders is verified before the first business migration is merged.
- Never edit a migration that has been merged to `main`; add a new one.

## Architectural decisions

Significant decisions (new technology, module boundaries, security approach, messaging design) are
recorded as ADRs in [docs/adr](docs/adr/README.md) and reviewed in a pull request like code.

## Secrets and data

- Never commit secrets, real credentials, private keys, tokens or production/personal data.
- If a secret is committed by mistake, tell the team immediately and rotate it. Deleting the file
  in a later commit does not remove it from Git history.
