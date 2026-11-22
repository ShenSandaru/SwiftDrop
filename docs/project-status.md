# SwiftDrop Project Status

**Last Updated:** 2026-11-22  
**Reference Branch:** `dev` / `main`  
**Verified Commit:** `a71d1b8` (PR #171)

---

## 1. Project Overview

SwiftDrop is a parcel delivery and courier management system built as a modular monolith (Spring Boot 4 / Java 21 backend, Next.js 16 / React 19 frontend, PostgreSQL 18 with Flyway, and RabbitMQ 4.3).

## 2. Current Development Phase

Core feature development across all primary user roles (Customer, Merchant, Courier, Dispatcher, Admin) and supporting infrastructure (Outbox, RabbitMQ, SSE Tracking, Notifications, AI Assistant) is **implemented and integrated** on `feat/dev_merchant` (PR #131), pending final merge into `dev` and production/external provider configuration.

---

## 3. Implemented Modules & Features

| Module / Area | Status | Details |
|---|---|---|
| `identity` | **Implemented and tested** | Email/password registration, JWT auth, Google OAuth2 exchange, user profiles, avatars, saved addresses, admin user management. |
| `pricing` | **Implemented and tested** | Zones, zone-pair pricing rules, fee calculator, admin zone/pricing management (`/admin/pricing`). |
| `delivery` | **Implemented and tested** | Parcel booking, lifecycle transitions, hub network (`hubs`) with direct links between branches (`hub_links`, ADR 0011) and home hubs for staff (`hub_staff`, ADR 0012), delivery proofs, receipts, transactional outbox relay. |
| `dispatch` | **Implemented and tested** | Courier assignment, handoff history, hub queries, stale parcel list, hub-scoped lists and actions (ADR 0012), concurrency control (`/dispatch`). |
| `tracking` | **Implemented and tested** | Public parcel tracking and real-time Server-Sent Events (SSE) stream (`/track`), rate limited per client, with an estimated delivery day. |
| `merchant` | **Implemented and tested** | Merchant registration, admin approval lifecycle, API keys (`sdk_...`), order API, SSRF-safe webhooks, RabbitMQ consumer, redelivery retries (`/merchant/*`). |
| `notification` | **Implemented and tested** | RabbitMQ event listener (parcel + merchant status), in-app notifications (`/notifications`), reconciliation service. |
| `assistant` | **Implemented and tested** | Gemini-powered delivery assistant and MCP server (`track_parcel`, `estimate_fee`, `list_service_areas`) with rate limiting. |
| `config` & `shared` | **Implemented and tested** | Spring Security filter chains, CORS, JWT/API-key auth, global exception handling, pagination. |
| Frontend Portals | **Implemented and tested** | 30 Next.js routes across Customer, Merchant, Courier, Dispatcher, Admin, Auth, Tracking, Profile, and Notifications. |
| CI/CD & Deployment | **Implemented and verified** | GitHub Actions (`ci.yml`, `docker-publish.yml`), multi-stage Dockerfiles, GHCR publishing, automated GCP VM deployment (`compose.prod.yaml`, Nginx, `deploy/scripts/deploy-prod.sh`), and K3s manifests (`swiftdrop-k8s/`, ADR 0013). See [docs/deployment/](deployment/README.md). |

---

## 4. Partially Implemented / Blocked Functionality

- **SMS & Email Notification Channels:** **Blocked / Mocked** — Notification persistence and in-app delivery are implemented; email/push providers default to `mock` unless external credentials (`RESEND_API_KEY`, Firebase service account) are configured.
- **Production Deployment:** **Implemented** — Containerized production deployment (`compose.prod.yaml` + Nginx + Let's Encrypt), GHCR image publishing, and automated GCP VM deployment via GitHub Actions (`production` environment) are implemented and verified on `main` (PR #170, PR #171). Separate K3s manifests (`swiftdrop-k8s/`) are prepared for `BON-1` ([docs/deployment/](deployment/README.md)).

---

## 5. Known Technical Debt & Documentation Notes

- **ADR Numbering Collision:** Two ADRs share number `0008` (`0008-dispatch-assignment-history-and-handoff.md` and `0008-merchant-api-key-authentication.md`).
- **Proposed ADRs:** ADRs `0006`, `0007`, and `0010` remain marked `Proposed` pending formal team sign-off, even though their corresponding features are implemented.

---

## 6. External Configuration Requirements

Running the full application locally requires `.env` (copied from `.env.example`) with:
- PostgreSQL & RabbitMQ credentials (`SWIFTDROP_POSTGRES_*`, `SWIFTDROP_RABBITMQ_*`)
- `SWIFTDROP_JWT_SECRET` (minimum 32 bytes)
- `SWIFTDROP_WEBHOOK_SECRET_KEY` (minimum 32 bytes)
- Optional: `SWIFTDROP_GOOGLE_CLIENT_ID` / `SWIFTDROP_GOOGLE_CLIENT_SECRET` for Google OAuth2
- Optional: Gemini API key for the AI assistant

---

## 7. Testing & Verification Results (Commit `9d85c27`)

Per [docs/pr-reviews/PR-131-FULL-TEST-REPORT.md](pr-reviews/PR-131-FULL-TEST-REPORT.md):
- **Backend Unit Tests (`.\mvnw.cmd test`):** 1,354 run, 1,354 passed, 0 failed, 0 skipped.
- **Backend Integration Tests (`.\mvnw.cmd verify -Pintegration`):** 181 run across 24 `*IT.java` classes (PostgreSQL 18 & RabbitMQ 4.3 Testcontainers), 181 passed, 0 failed, 0 skipped.
- **Flyway Migrations:** All 28 migrations verified on clean PostgreSQL 18 instances.
- **Frontend:** `npm ci`, `npx tsc --noEmit`, `npm run lint`, and `npm run build` (30 routes) all passed with 0 errors.

---

## 8. Remaining Work & Recommended Next Milestones

1. Merge PR #131 (`feat/dev_merchant` -> `dev`) after CI verification.
2. Formally review and accept ADRs `0006`, `0007`, and `0010` (and resolve `0008` numbering if desired).
3. Configure production hosting and real SMS/email provider credentials if deploying beyond local Docker Compose.
