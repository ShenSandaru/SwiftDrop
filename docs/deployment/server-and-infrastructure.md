# Production Server & Infrastructure Details

**Last Updated:** 2026-11-22  
**Compose Stack:** [`compose.prod.yaml`](../../compose.prod.yaml) (project name: `swiftdrop-prod`)  
**Environment Template:** [`.env.prod.example`](../../.env.prod.example)

---

## 1. Current Production Server Specifications

SwiftDrop's live production environment runs on a single Linux virtual machine hosted on **Google Cloud Platform (GCP)**.

| Attribute | Current Production Value |
|---|---|
| **Cloud Provider** | Google Cloud Platform (GCP Compute Engine) |
| **Machine Type** | `e2-micro` (2 shared vCPUs, `linux/amd64`) |
| **Operating System** | **Debian 13** |
| **Physical RAM** | **1 GB** |
| **Swap Space** | **2 GB** (required; verified via `swapon --show`) |
| **Container Runtime** | Docker Engine + Docker Compose plugin |
| **Deployment Directory** | `/opt/swiftdrop` (owned by dedicated Linux deploy user, e.g. `swiftdrop-deploy`, member of `docker` group) |
| **Image Build Policy** | **Never build on the VM.** Prebuilt `linux/amd64` images are pulled from GitHub Container Registry (`ghcr.io`). |

### Firewall & Exposed Ports (GCP VPC)

| Port | Protocol | Exposure | Purpose |
|---|---|---|---|
| `80` | TCP | Public | HTTP traffic & Let's Encrypt ACME HTTP-01 challenge (`/.well-known/acme-challenge/`) |
| `443` | TCP | Public | HTTPS traffic terminated by Nginx |
| `22` | TCP | Restricted (SSH) | Automated GitHub Actions deployment & admin access (Ed25519 key auth) |
| `3000`, `8080`, `5432`, `5672`, `15672` | TCP | **Closed / Internal only** | Never exposed to the internet |

---

## 2. Container Stack & Resource Tuning (`compose.prod.yaml`)

All 5 services run with `restart: unless-stopped` and JSON log rotation (`max-size: 10m`, `max-file: 3`).

| Service | Image | Docker Networks | Default `e2-micro` Memory / Swap Cap | Health Check |
|---|---|---|---|---|
| `postgres` | `postgres:18-alpine` | `internal` | `256m` / `512m` | `pg_isready` (`10s` interval, `30s` start) |
| `rabbitmq` | `rabbitmq:4.3-alpine` *(no management UI)* | `internal` | `384m` / `768m` | `rabbitmq-diagnostics check_port_connectivity` (`15s` interval, `40s` start) |
| `backend` | `ghcr.io/shensandaru/swiftdrop-backend:<tag>` | `internal`, `edge` | `512m` / `1024m` | `GET http://127.0.0.1:8080/actuator/health` (`20s` interval, `90s` start) |
| `frontend` | `ghcr.io/shensandaru/swiftdrop-frontend:<tag>` | `edge` | `256m` / `512m` | `GET http://127.0.0.1:3000/` (`20s` interval, `30s` start) |
| `nginx` | `nginx:1.27-alpine` | `edge` | `64m` / `128m` | `GET http://127.0.0.1/nginx-health` (`20s` interval, `15s` start) |

### Low-Memory Runtime Tuning (`e2-micro` Defaults)

* **Backend JVM (`JAVA_TOOL_OPTIONS`):** `-Xms128m -Xmx288m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError`
* **Frontend Node.js (`NODE_OPTIONS`):** `--max-old-space-size=192`
* **PostgreSQL 18:** `shared_buffers=64MB`, `work_mem=2MB`, `maintenance_work_mem=32MB`, `max_connections=40`
* **RabbitMQ 4.3:** `-rabbit vm_memory_high_watermark 0.7`, fixed hostname `rabbitmq` to preserve Mnesia/Raft state across container recreations
* **Spring Profile:** `SPRING_PROFILES_ACTIVE=prod` (enables `server.forward-headers-strategy=framework`, graceful shutdown, and disables Swagger UI by default)

> **Scaling to 4 GB / 8 GB VMs:** `.env.prod.example` includes ready-to-uncomment profiles for 4 GB (`e2-medium`) and 8 GB VMs raising JVM heap (`-Xmx512m` / `-Xmx1024m`), PostgreSQL `shared_buffers` (`256MB` / `512MB`), and container limits.

---

## 3. Networking, Volumes & Nginx Reverse Proxy

* **Networks:**
  * `internal` (`internal: true`): connects `postgres`, `rabbitmq`, and `backend` only — no external egress/ingress.
  * `edge`: connects `nginx`, `frontend`, and `backend`.
* **Persistent Volumes:**
  * `postgres-data` → `/var/lib/postgresql` (PostgreSQL 18 data + `BYTEA` delivery proof photos and avatars)
  * `rabbitmq-data` → `/var/lib/rabbitmq` (durable queues and messages)
  * `certbot-webroot` → `/var/www/certbot` (ACME HTTP-01 challenge files)
  * `certbot-conf` → `/etc/letsencrypt` (Let's Encrypt TLS certificates)
* **Nginx Routing ([`deploy/nginx/snippets/swiftdrop-routes.conf`](../../deploy/nginx/snippets/swiftdrop-routes.conf)):**
  * Proxies `/api/`, `/actuator/health`, `/login/oauth2/`, `/oauth2/`, and `/mcp` to `backend:8080`.
  * Disables proxy buffering (`proxy_buffering off`) for Server-Sent Events (`/api/v1/tracked-parcels/*/events`).
  * Enforces a `3m` client upload limit matching Spring multipart limits.
  * Routes all other paths to `frontend:3000`.
  * Supports HTTP-only bootstrap ([`deploy/nginx/http.conf`](../../deploy/nginx/http.conf)) and Let's Encrypt HTTPS ([`deploy/nginx/https.conf.template`](../../deploy/nginx/https.conf.template) → git-ignored `deploy/nginx/https.conf`).

---

## 4. Container Images

* **Backend ([`backend/Dockerfile`](../../backend/Dockerfile)):** Multi-stage build (`eclipse-temurin:21-jdk-alpine` → custom `jlink` JRE including `jdk.random` for `WebhookSenderConfiguration` → `alpine:3.21` running as non-root user `swiftdrop`).
* **Frontend ([`frontend/Dockerfile`](../../frontend/Dockerfile)):** Multi-stage `node:24-alpine` build (`deps` → `builder` with Next.js `output: "standalone"` → `runner` running as non-root user `nextjs`, UID `1001`).
