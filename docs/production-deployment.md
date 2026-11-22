# SwiftDrop Production Deployment Guide (Docker Compose + Nginx)

This guide covers deploying SwiftDrop on a single Linux VM (targeted at a **GCP Debian 13 `e2-micro` VM** with 1 GB RAM + 2 GB swap) using [`compose.prod.yaml`](../compose.prod.yaml), Nginx, and Let's Encrypt HTTPS.

Local development continues to use [`compose.yaml`](../compose.yaml) and [`.env.example`](../.env.example); see [docs/development-setup.md](development-setup.md). For the full CI/CD workflow architecture, GitHub environment configuration, and image tagging matrix, see [docs/ci-cd-and-deployment.md](ci-cd-and-deployment.md).

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

## 3. Pulling Prebuilt Images, Starting & Stopping

Images are built and published to GitHub Container Registry (GHCR) by [`.github/workflows/docker-publish.yml`](../.github/workflows/docker-publish.yml):

* **Pull requests (`main`, `dev`):** Validates Docker builds only (no image push).
* **Push to `main`:** Publishes `main`, `latest`, and `sha-<commit>`.
* **Push to `dev`:** Publishes `dev` and `sha-<commit>`.
* **Version tag (`vX.Y.Z`):** Publishes `X.Y.Z`, `X.Y`, and `sha-<commit>`.

If the GHCR packages are private, authenticate once on the VM with a GitHub Personal Access Token (`read:packages` scope):

```bash
echo "$GHCR_READ_TOKEN" | docker login ghcr.io -u ShenSandaru --password-stdin
```

