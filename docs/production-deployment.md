# SwiftDrop Production Deployment Guide (Docker Compose + Nginx)

This guide covers deploying SwiftDrop on a single Linux VM (targeted at a **GCP Debian 13 `e2-micro` VM** with 1 GB RAM + 2 GB swap) using [`compose.prod.yaml`](../compose.prod.yaml), Nginx, and Let's Encrypt HTTPS.

Local development continues to use [`compose.yaml`](../compose.yaml) and [`.env.example`](../.env.example); see [docs/development-setup.md](development-setup.md).

---

## 1. Prerequisites

* Docker Engine + Docker Compose plugin installed on the VM.
* On a 1 GB RAM `e2-micro` VM:
  * **At least 2 GB of swap** enabled (`swapon --show` to verify).
  * **Do not build Docker images on the VM.** Pull prebuilt images from GitHub Container Registry (GHCR) instead.
* GCP VPC firewall rules allowing inbound TCP **80** (HTTP) and **443** (HTTPS) only. Do **not** open ports `5432`, `5672`, `15672`, `8080`, or `3000`.

---

## 2. Environment Variable Configuration

Create `.env.prod` (git-ignored) from the committed template:

```bash
cp .env.prod.example .env.prod
chmod 600 .env.prod
```

Replace every `CHANGE_ME_*` placeholder:

| Variable | Required | Purpose |
|---|---|---|
| `SWIFTDROP_FRONTEND_BASE_URL` | Yes | Public origin (`http://<VM_IP>` initially, `https://<your-domain>` after TLS) |
| `SWIFTDROP_CORS_ALLOWED_ORIGINS` | Yes | Same public origin(s), comma-separated |
| `SWIFTDROP_POSTGRES_PASSWORD` | Yes | PostgreSQL password (applied only on first volume init) |
| `SWIFTDROP_RABBITMQ_PASSWORD` | Yes | RabbitMQ password (applied only on first volume init) |
| `SWIFTDROP_JWT_SECRET` | Yes | HS256 signing key, ≥ 32 bytes (`openssl rand -base64 48`) |
| `SWIFTDROP_WEBHOOK_SECRET_KEY` | Yes | AES-256-GCM key for merchant webhook secrets, ≥ 32 bytes (`openssl rand -base64 48`) |
| `SWIFTDROP_BOOTSTRAP_ADMIN_EMAIL` / `_PASSWORD` | Recommended | Creates initial `DISPATCHER_ADMIN` account on first boot |
| `SWIFTDROP_GOOGLE_CLIENT_ID` / `_SECRET` | Optional | Google OAuth2 sign-in (requires HTTPS domain redirect URI `https://<your-domain>/login/oauth2/code/google`) |
| `SWIFTDROP_GEMINI_API_KEY` | Optional | Enables the AI assistant (`POST /api/v1/assistant/messages`) |
| `SWIFTDROP_NOTIFICATION_EMAIL_PROVIDER` / `RESEND_API_KEY` | Optional | Set to `resend` for live emails (defaults to `mock`) |
| `SWIFTDROP_NOTIFICATION_PUSH_PROVIDER` / `SWIFTDROP_FIREBASE_SERVICE_ACCOUNT_PATH` | Optional | Set to `fcm` and bind-mount the service account JSON read-only into `backend` (defaults to `mock`) |

> **Larger VMs (4 GB or 8 GB RAM):** Uncomment the corresponding resource block at the bottom of `.env.prod` to raise JVM heap, Node.js heap, PostgreSQL `shared_buffers`, and container memory limits.

---

## 3. Starting and Stopping the Production Stack

Validate the Compose configuration first:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml config --quiet
```

Pull prebuilt images and start all 5 services:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml pull
docker compose --env-file .env.prod -f compose.prod.yaml up -d
```

Stop the stack **without deleting persistent data**:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml stop
# or remove containers while keeping named volumes:
docker compose --env-file .env.prod -f compose.prod.yaml down
```

> Never run `docker compose ... down -v` in production; `-v` deletes the `postgres-data`, `rabbitmq-data`, and `certbot-conf` volumes.

---

## 4. Inspecting Logs and Health Checks

Check container status and health checks:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml ps
```

Verify public endpoints through Nginx:

```bash
curl -fsS http://127.0.0.1/nginx-health
curl -fsS http://127.0.0.1/actuator/health
```

Inspect logs (Docker JSON log rotation is capped at `10m × 3` files per container):

