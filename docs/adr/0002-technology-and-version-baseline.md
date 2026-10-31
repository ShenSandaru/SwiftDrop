# 0002. Technology and version baseline

- Status: Accepted
- Date: 2026-10-24

## Context

Ten developers must use one compatible set of versions. Versions were chosen as a set, preferring
supported stable releases and framework-managed versions over picking the newest of everything.
Sources were checked on 2026-10-24.

## Decision

### Version policy

- **Backend:** pin only Spring Boot (parent POM), springdoc-openapi, and jacoco-maven-plugin. Every other backend library
  uses the version managed by the Spring Boot BOM.
- **Frontend:** use the versions installed by `create-next-app` 16.4.x, except where this ADR
  overrides them (TypeScript and `@types/node`). Commit `package-lock.json`.
- **Containers:** pin image tags to a major.minor series (e.g. `postgres:18-alpine`), not `latest`.
- **Patch updates** within the same series are allowed and are applied deliberately, not automatically.

### Backend

| Component | Version | How it is set |
|---|---|---|
| JDK | Eclipse Temurin **21 LTS** | Developer install; `java.version=21` in the POM |
| Spring Boot | **4.1.x** (4.1.2 at decision time) | Parent POM, pinned |
| Spring Framework / Security / Data JPA / AMQP | 7.0.x / 7.1.x / 4.1.x / 4.1.x | Boot-managed |
| Hibernate ORM | 7.4.x | Boot-managed |
| PostgreSQL JDBC driver | 42.7.x | Boot-managed |
| Flyway | 12.x: `spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql` | Boot-managed |
| RabbitMQ Java client (`amqp-client`) | 5.x | Boot-managed |
| springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`) | **3.2.0** | Pinned (not Boot-managed) |
| JaCoCo (`jacoco-maven-plugin`) | **0.8.12** | Pinned (not Boot-managed; SD-004) |
| JUnit Jupiter, Mockito, AssertJ, Spring Boot Test | 6.0.x, 5.x, 3.x | Boot-managed |
| Testcontainers | 2.0.x | Boot-managed |
| Maven | **3.9.16** | Pinned through the Maven Wrapper |
| Maven Wrapper | 3.3.4, `only-script` type, with `distributionSha256Sum` | Generated with `mvn wrapper:wrapper` in Stage 4 |
| Lombok | Not used | Java records instead |

#### Jackson 2 and Jackson 3

Spring Boot 4.1 uses **Jackson 3** (`tools.jackson.*`) as its preferred and default JSON library,
including for Spring MVC request and response bodies. springdoc-openapi 3.2.0 and its swagger-core
2.2.x dependency still require **Jackson 2** (`com.fasterxml.jackson.*`), so both lines are on the
classpath. Both versions are managed by the Spring Boot BOM (3.1.x and 2.21.x).

- The two lines live in different Java packages and are not interchangeable. A Jackson 2
  `ObjectMapper` is not configured by Spring Boot's `spring.jackson.*` properties.
- Application code uses the Jackson 3 APIs. Jackson annotations (`@JsonProperty` and others) stay in
  `com.fasterxml.jackson.annotation` in both lines.
- Do not add `spring-boot-jackson2` and do not exclude Jackson 2 from springdoc without a new
  compatibility review. springdoc and swagger-core call Jackson 2 classes at runtime.
- **Known limitation:** springdoc's schema generation reads only the Jackson 2 versions of
  `@JsonSerialize` and `@JsonDeserialize`, so the Jackson 3 versions in
  `tools.jackson.databind.annotation` can produce empty schemas ([springdoc #3333](https://github.com/springdoc/springdoc-openapi/issues/3333)).
  Prefer plain records with `@JsonProperty` where a name must be explicit, and check the generated
  OpenAPI schema when DTOs are implemented.
- Recheck this arrangement whenever Spring Boot, springdoc or swagger-core is upgraded. Once
  swagger-core supports Jackson 3 ([swagger-core #4991](https://github.com/swagger-api/swagger-core/issues/4991)),
  Jackson 2 may no longer be needed.

#### Maven distribution checksum

The wrapper verifies the downloaded Maven archive against `distributionSha256Sum` in
`backend/.mvn/wrapper/maven-wrapper.properties` and refuses to run on a mismatch. Apache publishes
only SHA-512 checksums and PGP signatures for Maven distributions, not SHA-256. The SHA-256 value
was therefore computed from the exact `apache-maven-3.9.16-bin.zip` after that archive matched the
SHA-512 published on Maven Central and downloads.apache.org and its PGP signature verified against
<https://downloads.apache.org/maven/KEYS>. When the Maven version changes, repeat this procedure;
never copy a checksum from an unofficial source. The check runs only when the wrapper downloads
the archive, not for an archive already cached in `~/.m2/wrapper/dists`.

### Frontend

| Component | Version | How it is set |
|---|---|---|
| Node.js | **24 LTS** (npm 11 bundled) | Developer install; `.nvmrc` and `engines` |
| Next.js / `eslint-config-next` | **16.4.x** | `create-next-app@16.4` |
| React / React DOM | 19.x (the version `create-next-app` pins, 19.3.0 for 16.4.3) | Generator |
| TypeScript | **6.0.x** (`~6.0.3`) | Override (the generator defaults to `^5`) |
| `@types/node` | **24.x** | Override to match the Node.js runtime (the generator defaults to `^20`) |
| `@types/react`, `@types/react-dom` | 19.x | Generator |
| ESLint | **9.x**, end-of-life since 2026-08-06 and kept on purpose (see below) | Generator (`^9`) |
| Tailwind CSS | **4.x** through `@tailwindcss/turbopack`, the Turbopack CSS loader in `next.config.ts`. There is no PostCSS config. | Generator (`^4`) |
| shadcn/ui | CLI `shadcn@4.x`, `base-nova` preset with neutral base color. Components use **Base UI** (`@base-ui/react`), not Radix. Component source is copied into `components/ui/`. | `shadcn init` |
| shadcn runtime packages | `shadcn` (provides `shadcn/tailwind.css`, imported by `app/globals.css`), `cn` (class merging; replaces `clsx` + `tailwind-merge`), `@base-ui/react`, `class-variance-authority`, `lucide-react`, `tw-animate-css` | Added by `shadcn init` |

#### ESLint 9 (end-of-life)

ESLint 9 reached end-of-life on 2026-08-06, and ESLint 10 is the only supported line. We still use
ESLint 9 because the official Next.js 16.4 template installs it. `eslint-config-next` 16.4.3 also
depends on `eslint-plugin-react`, `eslint-plugin-import` and `eslint-plugin-jsx-a11y`, whose peer
ranges allow ESLint 9 at most. ESLint is a development-only tool and is not shipped to users.
Do not force ESLint 10 with peer-dependency overrides or `--legacy-peer-deps`. **Upgrading to
ESLint 10 is pending** until `eslint-config-next` and its plugins declare support for it.

#### Next.js configuration choices

- **Cache Components: disabled on purpose.** It is part of the generator's "recommended defaults",
  but it changes the rendering model: dynamic data must sit inside `<Suspense>` or be marked with
  `'use cache'`, otherwise the build fails. That is an extra source of errors for a team new to
  Next.js, and the current requirements do not need it. It can be reconsidered later (one option in
  `next.config.ts`) if the application needs it, and the change should be recorded in an ADR.
- **React Compiler: disabled** (generator default).
- No `src/` directory; import alias `@/*`; Turbopack is the default bundler; `AGENTS.md` is included.

### Infrastructure

| Component | Version | Image |
|---|---|---|
| PostgreSQL | **18.x** | `postgres:18-alpine` |
| RabbitMQ | **4.3.x** with management plugin | `rabbitmq:4.3-management-alpine` |
| Docker Desktop / Compose | Current 4.x with WSL 2 / Compose v5 | Developer install |

## Evidence (verified facts)

- **Spring Boot 4.1.x** is the current GA line (4.1.2). Open-source support runs until 2027-07-31.
  Boot 3.5 open-source support ended 2026-06-30 and Boot 4.0 support ends 2026-12-31. 4.2.0 is still
  a release candidate.
  Sources: <https://spring.io/projects/spring-boot>, <https://api.spring.io/projects/spring-boot/generations>
- Boot 4.1.2 "requires at least Java 17 and is compatible with versions up to and including Java 27",
  Spring Framework 7.0.10 or later, and Maven 3.6.3 or later.
  Source: <https://docs.spring.io/spring-boot/system-requirements.html>
- Managed versions in Boot 4.1.2: Spring Security 7.1.2, Spring Data JPA 4.1.2, Spring AMQP 4.1.2,
  Hibernate 7.4.13.Final, PostgreSQL JDBC 42.7.14, Flyway 12.4.0 (core and PostgreSQL module),
  amqp-client 5.30.0, JUnit Jupiter 6.0.3, Mockito 5.23.0, AssertJ 3.27.7, Testcontainers 2.0.5,
  Jackson 3.1.7.
  Source: <https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html>
- Boot needs `org.flywaydb:flyway-database-postgresql` in addition to the Flyway starter for PostgreSQL.
  Source: <https://docs.spring.io/spring-boot/how-to/data-initialization.html>
- Flyway lists PostgreSQL 9.2 to 18 as verified versions.
  Source: <https://documentation.red-gate.com/fd/postgresql-database-277579325.html>
- Hibernate ORM 7.4 `PostgreSQLDialect` supports PostgreSQL 14.0 and later.
  Source: <https://docs.hibernate.org/stable/orm/dialect/>
- Spring AMQP 4.1.2 requires Spring Framework 7.0.0 or later and `amqp-client` 5.28.0 or later
  (Boot manages 5.30.0).
  Source: <https://docs.spring.io/spring-amqp/reference/introduction/quick-tour.html>
- springdoc FAQ: Spring Boot 4.1.x is compatible with springdoc-openapi 3.1.x to 3.2.x. 3.2.0 is the
  latest release on Maven Central.
  Sources: <https://springdoc.org/>,
  <https://repo.maven.apache.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/maven-metadata.xml>
- Temurin 21 is available until at least December 2029. The latest release is 21.0.13+9.
  Source: <https://adoptium.net/support/>
- Maven 3.9.16 (2026-05-13) is maintained. 3.10.0 (2026-09-27) is the newest release. Maven 4 is
  only a release candidate. Maven Wrapper 3.3.4 is the latest release.
  Sources: <https://maven.apache.org/download.cgi>, <https://maven.apache.org/docs/history.html>,
  <https://maven.apache.org/tools/wrapper/>
- PostgreSQL 18 (18.6) is supported until November 2030.
  Source: <https://www.postgresql.org/support/versioning/>
- The official `postgres` image for 18 and later sets `PGDATA=/var/lib/postgresql/18/docker` and
  declares `VOLUME /var/lib/postgresql`. Volumes must be mounted at `/var/lib/postgresql`.
  The `/var/lib/postgresql/data` path used by older tutorials is for 17 and below.
  `POSTGRES_PASSWORD` is required.
  Source: <https://github.com/docker-library/docs/blob/master/postgres/content.md>
- RabbitMQ 4.3 (4.3.7) has community support until 2027-01-31. 4.4.0 was released 2026-10-20.
  The official images publish `4.3-management-alpine` tags.
  Sources: <https://www.rabbitmq.com/release-information>, Docker Hub `rabbitmq` tags
- Node.js 24.22.1 "Krypton" is the latest LTS (2026-10-20) and bundles npm 11.19.1. Node 25 reached
  end-of-life on 2026-03-31.
  Sources: <https://nodejs.org/en/about/previous-releases>, <https://nodejs.org/dist/index.json>
- Next.js 16.4.3 is the npm `latest` release. It requires Node.js 20.9 or later, accepts React 18.2
  or 19, and requires TypeScript 5.1 or later. Its default type checker runs the project-local `tsc`
  and supports TypeScript 6.
  Sources: <https://nextjs.org/docs/app/getting-started/installation>,
  <https://nextjs.org/docs/app/api-reference/config/next-config-js/useTypeScriptCli>, npm registry
- `create-next-app` 16.4.3 installs React 19.3.0, TypeScript `^5`, `@types/node` `^20`, ESLint `^9`,
  Tailwind `^4`, and `@tailwindcss/turbopack` (with Turbopack) or `@tailwindcss/postcss`.
  Source: <https://github.com/vercel/next.js/blob/v16.4.3/packages/create-next-app/templates/index.ts>
- typescript-eslint supports TypeScript `>=4.8.4 <6.1.0`. This rules out TypeScript 7.0, the npm
  `latest` release.
  Source: <https://typescript-eslint.io/users/dependency-versions/>
- `eslint-config-next` 16.4.3 depends on `eslint-plugin-react`, `eslint-plugin-import` and
  `eslint-plugin-jsx-a11y`. Their peer ranges allow ESLint 9 at most, which rules out ESLint 10.
  Source: npm registry metadata
- ESLint v9 (last release 9.39.5) reached end-of-life on 2026-08-06. v10 is the current line.
  Source: <https://eslint.org/version-support>
- shadcn/ui: "All components are updated for Tailwind v4 and React 19." New projects use
  `tw-animate-css`.
  Source: <https://ui.shadcn.com/docs/tailwind-v4>
- The `cn` package is published by the shadcn maintainer from <https://github.com/shadcn-ui/cn>
  (MIT). Source: npm registry metadata
- **Stage 3 scaffold (2026-10-25), verified against the generated files:**
  - Installed: Next.js 16.4.3, React 19.3.0, TypeScript 6.0.3, `@types/node` 24.19.2, ESLint 9.39.5,
    `eslint-config-next` 16.4.3, `typescript-eslint` 8.72.0, Tailwind CSS 4.3.3,
    `@tailwindcss/turbopack` 4.3.3, `shadcn` 4.22.1, `cn` 0.5.0, `@base-ui/react` 1.9.0.
  - Results: `npm ci`, `npm run lint` and `npm run build` succeed on Node.js 24.22.1.
- **Stage 4 backend dependencies (2026-10-25), verified with `mvnw dependency:tree -Dverbose`:**
  - Jackson 3 (`tools.jackson.core:jackson-databind` 3.1.7) comes from `spring-boot-starter-webmvc`
    → `spring-boot-starter-jackson`. Jackson 2 (`com.fasterxml.jackson.core:jackson-databind`,
    `jackson-dataformat-yaml`, `jackson-datatype-jsr310` 2.21.7) comes from
    `springdoc-openapi-starter-common` 3.2.0. Its POM excludes swagger-core's versioned Jackson 2
    dependencies and redeclares them without a version, so the Boot BOM applies
    ([springdoc #3373](https://github.com/springdoc/springdoc-openapi/issues/3373)).
  - swagger-core's `io.swagger.v3.core.util.Json` returns a Jackson 2 `ObjectMapper`, so Jackson 2
    is a runtime requirement, not an unused leftover.
  - Spring Boot: "Jackson 3 is the preferred and default library." Jackson 2 support through
    `spring-boot-jackson2` is deprecated.
    Sources: <https://docs.spring.io/spring-boot/reference/features/json.html>,
    <https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide>
  - Maven 3.9.16 binary zip: SHA-512 `ed41650d…933454af3` matches Maven Central and
    downloads.apache.org; good PGP signature by key `84789D24DF77A32433CE1F079EB80E92EB2135B1`
    from the Maven `KEYS` file; SHA-256 computed from it:
    `5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce`. Both `mvnw` and `mvnw.cmd`
    rejected a modified checksum and accepted this one when run against an empty wrapper cache.
    Sources: <https://maven.apache.org/tools/wrapper/>,
    <https://downloads.apache.org/maven/maven-3/3.9.16/binaries/>
- **npm `allow-scripts` warning for `unrs-resolver`:** npm 11 skips this package's postinstall
  script. The script only runs `napi-postinstall`, a fallback for npm bug
  [npm/cli#4828](https://github.com/npm/cli/issues/4828), where npm sometimes fails to install the
  platform-specific native binding. The bindings are normal optional dependencies. The lockfile
  lists all 22 platform bindings, and on Windows x64 the binding installs and loads correctly.
  No remediation is needed. If lint ever reports a missing native binding, delete `node_modules`
  and run `npm ci`. Do not approve install scripts globally.
  Sources: `node_modules/unrs-resolver/package.json`, `node_modules/napi-postinstall/README.md`
- **Stage 5 local infrastructure (2026-10-25), verified with Docker Engine 29.8.2 / Compose v5.6.0:**
  - `postgres:18-alpine` contains PostgreSQL 18.6 and sets `PGDATA=/var/lib/postgresql/18/docker`
    with `VOLUME /var/lib/postgresql`. `compose.yaml` mounts the named volume at
    `/var/lib/postgresql`.
  - `rabbitmq:4.3-management-alpine` contains RabbitMQ 4.3.7 (Erlang 27.3.4.19). 4.3.7 was released
    on 2026-10-20 (GitHub releases). RabbitMQ stores data per node name, which defaults to the
    hostname, so `compose.yaml` sets `hostname: rabbitmq`. `RABBITMQ_DEFAULT_USER` and
    `RABBITMQ_DEFAULT_PASS` are still supported; the `*_FILE` variants were removed in 3.9.
  - Neither image defines a `HEALTHCHECK`. Compose uses `pg_isready` over TCP and
    `rabbitmq-diagnostics -q check_port_connectivity`.
  - A throwaway database and a durable queue survived `docker compose restart` and
    `docker compose down` / `up` (new containers, same volumes, same PostgreSQL system identifier).
  - The backend started against both services: Flyway 12.4.0 validated 0 migrations on
    PostgreSQL 18.6, and `/actuator/health` reported `db` and `rabbit` (4.3.7) as `UP`.
  Sources: image configuration via `docker buildx imagetools inspect`,
  <https://github.com/docker-library/docs/blob/master/postgres/content.md>,
  <https://github.com/docker-library/docs/blob/master/rabbitmq/content.md>,
  <https://www.rabbitmq.com/docs/monitoring#health-checks>,
  <https://github.com/rabbitmq/rabbitmq-server/releases>
- Docker Desktop on Windows needs WSL 2.1.5 or later and Windows 11 23H2 or later. The current
  release is 4.96.3, with Engine 29.9.x and Compose v5.6.x.
  Sources: <https://docs.docker.com/desktop/setup/install/windows-install/>,
  <https://docs.docker.com/desktop/release-notes/>

## Assumptions (not explicitly documented; verified by later stages)

- **Spring AMQP 4.1 with RabbitMQ 4.3:** no official version matrix exists. Spring AMQP uses
  AMQP 0-9-1 through `amqp-client` 5.x, which RabbitMQ 4.x supports. A connection smoke test in
  Stage 5 succeeded (Actuator `rabbit` health `UP` against RabbitMQ 4.3.7); message publishing and
  consuming are verified when the first messaging feature is built.
- ~~shadcn/ui with Next.js 16.4~~ and ~~TypeScript 6.0 with the `create-next-app` output~~ were
  confirmed in Stage 3 by a clean `npm ci`, `npm run lint` and `npm run build` (see evidence above).

## Alternatives considered

- **Spring Boot 3.5 / 4.0:** open-source support has ended or ends in 2026. Rejected.
- **Java 25 LTS:** supported by Boot 4.1, but there is no compelling reason to move away from 21,
  which has wide IDE and tutorial support.
- **Maven 3.10.0:** recommended upstream but released four weeks before this decision. 3.9.16 is
  maintained and well proven. Moving to 3.10.x later means changing one line in
  `.mvn/wrapper/maven-wrapper.properties`.
- **TypeScript 7.0:** typescript-eslint does not support it yet (see evidence).
- **ESLint 10 now:** supported upstream, but `eslint-config-next` 16.4.3's plugins do not declare
  support for it. Forcing it would mean peer-dependency overrides.
- **RabbitMQ 4.4:** newer support window, but its first release was four days old. Revisit after
  4.4.1 or later, or before 4.3 community support ends on 2027-01-31. The change is one image tag.
- **PostgreSQL 19:** release candidate only.

## Consequences

- Students following Spring Boot 3 tutorials must adjust starter names, Jackson packages and test
  annotations. The project rules in [docs/ai-agent-guidelines.md](../ai-agent-guidelines.md#project-rules)
  list the differences.
- springdoc-openapi must be re-checked against its compatibility matrix whenever Spring Boot is upgraded.
- Check whether `eslint-config-next` supports ESLint 10 at each Next.js upgrade, and move to
  ESLint 10 once it does.
- Before 2027-01-31, plan the RabbitMQ 4.3 → 4.4 image update.
- Before 2027-07-31, plan the Spring Boot 4.1 → 4.2 upgrade if the project is still active.