Validate the Compose configuration first:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml config --quiet
```

Pull prebuilt images and start all 5 services (no local build occurs on the VM):

```bash
docker compose --env-file .env.prod -f compose.prod.yaml pull
docker compose --env-file .env.prod -f compose.prod.yaml up -d
```

To roll back to a previous release, set `SWIFTDROP_IMAGE_TAG=sha-<previous-commit>` in `.env.prod` and run `pull` + `up -d`.

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

---

## 8. Automatic Production Deployment via GitHub Actions (GCP VM)

Pushing to `main` triggers [`.github/workflows/docker-publish.yml`](../.github/workflows/docker-publish.yml), which builds and publishes both images to GHCR and then runs the `deploy-production` job over SSH on the GCP VM (`/opt/swiftdrop`).

### Branch & Trigger Behavior

| Event | Behavior |
|---|---|
| Pull request (`main`, `dev`) | Validates Docker builds only (`push: false`); **never deploys** |
| Push to `dev` | Builds and publishes `dev` + `sha-<commit>` images; **never deploys production** |
| Push to `main` | Builds and publishes `main`, `latest`, + `sha-<commit>` images, then **automatically deploys `sha-<commit>`** to the GCP VM |
| Version tag (`v*.*.*`) | Publishes versioned images (`X.Y.Z`, `X.Y`, `sha-<commit>`); does **not** auto-deploy |
| Manual `workflow_dispatch` on `main` | Deploys only if `deploy_production` input is explicitly checked (`true`) |

### GitHub Actions Secrets vs VM `.env.prod`

Keep deployment credentials strictly separated from runtime application secrets:

* **Stored ONLY in GitHub Actions Secrets** (Settings → Secrets and variables → Actions, or `production` Environment secrets):
  * `GCP_VM_HOST` — Public IP or DNS name of the GCP VM (e.g., `34.60.94.116`)
  * `GCP_VM_USER` — Dedicated Linux deployment user on the VM (e.g., `swiftdrop-deploy`)
  * `GCP_VM_SSH_PRIVATE_KEY` — Private key of the dedicated Ed25519 deployment SSH key
  * `GCP_VM_SSH_KNOWN_HOSTS` — Verified SSH host key line(s) for strict host-key verification
  * `GHCR_USERNAME` *(optional)* — GitHub username, only if GHCR packages are Private
  * `GHCR_TOKEN` *(optional)* — PAT with `read:packages` scope, only if GHCR packages are Private
* **Stored ONLY on the GCP VM in `/opt/swiftdrop/.env.prod`** (`chmod 600`, git-ignored):
  * PostgreSQL/RabbitMQ passwords, `SWIFTDROP_JWT_SECRET`, `SWIFTDROP_WEBHOOK_SECRET_KEY`, bootstrap admin password, OAuth2/Gemini/Resend/FCM credentials.

### One-Time SSH & VM Setup

1. **Generate a dedicated deployment SSH key pair** (on your local workstation, not a personal key):

   ```bash
   ssh-keygen -t ed25519 -C "swiftdrop-github-actions-deploy" -f ~/.ssh/swiftdrop_deploy_ed25519 -N ""
   ```

2. **Install the public key on the GCP VM** (add `~/.ssh/swiftdrop_deploy_ed25519.pub` to `/home/<GCP_VM_USER>/.ssh/authorized_keys` with `chmod 700 ~/.ssh && chmod 600 ~/.ssh/authorized_keys`, and ensure `<GCP_VM_USER>` is in the `docker` group and owns `/opt/swiftdrop`):

   ```bash
   sudo usermod -aG docker <GCP_VM_USER>
   sudo chown -R <GCP_VM_USER>:<GCP_VM_USER> /opt/swiftdrop
   ```

3. **Capture the verified VM host key** (run on a trusted machine or directly from the VM's `/etc/ssh/ssh_host_ed25519_key.pub`):

   ```bash
   ssh-keyscan -t ed25519 -H <GCP_VM_HOST>
   ```

4. **Add the GitHub Secrets** in **Settings → Secrets and variables → Actions** (or under the `production` Environment):
   * Paste the contents of `~/.ssh/swiftdrop_deploy_ed25519` into `GCP_VM_SSH_PRIVATE_KEY`.
   * Paste the `ssh-keyscan` output into `GCP_VM_SSH_KNOWN_HOSTS`.
   * Set `GCP_VM_HOST` and `GCP_VM_USER` (and `GHCR_USERNAME` / `GHCR_TOKEN` if packages are Private).
   * Delete the local copy of the private key once stored if not needed locally.

5. *(Only if the GitHub repository itself is Private)* **Configure a read-only GitHub Deploy Key on the VM** so `git fetch origin main` succeeds without interactive prompts or write-scoped tokens:

   ```bash
   # On the GCP VM as <GCP_VM_USER>:
   ssh-keygen -t ed25519 -C "swiftdrop-vm-readonly-git" -f ~/.ssh/github_deploy_ed25519 -N ""
   cat ~/.ssh/github_deploy_ed25519.pub
   ```

   * In GitHub, open **Settings → Deploy keys → Add deploy key**, paste the public key, and leave **Allow write access** **unchecked** (read-only).
   * Configure `~/.ssh/config` on the VM for `github.com` using `~/.ssh/github_deploy_ed25519`, add GitHub's host key (`ssh-keyscan -t ed25519 github.com >> ~/.ssh/known_hosts`), and point the VM clone to SSH:

   ```bash
   cd /opt/swiftdrop
   git remote set-url origin git@github.com:ShenSandaru/SwiftDrop.git
   GIT_TERMINAL_PROMPT=0 git fetch origin main
   ```

### Checking Deployment Status, Manual Deploy & Rollback

* **Check CI/CD status:** Open the **Actions → Docker Publish** tab in GitHub and inspect the `deploy-gcp-production` job logs.
* **Inspect live VM logs:**
  ```bash
  cd /opt/swiftdrop
  docker compose --env-file .env.prod -f compose.prod.yaml ps
  docker compose --env-file .env.prod -f compose.prod.yaml logs --tail=100 backend frontend nginx
  ```
* **Manual deployment on the VM** (using an immutable SHA tag already published to GHCR):
  ```bash
  cd /opt/swiftdrop
  git fetch origin main && git checkout --force origin/main
  bash deploy/scripts/deploy-prod.sh sha-<7-char-commit>
  ```
* **Automatic & manual rollback:**
  * Before checking out the new commit, the workflow captures `PREVIOUS_COMMIT` (`git rev-parse HEAD`), and [`deploy/scripts/deploy-prod.sh`](../deploy/scripts/deploy-prod.sh) records `PREVIOUS_TAG` from `.env.prod`. If `compose config`, image pull, `compose up`, Nginx `nginx -t` / reload, container health checks, or HTTP checks fail, the script automatically checks out `PREVIOUS_COMMIT`, restores `.env.prod`, recreates the previous containers, validates and reloads Nginx, verifies health, and exits non-zero so GitHub Actions flags the failure.
  * To roll back manually at any time, run `git checkout --force <previous-commit>` and `bash deploy/scripts/deploy-prod.sh sha-<previous-commit>`.
  * **Database migration caveat:** Flyway migrations are **not** automatically rolled back because schema changes may not be backward-compatible. Always take a `pg_dump` backup (Section 5) before merging destructive or irreversible migrations into `main`.

### Troubleshooting Automatic Deployments

* **SSH `Host key verification failed`:** Regenerate `GCP_VM_SSH_KNOWN_HOSTS` from the VM's `/etc/ssh/ssh_host_ed25519_key.pub` or `ssh-keyscan -t ed25519 -H <GCP_VM_HOST>`.
* **SSH `Permission denied (publickey)`:** Confirm the public key is in `/home/<GCP_VM_USER>/.ssh/authorized_keys` (`chmod 600`), `GCP_VM_USER` matches the VM account, and `GCP_VM_SSH_PRIVATE_KEY` was pasted completely without extra whitespace.
* **GHCR `unauthorized` / `denied`:** Either make `swiftdrop-frontend` and `swiftdrop-backend` packages Public in GitHub Packages, or set `GHCR_USERNAME` + `GHCR_TOKEN` (`read:packages`) in GitHub Secrets.
* **Container health check timeout:** Check `docker compose --env-file .env.prod -f compose.prod.yaml logs --tail=100 backend` and `free -h` on the VM; heavy swap pressure on `e2-micro` can slow JVM startup beyond 180 s (override `HEALTH_TIMEOUT_SECONDS` if needed, or temporarily scale the VM).
