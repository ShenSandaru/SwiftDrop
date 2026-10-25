# SwiftDrop agent instructions

Shared entry point for AI coding agents. Claude Code loads it through `CLAUDE.md`; other tools read
it directly.

SwiftDrop is a parcel delivery and courier management system (university group project). It is a
monorepo with a Next.js frontend (`frontend/`, npm) and a Spring Boot 4 / Java 21 backend
(`backend/`, Maven Wrapper) structured as a **modular monolith**, with PostgreSQL + Flyway,
RabbitMQ and Docker Compose for local infrastructure. Only email/password authentication exists;
no business feature exists yet.

## Read before changing files

1. [docs/ai-agent-guidelines.md](docs/ai-agent-guidelines.md): mandatory rules and project rules.
2. [docs/architecture.md](docs/architecture.md): what is implemented, decided, proposed and open.
3. The relevant [ADRs](docs/adr/README.md).
4. The docs for your area: [CONTRIBUTING.md](CONTRIBUTING.md) (task workflow),
   [docs/development-setup.md](docs/development-setup.md) (setup and local services),
   [frontend/README.md](frontend/README.md) and `frontend/AGENTS.md` (Next.js) for frontend work.

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
