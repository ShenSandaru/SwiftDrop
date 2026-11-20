#!/usr/bin/env bash
# Production deployment script for SwiftDrop on a single Linux VM (GCP Debian 13 e2-micro).
# Executed over SSH by .github/workflows/docker-publish.yml or manually on the VM.
#
# Usage:
#   bash deploy/scripts/deploy-prod.sh <image-tag>
#
# Optional environment variables:
#   SWIFTDROP_DEPLOY_DIR    Repository directory on the VM (default: /opt/swiftdrop)
#   PREVIOUS_COMMIT         40-char Git SHA deployed before checkout (for full config rollback)
#   GHCR_USERNAME           GitHub username for private GHCR packages (optional)
#   GHCR_TOKEN_ON_STDIN     Set to "1" when piping GHCR_TOKEN via stdin (never passed as CLI arg)
#   HEALTH_TIMEOUT_SECONDS  Max seconds to wait for all 5 containers to become healthy (default: 180)
#
# Safety guarantees:
# - Never runs `docker compose down -v` or deletes persistent volumes.
# - Never builds Docker images on the VM (`--no-build`).
# - Preserves `.env.prod` secrets and updates only `SWIFTDROP_IMAGE_TAG`.
# - Validates Nginx config (`nginx -t`) before reloading (`nginx -s reload`).
# - Automatically rolls back to the previous Git commit and image tag if checks fail.
# - Does NOT automatically roll back Flyway database migrations.

set -euo pipefail

IMAGE_TAG="${1:-}"
DEPLOY_DIR="${SWIFTDROP_DEPLOY_DIR:-/opt/swiftdrop}"
PREVIOUS_COMMIT="${PREVIOUS_COMMIT:-}"
COMPOSE_FILE="compose.prod.yaml"
ENV_FILE=".env.prod"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-180}"

if [[ -z "${IMAGE_TAG}" ]]; then
  echo "ERROR: Missing required argument <image-tag> (e.g. sha-083515f)." >&2
  exit 2
fi

if [[ ! "${IMAGE_TAG}" =~ ^[a-zA-Z0-9._-]+$ ]]; then
  echo "ERROR: Invalid image tag format: ${IMAGE_TAG}" >&2
  exit 2
fi

