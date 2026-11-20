# SwiftDrop Kubernetes (K3s) Deployment Guide

> **Important Deployment Distinction:**
> SwiftDrop's primary production deployment on the **1 GB RAM GCP `e2-micro` VM** uses **Docker Compose** ([docs/production-deployment.md](production-deployment.md)).
> **Do not install or run K3s on the 1 GB `e2-micro` VM.** The manifests under [`swiftdrop-k8s/`](../swiftdrop-k8s/) are a separate single-node Kubernetes deployment option prepared for the EAD assignment bonus (`BON-1`, [ADR 0013](adr/0013-containerized-and-k3s-deployment.md)) on a VM with at least **2 vCPU / 4 GB RAM** (recommended **8 GB RAM**).

---

## 1. Prerequisites for a K3s Cluster

* Ubuntu 24.04 LTS or Debian 13 VM (x64 `amd64`) with **minimum 2 vCPU / 4 GB RAM / 30 GB SSD** (recommended **2–4 vCPU / 8 GB RAM / 64 GB SSD**).
* Inbound TCP **80** and **443** open to the internet (do **not** expose `5432`, `5672`, `8080`, or `3000`).
* Prebuilt GHCR images published by [`.github/workflows/docker-publish.yml`](../.github/workflows/docker-publish.yml):
  * `ghcr.io/shensandaru/swiftdrop-frontend:latest`
  * `ghcr.io/shensandaru/swiftdrop-backend:latest`

### Install K3s on Ubuntu / Debian

```bash
curl -sfL https://get.k3s.io | sh -
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown "$(id -u):$(id -g)" ~/.kube/config
export KUBECONFIG=~/.kube/config
kubectl get nodes
kubectl get storageclass
```

Verify `local-path` is listed as the default StorageClass and `traefik` is running in `kube-system`.

---

## 2. Create Namespace & Configure Secrets

1. **Create the `swiftdrop` namespace:**

   ```bash
   kubectl apply -f swiftdrop-k8s/namespace.yaml
   ```

2. **Create application Secrets** (never commit real secrets to Git):

   ```bash
   cp swiftdrop-k8s/secrets/swiftdrop-secrets.yaml.example swiftdrop-k8s/secrets/swiftdrop-secrets.yaml
   chmod 600 swiftdrop-k8s/secrets/swiftdrop-secrets.yaml
   ```

   Edit `swiftdrop-k8s/secrets/swiftdrop-secrets.yaml` and replace every `CHANGE_ME_*` value (generate 48-byte keys with `openssl rand -base64 48`), then apply:

   ```bash
   kubectl apply -f swiftdrop-k8s/secrets/swiftdrop-secrets.yaml
   ```

3. *(Only if GHCR packages are Private)* Create the image pull secret and uncomment `imagePullSecrets` in `backend-deployment.yaml` and `frontend-deployment.yaml`:

   ```bash
   kubectl -n swiftdrop create secret docker-registry ghcr-pull-secret \
     --docker-server=ghcr.io \
     --docker-username=ShenSandaru \
     --docker-password="$GHCR_READ_TOKEN"
   ```

---

## 3. Configure Environment & Persistent Storage

1. Edit [`swiftdrop-k8s/configmaps/swiftdrop-config.yaml`](../swiftdrop-k8s/configmaps/swiftdrop-config.yaml) and update `SWIFTDROP_FRONTEND_BASE_URL` and `SWIFTDROP_CORS_ALLOWED_ORIGINS` to your domain (or `http://<VM_IP>` for initial HTTP testing).
2. Edit [`swiftdrop-k8s/ingress/swiftdrop-ingress.yaml`](../swiftdrop-k8s/ingress/swiftdrop-ingress.yaml) and replace `swiftdrop.example.com` with your domain (or remove the `host:` / `tls:` entries for initial HTTP testing on a raw IP).
3. Persistent storage is automatically provisioned by K3s's `local-path` StorageClass via StatefulSet `volumeClaimTemplates`:
   * **PostgreSQL 18:** `5Gi` PVC mounted at `/var/lib/postgresql` (`postgres-data-postgres-0`)
   * **RabbitMQ 4.3:** `2Gi` PVC mounted at `/var/lib/rabbitmq` (`rabbitmq-data-rabbitmq-0`)

---

## 4. Deploy All Services

Apply the manifests in dependency order:

