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

## Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on pull requests targeting either
protected branch (`dev` or `main`) and on pushes to `dev` and `main`, with read-only
`contents: read` permissions and no secrets. Feature pull requests into `dev` and release pull
requests from `dev` into `main` therefore run the same checks:

| Status check | Runs |
|---|---|
| `CI / frontend` | Node.js from `.nvmrc` (24): `npm ci`, `npm run lint`, `npm run build` in `frontend/` |
| `CI / backend` | Temurin JDK 21: `./mvnw --batch-mode --no-transfer-progress clean verify` in `backend/` |

The backend job runs the same Docker-free verification as locally. It does not start PostgreSQL or
RabbitMQ, so no persistence or messaging integration is tested in CI yet.

Actions are pinned to full commit SHAs with the release tag in a comment. To update an action, look
up the new tag's commit (`git ls-remote https://github.com/<owner>/<action> refs/tags/<tag>`) and
change both the SHA and the comment.

Required status checks are a manual GitHub repository setting; no branch protection or ruleset is
configured from this repository, and it has not been verified that any status check is required
yet. The checks appear in the settings list only after the workflow has run once on GitHub, so let
it run on a pull request into `dev` first. Then a repository admin, for both `dev` and `main`:

1. Opens **Settings → Rules → Rulesets → New ruleset → New branch ruleset** (or the classic
   **Settings → Branches → Add branch protection rule**).
2. Sets enforcement to **Active** and targets `dev` and `main` (one ruleset with both branches, or
   one per branch).
3. Enables **Require a pull request before merging** with at least one required approval.
4. Enables **Require status checks to pass** and adds `frontend` and `backend` (source: GitHub
   Actions). Enable **Require branches to be up to date before merging** if wanted.
5. Enables **Block force pushes** and keeps **Restrict deletions** on.

The job names `frontend` and `backend` are the check names; on pull requests they are displayed as
`CI / frontend` and `CI / backend`.

## Team conventions

> **Status:** the branch workflow below is the team's policy. The other pull-request rules are
> **proposed defaults** that the team has not yet confirmed. The commit format is already used by
> every commit in the history.

### Branches

- `dev` is the protected integration branch and `main` the protected stable/release branch. Both
  are always buildable; nobody pushes to them directly, and changes arrive through reviewed pull
  requests.
- Create one short-lived branch per task from the latest `dev`:
  - `feature/<short-description>`, e.g. `feature/parcel-tracking-sse`
  - `fix/<short-description>`
  - `docs/<short-description>`
  - `chore/<short-description>` (build, configuration, dependencies)
- Workflow: feature pull request into `dev` → review and CI → merge; for a release, a separate pull
  request from `dev` into `main` → review and CI → merge.
- Keep branches small and rebase or merge `dev` into them often to avoid large conflicts.

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
- Include authorization matrix tests for new or modified backend endpoints using the shared test
  harness (`com.swiftdrop.test.security`).
- Include a new Flyway migration for any schema change (never edit a merged migration).
- Explain any new dependency and keep [ADR 0002](docs/adr/0002-technology-and-version-baseline.md)
  up to date if a version baseline changes.
- Receive at least one approving review from another team member before merging.

### Reviews

Reviewers check correctness, backend authorization, tests, naming and simplicity. Ask questions
rather than assume. Authors respond to every comment before merging.

### Authorization test matrix (TST-2)

Every pull request introducing or modifying backend HTTP endpoints must include authorization matrix
tests using the shared test harness in `com.swiftdrop.test.security`:

- Declare rows with `AuthorizationMatrixRow.row(...)` or the builder, specifying the scenario
  description, HTTP method, target URL path, `TestPrincipal`, and expected `HttpStatus`.
- Use `TestPrincipal` to declare the caller state: `anonymous()`, `customer()`, `merchant()`,
  `courier()`, `dispatcherAdmin()`, `user(UUID, Role...)` for custom subject IDs, or `apiKey(String)`
  for merchant API keys.
- Customize requests where valid payloads or headers are required to reach authorization or
  assert validation outcomes using `customize(Consumer<MockHttpServletRequestBuilder>)`, for example:
  `.customize(req -> req.contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"user@example.com\"}))`
- Execute rows as dynamic tests with `@TestFactory` returning `AuthorizationMatrixExecutor.toDynamicTests(mvc, rows)`,
  or in batch with `AuthorizationMatrixExecutor.executeAll(mvc, rows)`.
- For each endpoint, assert:
  1. Anonymous access behavior (201/200 for public/registration/login, 401 for protected).
  2. Allowed roles receive the expected successful status code.
  3. Disallowed roles receive `403 Forbidden` (or `401 Unauthorized` where unauthenticated).
  4. Resource ownership (e.g., customer accessing their own resource vs. another customer's resource)
     where ownership rules apply.
- Note: `TestPrincipal` JWTs simulate authenticated tokens for Spring Security authorization and
  `@PreAuthorize` evaluation; they do not replace dedicated token signing, decoding, or provider tests.

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