if [[ -n "${PREVIOUS_COMMIT}" && ! "${PREVIOUS_COMMIT}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "ERROR: Invalid PREVIOUS_COMMIT format (expected 40-char lowercase hex SHA)." >&2
  exit 2
fi

cd "${DEPLOY_DIR}"

TARGET_COMMIT="$(git rev-parse HEAD </dev/null 2>/dev/null || true)"
if [[ -n "${TARGET_COMMIT}" && ! "${TARGET_COMMIT}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "ERROR: Unexpected HEAD commit SHA format." >&2
  exit 1
fi

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "ERROR: ${DEPLOY_DIR}/${COMPOSE_FILE} not found." >&2
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "ERROR: ${DEPLOY_DIR}/${ENV_FILE} not found. Create it from .env.prod.example before deploying." >&2
  exit 1
fi

compose() {
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

read_env_value() {
  local key="$1"
  local line
  line="$(grep -E "^${key}=" "${ENV_FILE}" | tail -n 1 || true)"
  printf '%s' "${line#*=}"
}

set_image_tag_in_env() {
  local new_tag="$1"
  local tmp_file
  tmp_file="$(mktemp)"
  chmod 600 "${tmp_file}"
  if grep -q -E '^SWIFTDROP_IMAGE_TAG=' "${ENV_FILE}"; then
    awk -v tag="${new_tag}" '
      /^SWIFTDROP_IMAGE_TAG=/ { print "SWIFTDROP_IMAGE_TAG=" tag; next }
      { print }
    ' "${ENV_FILE}" > "${tmp_file}"
  else
    cat "${ENV_FILE}" > "${tmp_file}"
    printf '\nSWIFTDROP_IMAGE_TAG=%s\n' "${new_tag}" >> "${tmp_file}"
  fi
  mv "${tmp_file}" "${ENV_FILE}"
  chmod 600 "${ENV_FILE}"
}

wait_for_stack_health() {
  local timeout="$1"
  local elapsed=0
  local interval=5
  local services=(postgres rabbitmq backend frontend nginx)

  echo "Waiting up to ${timeout}s for all services to become healthy..."
  while (( elapsed < timeout )); do
    local all_healthy=1
    for svc in "${services[@]}"; do
      local cid status
      cid="$(compose ps -q "${svc}" 2>/dev/null || true)"
      if [[ -z "${cid}" ]]; then
        all_healthy=0
        break
      fi
      status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${cid}" 2>/dev/null || echo "unknown")"
      if [[ "${status}" != "healthy" ]]; then
        all_healthy=0
        break
      fi
    done

    if (( all_healthy == 1 )); then
      echo "All 5 services reported healthy after ${elapsed}s."
      return 0
    fi

    sleep "${interval}"
    elapsed=$((elapsed + interval))
  done

  echo "ERROR: Timed out after ${timeout}s waiting for services to become healthy." >&2
  return 1
}

verify_http_endpoints() {
  local http_port https_port base_url
  http_port="$(read_env_value "SWIFTDROP_HTTP_PORT")"
  https_port="$(read_env_value "SWIFTDROP_HTTPS_PORT")"
  base_url="$(read_env_value "SWIFTDROP_FRONTEND_BASE_URL")"
  http_port="${http_port:-80}"
  https_port="${https_port:-443}"

  echo "Verifying Nginx health endpoint..."
  curl -fsS --max-time 10 "http://127.0.0.1:${http_port}/nginx-health" | grep -q "ok"

  if [[ "${base_url}" == https://* ]]; then
    local host
    host="${base_url#https://}"
    host="${host%%/*}"
    host="${host%%:*}"
    echo "Verifying HTTPS endpoints through Nginx for host ${host}..."
    curl -fsS --max-time 15 --resolve "${host}:${https_port}:127.0.0.1" \
      "https://${host}:${https_port}/actuator/health" | grep -q '"status":"UP"'
    curl -fsS --max-time 15 -o /dev/null --resolve "${host}:${https_port}:127.0.0.1" \
      "https://${host}:${https_port}/"
  else
    echo "Verifying HTTP endpoints through Nginx on port ${http_port}..."
    curl -fsS --max-time 15 "http://127.0.0.1:${http_port}/actuator/health" | grep -q '"status":"UP"'
    curl -fsS --max-time 15 -o /dev/null "http://127.0.0.1:${http_port}/"
  fi

  echo "HTTP/HTTPS endpoint checks passed."
}

reload_nginx() {
  echo "Validating Nginx configuration..."
  if ! compose exec -T nginx nginx -t; then
    echo "ERROR: Nginx configuration test failed; skipping reload." >&2
    return 1
  fi
  echo "Reloading Nginx configuration..."
  compose exec -T nginx nginx -s reload
}

print_diagnostics() {
  echo "=== Container Status ===" >&2
  compose ps >&2 || true
  echo "=== Memory & Swap ===" >&2
  free -h >&2 || true
  echo "=== Recent Backend Logs ===" >&2
  compose logs --tail=80 backend >&2 || true
  echo "=== Recent Frontend Logs ===" >&2
  compose logs --tail=40 frontend >&2 || true
  echo "=== Recent Nginx Logs ===" >&2
  compose logs --tail=40 nginx >&2 || true
}

# Optional GHCR login (token read from stdin, never passed on command line)
if [[ -n "${GHCR_USERNAME:-}" && "${GHCR_TOKEN_ON_STDIN:-0}" == "1" ]]; then
  echo "Logging into ghcr.io as ${GHCR_USERNAME}..."
  docker login ghcr.io -u "${GHCR_USERNAME}" --password-stdin
fi

# Preserve previous state for rollback
PREVIOUS_TAG="$(read_env_value "SWIFTDROP_IMAGE_TAG")"
PREVIOUS_TAG="${PREVIOUS_TAG:-latest}"
ENV_BACKUP="$(mktemp)"
chmod 600 "${ENV_BACKUP}"
cp "${ENV_FILE}" "${ENV_BACKUP}"
trap 'rm -f "${ENV_BACKUP}"' EXIT

echo "Starting deployment: previous_commit=${PREVIOUS_COMMIT:-unknown} -> target_commit=${TARGET_COMMIT:-unknown}, previous_tag=${PREVIOUS_TAG} -> target_tag=${IMAGE_TAG}"

set_image_tag_in_env "${IMAGE_TAG}"

deploy_ok=1
if ! compose config --quiet; then
  echo "ERROR: compose.prod.yaml configuration check failed." >&2
  deploy_ok=0
elif ! compose pull frontend backend; then
  echo "ERROR: Failed to pull frontend/backend images for tag ${IMAGE_TAG}." >&2
  deploy_ok=0
elif ! compose up -d --no-build --remove-orphans; then
  echo "ERROR: docker compose up failed for tag ${IMAGE_TAG}." >&2
  deploy_ok=0
elif ! reload_nginx; then
  echo "ERROR: Nginx configuration validation or reload failed for tag ${IMAGE_TAG}." >&2
  deploy_ok=0
elif ! wait_for_stack_health "${HEALTH_TIMEOUT_SECONDS}"; then
  deploy_ok=0
elif ! verify_http_endpoints; then
  echo "ERROR: HTTP endpoint verification failed for tag ${IMAGE_TAG}." >&2
  deploy_ok=0
fi

if (( deploy_ok == 1 )); then
  echo "Deployment succeeded for image tag ${IMAGE_TAG}."
  compose ps
  # Remove only untagged dangling layers to keep disk tidy on e2-micro
  docker image prune -f >/dev/null 2>&1 || true
  exit 0
fi

echo "Deployment FAILED for image tag ${IMAGE_TAG}. Capturing diagnostics..." >&2
print_diagnostics

needs_commit_rollback=0
if [[ -n "${PREVIOUS_COMMIT}" && "${PREVIOUS_COMMIT}" != "${TARGET_COMMIT}" ]]; then
  needs_commit_rollback=1
fi

needs_tag_rollback=0
if [[ -n "${PREVIOUS_TAG}" && "${PREVIOUS_TAG}" != "${IMAGE_TAG}" ]]; then
  needs_tag_rollback=1
fi

if (( needs_commit_rollback == 1 || needs_tag_rollback == 1 )); then
  echo "Attempting automatic rollback (commit=${PREVIOUS_COMMIT:-unchanged}, tag=${PREVIOUS_TAG})..." >&2
  echo "NOTE: Flyway database migrations are NOT automatically rolled back." >&2

  if (( needs_commit_rollback == 1 )); then
    echo "Restoring previous Git commit: ${PREVIOUS_COMMIT}" >&2
    GIT_TERMINAL_PROMPT=0 git checkout --force "${PREVIOUS_COMMIT}" </dev/null
  fi

  cp "${ENV_BACKUP}" "${ENV_FILE}"
  chmod 600 "${ENV_FILE}"

  if compose config --quiet \
    && compose pull frontend backend \
    && compose up -d --no-build --remove-orphans \
    && reload_nginx \
    && wait_for_stack_health "${HEALTH_TIMEOUT_SECONDS}" \
    && verify_http_endpoints; then
    echo "ROLLBACK SUCCEEDED: Restored commit ${PREVIOUS_COMMIT:-unchanged} and image tag ${PREVIOUS_TAG}." >&2
  else
    echo "ROLLBACK FAILED: Previous deployment (commit=${PREVIOUS_COMMIT:-unchanged}, tag=${PREVIOUS_TAG}) did not become healthy. Manual intervention required." >&2
    print_diagnostics
  fi
else
  echo "Skipping rollback (previous commit and tag match target)." >&2
fi

exit 1
