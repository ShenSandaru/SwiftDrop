# 0013. Containerized production deployment and K3s manifests

- Status: Accepted
- Date: 2026-11-20

## Context

SwiftDrop requires containerized deployment of the frontend, backend, database and message broker
(`SYS-6`, `IMP-7`), and the assignment brief offers Kubernetes as an optional bonus (`BON-1`).
[ADR 0001](0001-modular-monolith-in-a-monorepo.md) excluded Kubernetes initially until a verified
need existed and required a separate, explicitly reviewed ADR before introducing it.

The team is now preparing SwiftDrop for:

1. Production deployment on a single low-memory Google Cloud Platform VM (`e2-micro`, 1 GB RAM +
   swap, Debian 13) using Docker Compose, Nginx and Let's Encrypt HTTPS.
2. Automated container image builds published to GitHub Container Registry (GHCR) via GitHub
   Actions, so images are never compiled on the small production VM.
3. Single-node Kubernetes (`K3s`) manifests in a dedicated directory (`swiftdrop-k8s/`) to satisfy
   the `BON-1` bonus requirement on a suitably sized VM without altering the modular-monolith
   application architecture.

## Decision

1. **Preserve the local development setup:** `compose.yaml` and `.env.example` remain focused on
   local PostgreSQL 18 and RabbitMQ 4.3 development without breaking developer workflows.
2. **Production multi-stage Dockerfiles:**
   - `frontend/Dockerfile`: multi-stage Node.js 24 Alpine build using Next.js `output: "standalone"`,
     non-root user (`nextjs`), and same-origin API routing support behind a reverse proxy.
   - `backend/Dockerfile`: multi-stage Eclipse Temurin 21 JDK → JRE Alpine build, non-root user
     (`swiftdrop`), container-aware JVM memory limits, and Actuator health checks.
3. **Production Spring profile (`application-prod.properties`):**
   - Activated via `SPRING_PROFILES_ACTIVE=prod`.
   - Enables `server.forward-headers-strategy=framework` so OAuth2 redirects and per-IP rate
     limiters work correctly behind Nginx or Kubernetes Ingress.
   - Enables graceful shutdown (`server.shutdown=graceful`), explicit Actuator liveness/readiness
     probes, and disables Swagger UI / OpenAPI exposure by default in production.
4. **Production Docker Compose (`compose.prod.yaml`) + Nginx:**
   - Runs Frontend, Backend, PostgreSQL 18, RabbitMQ 4.3, and Nginx on isolated Docker networks
     with persistent volumes, health checks, JSON log rotation, and memory limits tuned for
     low-memory single-VM hosting (with documented 4 GB / 8 GB alternatives).
   - PostgreSQL (`5432`) and RabbitMQ (`5672`, `15672`) are never exposed publicly.
   - Nginx supports initial HTTP testing on a public IP and HTTPS via Let's Encrypt / Certbot,
     with proxy buffering disabled for SSE live tracking (`/api/v1/tracked-parcels/*/events`) and
     a `3m` upload limit matching Spring multipart limits.
5. **CI/CD image publishing:** GitHub Actions builds and publishes frontend and backend images to
   GHCR using SHA-pinned actions and `GITHUB_TOKEN`.
6. **Kubernetes (`K3s`) manifests (`swiftdrop-k8s/`):**
   - Amends ADR 0001's exclusion of Kubernetes strictly to provide single-node K3s deployment
     manifests for `BON-1` (Namespace, ConfigMaps, Secret templates, Deployments, StatefulSets,
     ClusterIP Services, PVCs, and Ingress).
   - Because `LiveTracking` SSE streams and public rate limiters hold state in JVM memory, the
     backend remains a single-replica (`replicas: 1`) modular monolith.
   - Helm charts are deferred until the plain K3s manifests are verified.

## Alternatives considered

- **Building Docker images directly on the GCP `e2-micro` VM:** Rejected. `next build` (1.5–2.5 GB
  RAM) and Maven compilation (1–1.5 GB RAM) exceed `e2-micro` memory and risk OOM crashes.
- **Running K3s directly on the 1 GB `e2-micro` VM:** Rejected. K3s control-plane overhead plus all
  five application containers cannot run reliably in 1 GB RAM; production Compose is used on
  `e2-micro`, while K3s manifests target a larger VM (minimum 4 GB RAM, recommended 8 GB RAM).
- **Splitting into microservices for Kubernetes:** Rejected. Violates ADR 0001 and adds unnecessary
  complexity for no functional benefit.

## Consequences

- Local development (`compose.yaml`, `./mvnw spring-boot:run`, `npm run dev`) is unchanged.
- Prebuilt GHCR images can be deployed unchanged to either Docker Compose or K3s via same-origin
  reverse-proxy routing and environment variables.
- Running all five containers on a 1 GB RAM `e2-micro` VM relies on swap space and tight container
  memory caps; higher-traffic demonstrations should use a 4–8 GB VM.
- Secrets remain strictly outside Git (`.env.prod.example` and `swiftdrop-k8s/secrets/*.example`
  contain placeholders only).
