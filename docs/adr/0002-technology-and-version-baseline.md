# 0002. Technology and version baseline

- Status: Accepted
- Date: 2026-10-24

## Context

Ten developers must use one compatible set of versions. Versions were chosen as a set, preferring
supported stable releases and framework-managed versions over picking the newest of everything.
Sources were checked on 2026-10-24.

## Decision

### Version policy

- **Backend:** pin only Spring Boot (parent POM) and springdoc-openapi. Every other backend library
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
| JUnit Jupiter, Mockito, AssertJ, Spring Boot Test | 6.0.x, 5.x, 3.x | Boot-managed |
| Testcontainers | 2.0.x | Boot-managed |
| Maven | **3.9.16** | Pinned through the Maven Wrapper |
| Maven Wrapper | 3.3.4, `only-script` type | Generated with `mvn wrapper:wrapper` in Stage 4 |
| Lombok | Not used | Java records instead |

### Frontend

| Component | Version | How it is set |
|---|---|---|
| Node.js | **24 LTS** (npm 11 bundled) | Developer install; `.nvmrc` and `engines` |
| Next.js / `eslint-config-next` | **16.4.x** | `create-next-app@16.4` |
| React / React DOM | 19.x (the version `create-next-app` pins, 19.3.0 for 16.4.3) | Generator |
| TypeScript | **6.0.x** (`~6.0.3`) | Override (the generator defaults to `^5`) |
| `@types/node` | **24.x** | Override to match the Node.js runtime (the generator defaults to `^20`) |
| `@types/react`, `@types/react-dom` | 19.x | Generator |
| ESLint | **9.x** | Generator (`^9`) |
| Tailwind CSS | **4.x**, with the PostCSS or Turbopack plugin the generator selects | Generator (`^4`) |
| shadcn/ui | CLI `shadcn@4.x` via `npx`; components are copied into the repo | Run on demand |

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
- shadcn/ui: "All components are updated for Tailwind v4 and React 19." New projects use
  `tw-animate-css`.
  Source: <https://ui.shadcn.com/docs/tailwind-v4>
- Docker Desktop on Windows needs WSL 2.1.5 or later and Windows 11 23H2 or later. The current
  release is 4.96.3, with Engine 29.9.x and Compose v5.6.x.
  Sources: <https://docs.docker.com/desktop/setup/install/windows-install/>,
  <https://docs.docker.com/desktop/release-notes/>

## Assumptions (not explicitly documented; verified by later stages)

- **Spring AMQP 4.1 with RabbitMQ 4.3:** no official version matrix exists. Spring AMQP uses
  AMQP 0-9-1 through `amqp-client` 5.x, which RabbitMQ 4.x supports. Stage 6 confirms this with a
  connection smoke test.
- **shadcn/ui with Next.js 16.4:** shadcn publishes no per-version Next.js matrix. Stage 3 verifies
  it with `npm run build` and `npm run lint`.
- **TypeScript 6.0 with `create-next-app` output:** Next.js documents TypeScript 6 support, but the
  generator defaults to `^5`. Stage 3 verifies the override with a clean build and lint.

## Alternatives considered

- **Spring Boot 3.5 / 4.0:** open-source support has ended or ends in 2026. Rejected.
- **Java 25 LTS:** supported by Boot 4.1, but there is no compelling reason to move away from 21,
  which has wide IDE and tutorial support.
- **Maven 3.10.0:** recommended upstream but released four weeks before this decision. 3.9.16 is
  maintained and well proven. Moving to 3.10.x later means changing one line in
  `.mvn/wrapper/maven-wrapper.properties`.
- **TypeScript 7.0 / ESLint 10:** incompatible with the current lint toolchain (see evidence).
- **RabbitMQ 4.4:** newer support window, but its first release was four days old. Revisit after
  4.4.1 or later, or before 4.3 community support ends on 2027-01-31. The change is one image tag.
- **PostgreSQL 19:** release candidate only.

## Consequences

- Students following Spring Boot 3 tutorials must adjust starter names, Jackson packages and test
  annotations. [CLAUDE.md](../../CLAUDE.md) lists the differences.
- springdoc-openapi must be re-checked against its compatibility matrix whenever Spring Boot is upgraded.
- Before 2027-01-31, plan the RabbitMQ 4.3 → 4.4 image update.
- Before 2027-07-31, plan the Spring Boot 4.1 → 4.2 upgrade if the project is still active.
