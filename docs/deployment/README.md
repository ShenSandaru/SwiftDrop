# SwiftDrop Deployment & CI/CD Documentation

**Last Updated:** 2026-11-22  
**Authoritative ADR:** [ADR 0013 — Containerized production deployment and K3s manifests](../adr/0013-containerized-and-k3s-deployment.md)

This directory contains the detailed technical records for SwiftDrop's production infrastructure, CI/CD pipelines, deployment automation scripts, and Kubernetes (`K3s`) deployment option.

## Contents

| Document | Scope |
|---|---|
| [Server & Infrastructure](server-and-infrastructure.md) | Current GCP `e2-micro` production server, Debian 13 OS, hardware/swap specs, firewall rules, 5-container Docker Compose stack (`compose.prod.yaml`), memory tuning, Nginx routing, and TLS |
| [CI/CD & Deployment Scripts](ci-cd-and-scripts.md) | GitHub Actions workflows (`ci.yml`, `docker-publish.yml`), live GitHub environment/secrets/variables configuration, and how `deploy/scripts/deploy-prod.sh` executes and rolls back |
| [Kubernetes (K3s) Details](k3s-architecture.md) | Single-node K3s deployment option (`BON-1`), target VM specs, `swiftdrop-k8s/` manifest inventory, Traefik/SSE routing, and `cert-manager` TLS |

## Operational Runbooks

* **Docker Compose (GCP VM) Runbook:** [docs/production-deployment.md](../production-deployment.md)
* **K3s (`BON-1`) Runbook:** [docs/kubernetes-deployment.md](../kubernetes-deployment.md)
* **Local Development Setup:** [docs/development-setup.md](../development-setup.md)

## Deployment Summary

| Environment | Orchestrator | Target Host | Image Source | Auto-Deploy |
|---|---|---|---|---|
| **Local Development** | Docker Compose (`compose.yaml`) | Developer workstation | Local `mvnw` + `npm run dev`; Postgres & RabbitMQ in Docker | No |
| **Production (Current)** | Docker Compose (`compose.prod.yaml`) + Nginx | **GCP `e2-micro` VM** (Debian 13, 1 GB RAM + 2 GB swap) | GHCR (`ghcr.io/shensandaru/swiftdrop-*`) | **Yes** (push to `main` via GitHub Actions `production` environment) |
| **Kubernetes Bonus (`BON-1`)** | Single-node **K3s** (`swiftdrop-k8s/`) | Dedicated Linux VM (**min 2 vCPU / 4 GB RAM**, rec. 8 GB RAM) | Same GHCR images | Manual (`kubectl apply` / `kubectl set image`) |
