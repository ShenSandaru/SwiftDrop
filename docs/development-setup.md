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

Maven does **not** need to be installed globally. The backend will include the Maven Wrapper
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
