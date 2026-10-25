# Development setup (Windows)

Every team member installs the same toolchain. The versions come from
[ADR 0002](adr/0002-technology-and-version-baseline.md).

## Required tools

| Tool | Version | Notes |
|---|---|---|
| Eclipse Temurin JDK | 21 LTS (latest 21.0.x patch) | Other JDKs may stay installed, but `JAVA_HOME` must point to 21 |
| Node.js | 24 LTS (latest 24.x patch) | Bundles npm 11. Node 25 is end-of-life; uninstall it |
| Docker Desktop | Current 4.x release with the WSL 2 backend | Provides Docker Engine and Docker Compose |
| Git | Any current release | |

Maven does **not** need to be installed globally. The backend includes the Maven Wrapper
(`mvnw` / `mvnw.cmd`), which downloads the pinned Maven version automatically.

## 1. Install JDK 21

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

Alternatively, download the Windows MSI from <https://adoptium.net/temurin/releases/?version=21>
and tick **Set JAVA_HOME variable** during installation.

Then make sure `JAVA_HOME` points to the JDK 21 folder, e.g.
`C:\Program Files\Eclipse Adoptium\jdk-21.0.x-hotspot`, and that `%JAVA_HOME%\bin` comes before
any other JDK on `PATH` (System Properties → Environment Variables). Open a new terminal afterwards.

## 2. Install Node.js 24 LTS

Uninstall any other Node.js version first (Settings → Apps), then:

```powershell
winget install OpenJS.NodeJS.LTS
```

Alternatively, download the Windows installer for the **LTS** line from <https://nodejs.org/>.
The repository's `.nvmrc` file records the expected major version (24). Use npm only, not pnpm
or yarn, in this repository.

## 3. Install Docker Desktop

1. Requirements: Windows 11 64-bit 23H2 (build 22631) or later, or Windows 10 22H2 (build 19045);
   hardware virtualization enabled in BIOS/UEFI; 8 GB RAM.
2. Update WSL (version 2.1.5 or later is required):

   ```powershell
   wsl --update
   ```

3. Download and run the installer from
   <https://docs.docker.com/desktop/setup/install/windows-install/>, and choose the **WSL 2** backend.
4. Start Docker Desktop and accept the terms. It is free for education and personal use.

Docker must be running for Docker Compose and for Testcontainers-based integration tests.

## 4. Configure Git

```powershell
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
```

The repository's `.gitattributes` normalizes line endings, so no extra `core.autocrlf` setup is needed.

## 5. Optional tools

- IDE: IntelliJ IDEA (Community or Ultimate) or VS Code with the Java and ESLint extensions.
- GitHub CLI (`winget install GitHub.cli`) for working with pull requests from the terminal.

## Verify your setup

Run these in a **new** terminal:

```powershell
java -version            # openjdk version "21.0.x" ... Temurin
node -v                  # v24.x.x
npm -v                   # 11.x.x
docker compose version   # Docker Compose version v5.x
docker info              # must succeed (Docker Desktop running)
git --version
```

If `java -version` still shows another version, `JAVA_HOME` or `PATH` is not pointing to JDK 21.

## 6. Local infrastructure (PostgreSQL and RabbitMQ)

[`compose.yaml`](../compose.yaml) runs PostgreSQL 18 and RabbitMQ 4.3 (with the management UI) for
local development only. Run all `docker compose` commands from the repository root, with Docker
Desktop running.

| Service | Image | Host address (default) | Data volume |
|---|---|---|---|
| PostgreSQL | `postgres:18-alpine` | `127.0.0.1:5432` | `swiftdrop_postgres-data` |
| RabbitMQ (AMQP) | `rabbitmq:4.3-management-alpine` | `127.0.0.1:5672` | `swiftdrop_rabbitmq-data` |
| RabbitMQ management UI | same container | <http://localhost:15672> | |

Ports are published on `127.0.0.1` only, so other machines on the network cannot reach them.

### Create your `.env` file (once)

```powershell
Copy-Item .env.example .env      # Unix: cp .env.example .env
```

Open `.env` and replace both `change-me-...` passwords with your own local values. `.env` is
git-ignored; never commit it. These are development credentials only. Write values as
`KEY=value` without quotes or spaces, because both Docker Compose and the backend read this file.

The database name, user and passwords are applied **only when the volumes are first created**.
Changing them in `.env` later has no effect until you reset the data (see below).

### Start, check and stop

```powershell
docker compose up -d                 # start in the background
docker compose ps                    # STATUS must show "(healthy)" for both services
docker compose logs -f postgres      # follow logs (Ctrl+C to stop following); or: rabbitmq
docker compose stop                  # stop the containers; data is kept
docker compose down                  # remove the containers and network; volumes and data are kept
```

The services use `restart: unless-stopped`, so they start again with Docker Desktop until you stop
them. Sign in to the management UI with `SWIFTDROP_RABBITMQ_USER` and `SWIFTDROP_RABBITMQ_PASSWORD`
from `.env`. The default `guest` user does not exist. To open a database shell:

```powershell
docker compose exec postgres psql -U swiftdrop -d swiftdrop
```

### Connect the backend

The backend reads the repository-root `.env` file when it runs from `backend/`
(`spring.config.import` in `application.properties`). Environment variables with the same names
take precedence. With the services healthy:

```powershell
cd backend
.\mvnw.cmd spring-boot:run           # Unix: ./mvnw spring-boot:run
```

On startup Flyway connects and reports "No migrations found" until the first migration exists, and
creates its `flyway_schema_history` table. `http://localhost:8080/actuator/health` returns
`{"status":"UP"}` when the database and RabbitMQ are reachable. If you run the backend from an IDE,
set the working directory to `backend/` or define the `SWIFTDROP_*` variables in the run
configuration. `.\mvnw.cmd clean verify` does not need the containers.

### Reset data (deletes everything)

Only when you intend to wipe your local database and broker, for example after changing the
credentials in `.env`:

```powershell
docker compose down -v               # DELETES the swiftdrop_postgres-data and swiftdrop_rabbitmq-data volumes
docker compose up -d
```

`-v` only removes this project's volumes. Do not use `docker volume prune`, which can delete other
projects' data.

### Troubleshooting

- **Port conflict** (`docker compose up` fails with "port is already allocated" or a similar bind
  error): another PostgreSQL (a local install or another project's container) uses the port. Find it with
  `Get-NetTCPConnection -LocalPort 5432 -State Listen` or `docker ps`. Do not stop other projects'
  services; instead set a free host port in `.env`, e.g. `SWIFTDROP_POSTGRES_PORT=5433`, and run
  `docker compose up -d` again. The backend picks up the same value from `.env`. The same applies to
  `SWIFTDROP_RABBITMQ_PORT` and `SWIFTDROP_RABBITMQ_MANAGEMENT_PORT`.
- **`required variable SWIFTDROP_..._PASSWORD is missing a value`:** `.env` is missing or
  incomplete. Create it from `.env.example`.
- **`password authentication failed`** (backend or `psql`): the volume was created with different
  credentials than the current `.env`. Restore the old values or reset the data.
- **A service stays `starting` or becomes `unhealthy`:** check `docker compose logs <service>`.
- **Docker commands fail with "cannot connect to the Docker daemon":** start Docker Desktop and
  wait until it reports that the engine is running.