```bash
kubectl apply -f swiftdrop-k8s/namespace.yaml
kubectl apply -f swiftdrop-k8s/storage/
kubectl apply -f swiftdrop-k8s/configmaps/
kubectl apply -f swiftdrop-k8s/secrets/swiftdrop-secrets.yaml
kubectl apply -f swiftdrop-k8s/database/
kubectl apply -f swiftdrop-k8s/rabbitmq/

# Wait for PostgreSQL and RabbitMQ to become Ready before starting the backend
kubectl -n swiftdrop rollout status statefulset/postgres --timeout=180s
kubectl -n swiftdrop rollout status statefulset/rabbitmq --timeout=180s

kubectl apply -f swiftdrop-k8s/backend/
kubectl -n swiftdrop rollout status deployment/backend --timeout=240s

kubectl apply -f swiftdrop-k8s/frontend/
kubectl -n swiftdrop rollout status deployment/frontend --timeout=120s

kubectl apply -f swiftdrop-k8s/ingress/
```

---

## 5. Enable HTTPS with cert-manager (Let's Encrypt)

To issue automatic Let's Encrypt TLS certificates for the Ingress:

```bash
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.17.2/cert-manager.yaml
kubectl -n cert-manager rollout status deployment/cert-manager --timeout=180s
```

Create a `ClusterIssuer` named `letsencrypt-prod` (matching the annotation in `swiftdrop-ingress.yaml`):

```bash
cat <<'EOF' | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    email: you@example.com
    server: https://acme-v02.api.letsencrypt.org/directory
    privateKeySecretRef:
      name: letsencrypt-prod-account-key
    solvers:
      - http01:
          ingress:
            class: traefik
EOF
```

Verify certificate issuance:

```bash
kubectl -n swiftdrop get certificate,order,challenge
```

---

## 6. Verify Health & Access the Application

```bash
kubectl -n swiftdrop get pods,svc,pvc,ingress
```

Verify backend Flyway migrations and health:

```bash
kubectl -n swiftdrop logs deployment/backend --tail=50
curl -fsS https://your-domain.example.com/actuator/health
```

Access the web portals at `https://your-domain.example.com/` (or `http://<VM_IP>/` if testing without TLS).

---

## 7. Database Backups on K3s

Run logical `pg_dump` backups directly from the PostgreSQL pod:

```bash
kubectl -n swiftdrop exec statefulset/postgres -- \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' \
  > "swiftdrop-k8s-$(date +%Y%m%d-%H%M%S).dump"
```

---

## 8. Updating Images & Rolling Back

Update to a new pinned image tag (e.g., `sha-abc1234` or `1.0.0`):

```bash
kubectl -n swiftdrop set image deployment/backend backend=ghcr.io/shensandaru/swiftdrop-backend:sha-abc1234
kubectl -n swiftdrop set image deployment/frontend frontend=ghcr.io/shensandaru/swiftdrop-frontend:sha-abc1234
kubectl -n swiftdrop rollout status deployment/backend --timeout=240s
kubectl -n swiftdrop rollout status deployment/frontend --timeout=120s
```

Roll back to the previous revision:

```bash
kubectl -n swiftdrop rollout undo deployment/backend
kubectl -n swiftdrop rollout undo deployment/frontend
```

---

## 9. Troubleshooting Common Problems

* **Backend `CrashLoopBackOff`:** Inspect logs (`kubectl -n swiftdrop logs deployment/backend --previous`). Common causes: missing or <32-byte `SWIFTDROP_JWT_SECRET` / `SWIFTDROP_WEBHOOK_SECRET_KEY`, or PostgreSQL/RabbitMQ credentials mismatching an existing PVC.
* **`ImagePullBackOff`:** Verify GHCR packages are Public or `ghcr-pull-secret` is created and referenced in both Deployments.
* **PVC stuck in `Pending`:** Run `kubectl -n swiftdrop describe pvc` and confirm `storageClassName: local-path` matches `kubectl get storageclass`.
* **Google OAuth2 redirect uses `http://`:** Confirm `SPRING_PROFILES_ACTIVE=prod` is set in `swiftdrop-config` (enables `server.forward-headers-strategy=framework`) and Traefik is terminating HTTPS.
* **SSE live tracking disconnects or buffers:** Confirm `swiftdrop-sse-ingress` is applied (`kubectl -n swiftdrop get ingress`) so `/api/v1/tracked-parcels` bypasses `swiftdrop-upload-limit` buffering.
