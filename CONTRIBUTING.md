# Contributing to SwiftDrop

This guide describes how the 10-person team works in this repository. The coding rules are in
[CLAUDE.md](CLAUDE.md).

> The branching and commit conventions below are proposed defaults. Confirm or adjust them as a team
> before the first feature work starts.

## Before you start

1. Install the tools in [docs/development-setup.md](docs/development-setup.md).
2. Verify your setup with the commands at the end of that document.
3. Never commit `.env` files, credentials or keys. Copy the committed `*.example` files instead.

## Branches

- `main` is always buildable. Nobody pushes to it directly; changes arrive through pull requests.
- Create one short-lived branch per task from the latest `main`:
  - `feature/<short-description>`, e.g. `feature/parcel-tracking-sse`
  - `fix/<short-description>`
  - `docs/<short-description>`
  - `chore/<short-description>` (build, configuration, dependencies)
- Keep branches small and rebase or merge `main` into them often to avoid large conflicts.

## Commits

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

## Pull requests

Each pull request should:

- Address one task and stay reasonably small.
- Describe what changed, why, and how it was tested.
- Pass the build, lint and tests locally before review is requested.
- Include tests for new backend business logic.
- Include a new Flyway migration for any schema change (never edit a merged migration).
- Explain any new dependency and keep [ADR 0002](docs/adr/0002-technology-and-version-baseline.md)
  up to date if a version baseline changes.
- Receive at least one approving review from another team member before merging.

## Reviews

Reviewers check correctness, backend authorization, tests, naming and simplicity. Ask questions
rather than assume. Authors respond to every comment before merging.

## Architectural decisions

Significant decisions (new technology, module boundaries, security approach, messaging design) are
recorded as ADRs in [docs/adr](docs/adr/README.md) and reviewed in a pull request like code.

## Secrets and data

- Never commit secrets, real credentials, private keys, tokens or production/personal data.
- If a secret is committed by mistake, tell the team immediately and rotate it. Deleting the file
  in a later commit does not remove it from Git history.
