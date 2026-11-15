@AGENTS.md
@docs/ai-agent-guidelines.md

# Claude Code notes

The two imports above load the shared agent instructions and the project rules into every session.
Read these on demand when the task needs them:

- [README.md](README.md): project overview, technology stack, and quickstart
- [docs/project-status.md](docs/project-status.md): current implementation status and verified test state
- [docs/architecture.md](docs/architecture.md): current architecture with status labels
- [CONTRIBUTING.md](CONTRIBUTING.md): task workflow, branches, commits, pull requests
- [docs/development-setup.md](docs/development-setup.md): setup, local services, troubleshooting
- [backend/README.md](backend/README.md): backend modules and Maven testing commands (for backend tasks)
- [docs/adr/](docs/adr/README.md): accepted decisions

Before implementing or modifying functionality, inspect the existing implementation and related tests in the target module.

Claude-specific notes:

- `frontend/CLAUDE.md` imports the Next.js-managed `frontend/AGENTS.md`, so it loads when you work
  in `frontend/`. Do not edit the managed block in `frontend/AGENTS.md`.
- In instruction files, write file paths in backticks unless you intend an `@` import.

## Commands

Frontend, from `frontend/` (details in [frontend/README.md](frontend/README.md)):

```bash
npm ci          # install exact versions from package-lock.json
npm run lint    # ESLint; must pass before a pull request
npm run build   # production build, including TypeScript type checking
npm run dev     # development server on http://localhost:3000
```

Infrastructure from the repository root, backend from `backend/` (use `./mvnw` on Unix shells;
details in [docs/development-setup.md](docs/development-setup.md)):

```powershell
docker compose up -d          # start PostgreSQL and RabbitMQ (needs .env, copied from .env.example)
docker compose ps             # both services must be "healthy"
docker compose stop           # stop without deleting data
.\mvnw.cmd test                  # run all unit tests (*Test.java); no Docker needed
.\mvnw.cmd verify -Pintegration   # run unit + integration tests (*IT.java) via Testcontainers; requires Docker
.\mvnw.cmd spring-boot:run        # run the backend against the local services
```

Never run `docker compose down -v` or delete volumes unless you intend to wipe local data.
