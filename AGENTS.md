# SwiftDrop agent instructions

Shared entry point for AI coding agents. Claude Code loads it through `CLAUDE.md`; other tools read
it directly.

SwiftDrop is a parcel delivery and courier management system (university group project). It is a
monorepo with a Next.js frontend (`frontend/`, npm) and a Spring Boot 4 / Java 21 backend
(`backend/`, Maven Wrapper) structured as a **modular monolith** (`identity`, `pricing`, `delivery`,
`dispatch`, `tracking`, `merchant`, `notification`, `assistant`, `config`, `shared`), with PostgreSQL
+ Flyway, RabbitMQ and Docker Compose for local infrastructure. Core business modules and frontend
portals are implemented (see [docs/project-status.md](docs/project-status.md)).

## Read before changing files

1. [docs/ai-agent-guidelines.md](docs/ai-agent-guidelines.md): mandatory rules and project rules.
2. [CONTRIBUTING.md](CONTRIBUTING.md): task workflow, branches, commits, pull requests.
3. [README.md](README.md): project overview, technology stack, and quickstart.
4. [docs/project-status.md](docs/project-status.md): current implementation status and verified test state.
5. [docs/architecture.md](docs/architecture.md): what is implemented, decided, proposed and open.
6. The relevant [ADRs](docs/adr/README.md).
7. For backend tasks: [backend/README.md](backend/README.md) and [docs/development-setup.md](docs/development-setup.md).
8. For frontend tasks: [frontend/README.md](frontend/README.md) and `frontend/AGENTS.md` (Next.js).

Before implementing or modifying functionality, inspect the existing implementation, related interfaces, services, repositories, controllers, frontend components, tests, and relevant dependencies in the target module. Reuse or extend existing functionality where appropriate. Do not create duplicate implementations or introduce new architectural patterns without justification.

## Always

Summary only; the guidelines are authoritative.

- Check `git status` and the branch before modifying files.
- The approved task sets the scope; accepted ADRs and the mandatory rules still apply. If a task
  would violate an ADR, change the architecture or override a mandatory rule, stop and ask
  ([decision authority](docs/ai-agent-guidelines.md#decision-authority)).
- Do not change the stack, versions or module boundaries without approval.
- Never expose or commit secrets. Do not read `.env` unless explicitly authorized.
- No destructive operations, and do not touch unrelated containers, volumes, databases or data.
- Do not push or change remote Git state without explicit authorization.
- Run the relevant checks and report exact results, skipped checks and risks.

These are instructions, not technical enforcement.