```bash
docker compose --env-file .env.prod -f compose.prod.yaml logs -f --tail=100 backend
docker compose --env-file .env.prod -f compose.prod.yaml logs --tail=100 nginx frontend postgres rabbitmq
```

---

## 5. Backing Up and Restoring Persistent Data

PostgreSQL stores all application tables plus `BYTEA` delivery proof photos and user avatars in the `postgres-data` volume (mounted at `/var/lib/postgresql`).

### PostgreSQL logical backup (`pg_dump`)

```bash
mkdir -p backups
source .env.prod
docker compose --env-file .env.prod -f compose.prod.yaml exec -T postgres \
  pg_dump -U "$SWIFTDROP_POSTGRES_USER" -d "$SWIFTDROP_POSTGRES_DB" --format=custom \
  > "backups/swiftdrop-$(date +%Y%m%d-%H%M%S).dump"
```

### PostgreSQL restore

```bash
source .env.prod
docker compose --env-file .env.prod -f compose.prod.yaml exec -T postgres \
  pg_restore -U "$SWIFTDROP_POSTGRES_USER" -d "$SWIFTDROP_POSTGRES_DB" --clean --if-exists \
  < backups/swiftdrop-YYYYMMDD-HHMMSS.dump
```

Copy backups off the VM (for example to Google Cloud Storage or your local machine) so a VM disk loss does not lose database backups.

---

## 6. Configuring a Custom Domain and HTTPS (Let's Encrypt / Certbot)

Initial startup uses [`deploy/nginx/http.conf`](../deploy/nginx/http.conf) so you can test the application on `http://<VM_PUBLIC_IP>` immediately.

Once your domain's DNS `A` record points to the VM's public IP:

1. **Issue the Let's Encrypt certificate** using the shared `certbot-webroot` and `certbot-conf` volumes (while Nginx is running on port 80):

   ```bash
   docker run --rm \
     -v swiftdrop-prod_certbot-webroot:/var/www/certbot \
     -v swiftdrop-prod_certbot-conf:/etc/letsencrypt \
     certbot/certbot certonly --webroot \
     -w /var/www/certbot \
     -d your-domain.example.com \
     --email you@example.com \
     --agree-tos --no-eff-email
   ```

2. **Create your domain HTTPS config** from the template (`deploy/nginx/https.conf` is git-ignored):

   ```bash
   cp deploy/nginx/https.conf.template deploy/nginx/https.conf
   sed -i 's/YOUR_DOMAIN/your-domain.example.com/g' deploy/nginx/https.conf
   ```

3. **Update `.env.prod`:**

   ```dotenv
   SWIFTDROP_NGINX_CONF=./deploy/nginx/https.conf
   SWIFTDROP_FRONTEND_BASE_URL=https://your-domain.example.com
   SWIFTDROP_CORS_ALLOWED_ORIGINS=https://your-domain.example.com
   ```

   If using Google OAuth2, add `https://your-domain.example.com/login/oauth2/code/google` to the Authorized Redirect URIs in Google Cloud Console.

4. **Apply the HTTPS configuration:**

   ```bash
   docker compose --env-file .env.prod -f compose.prod.yaml up -d nginx backend
   ```

5. **Renew certificates automatically** (add a weekly cron job on the VM):

   ```bash
   docker run --rm \
     -v swiftdrop-prod_certbot-webroot:/var/www/certbot \
     -v swiftdrop-prod_certbot-conf:/etc/letsencrypt \
     certbot/certbot renew --quiet \
   && docker compose --env-file .env.prod -f compose.prod.yaml exec -T nginx nginx -s reload
   ```

---

## 7. Monitoring Memory & Swap on GCP `e2-micro`

With 1 GB physical RAM + 2 GB swap, monitor memory pressure regularly during testing and demos:

```bash
free -h
swapon --show
docker stats --no-stream
```

Watch for:

* **Swap exhaustion or heavy thrashing** (`vmstat 2 5` showing sustained `si`/`so` spikes).
* **OOM kills** in kernel logs (`dmesg -T | grep -i oom` or `docker inspect <container> --format '{{.State.OOMKilled}}'`).
* **Disk growth** from `BYTEA` proof photos and Docker images (`df -h` and `docker system df`).

If you observe frequent OOM kills or slow login/API responses under demo load, upgrade the VM temporarily to a 4 GB RAM machine (e.g., `e2-medium`) and switch `.env.prod` to the 4 GB profile.
