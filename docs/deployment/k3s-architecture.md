# Kubernetes (K3s) Deployment Architecture (`BON-1`)

**Last Updated:** 2026-11-22  
**Authoritative ADR:** [ADR 0013 — Containerized production deployment and K3s manifests](../adr/0013-containerized-and-k3s-deployment.md)  
**Manifests Directory:** [`swiftdrop-k8s/`](../../swiftdrop-k8s/)  
**Operational Runbook:** [docs/kubernetes-deployment.md](../kubernetes-deployment.md)

---

## 1. When K3s Is Used vs. Docker Compose

> **Do not install K3s on the 1 GB RAM GCP `e2-micro` VM.**

* **Current production (`e2-micro`, 1 GB RAM + 2 GB swap):** Uses Docker Compose (`compose.prod.yaml`) + Nginx.
* **K3s (`swiftdrop-k8s/`):** A separate single-node Kubernetes deployment prepared for the EAD assignment bonus (`BON-1`) on a larger Linux VM, consuming the **exact same prebuilt GHCR images** (`ghcr.io/shensandaru/swiftdrop-backend`, `ghcr.io/shensandaru/swiftdrop-frontend`) without altering the modular-monolith architecture.

---

## 2. Target K3s Cluster Specifications

| Attribute | Minimum | Recommended |
|---|---|---|
| **OS / Architecture** | Ubuntu 24.04 LTS or Debian 13 (`amd64`) | Ubuntu 24.04 LTS or Debian 13 (`amd64`) |
| **CPU** | 2 vCPU | 2–4 vCPU |
| **RAM** | 4 GB | 8 GB |
| **Disk** | 30 GB SSD | 64 GB SSD |
| **Kubernetes Distro** | Single-node **K3s** (bundled Traefik Ingress + `local-path` StorageClass) | Same |
| **Open Ports** | Inbound TCP `80`, `443` only (`5432`, `5672`, `8080`, `3000` closed) | Same |

---

## 3. Manifest Inventory (`swiftdrop-k8s/`)

| Path | Kind / Resource | Purpose |
|---|---|---|
| `namespace.yaml` | `Namespace` (`swiftdrop`) | Isolates all SwiftDrop workloads |
| `configmaps/swiftdrop-config.yaml` | `ConfigMap` (`swiftdrop-config`) | Non-secret config (`SPRING_PROFILES_ACTIVE=prod`, `SWIFTDROP_FRONTEND_BASE_URL`, `SWIFTDROP_CORS_ALLOWED_ORIGINS`, etc.) |
| `secrets/swiftdrop-secrets.yaml.example` | `Secret` template | Placeholder template for DB/RabbitMQ/JWT/webhook/API secrets (git-ignored once copied to `swiftdrop-secrets.yaml`) |
| `secrets/ghcr-pull-secret.yaml.example` | `Secret` template | Optional Docker registry pull secret if GHCR packages are Private |
| `storage/storage-notes.yaml` | Storage documentation | Documents K3s `local-path` StorageClass usage |
| `database/postgres-statefulset.yaml` & `postgres-service.yaml` | `StatefulSet` + `ClusterIP Service` | PostgreSQL 18 with `5Gi` PVC (`postgres-data-postgres-0`) mounted at `/var/lib/postgresql` |
| `rabbitmq/rabbitmq-statefulset.yaml` & `rabbitmq-service.yaml` | `StatefulSet` + `ClusterIP Service` | RabbitMQ 4.3 with `2Gi` PVC (`rabbitmq-data-rabbitmq-0`) mounted at `/var/lib/rabbitmq` |
| `backend/backend-deployment.yaml` & `backend-service.yaml` | `Deployment` (`replicas: 1`) + `ClusterIP Service` | Spring Boot modular monolith (`replicas: 1` per ADR 0013 because SSE streams and public rate limiters hold state in JVM memory) |
| `frontend/frontend-deployment.yaml` & `frontend-service.yaml` | `Deployment` + `ClusterIP Service` | Next.js standalone server on port `3000` |
| `ingress/traefik-middlewares.yaml` | Traefik `Middleware` | Enforces upload size limit (`swiftdrop-upload-limit`) |
| `ingress/swiftdrop-ingress.yaml` | `Ingress` (`traefik`) | Routes `/api/`, `/actuator/health`, OAuth2, and `/mcp` to `backend`, `/` to `frontend`, plus dedicated `swiftdrop-sse-ingress` for `/api/v1/tracked-parcels` so SSE bypasses buffering |

---

## 4. TLS & Image Updates on K3s

* **HTTPS:** Managed by `cert-manager` (`v1.17.2`) with a `letsencrypt-prod` `ClusterIssuer` using HTTP-01 via Traefik.
* **Rollouts & Rollbacks:** Deploy pinned GHCR tags via `kubectl -n swiftdrop set image deployment/backend ...` and `deployment/frontend ...`, and roll back with `kubectl -n swiftdrop rollout undo`.
* **Backups:** Logical `pg_dump` directly from `statefulset/postgres` (see [docs/kubernetes-deployment.md](../kubernetes-deployment.md#7-database-backups-on-k3s)).
