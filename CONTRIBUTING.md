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

## Architectural decisions

Significant decisions (new technology, module boundaries, security approach, messaging design) are
recorded as ADRs in [docs/adr](docs/adr/README.md) and reviewed in a pull request like code.

## Secrets and data

- Never commit secrets, real credentials, private keys, tokens or production/personal data.
- If a secret is committed by mistake, tell the team immediately and rotate it. Deleting the file
  in a later commit does not remove it from Git history.
