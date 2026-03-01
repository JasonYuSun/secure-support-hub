#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/infra/docker-compose/docker-compose.yml"
KEEP_STACK_UP="${KEEP_STACK_UP:-0}"
RUN_E2E="${RUN_E2E:-1}"

step() {
  echo
  echo "==> $1"
}

wait_for_url() {
  local name="$1"
  local url="$2"
  local timeout_seconds="${3:-120}"
  local elapsed=0

  until curl -fsS "$url" >/dev/null; do
    sleep 2
    elapsed=$((elapsed + 2))
    if (( elapsed >= timeout_seconds )); then
      echo "Timed out after ${timeout_seconds}s waiting for ${name}: ${url}" >&2
      return 1
    fi
  done
}

cleanup() {
  if [[ "$KEEP_STACK_UP" == "1" ]]; then
    echo
    echo "Leaving Docker stack running because KEEP_STACK_UP=1."
    return
  fi

  echo
  echo "==> Stopping Docker stack"
  docker compose -f "$COMPOSE_FILE" down
}

trap cleanup EXIT

step "Starting full stack"
docker compose -f "$COMPOSE_FILE" up --build -d

step "Waiting for backend health endpoint"
wait_for_url "backend health" "http://localhost:8080/actuator/health" 180
curl -fsS http://localhost:8080/actuator/health

step "Running backend tests"
(
  cd "$ROOT_DIR/apps/api"
  ./gradlew test
)

step "Running frontend build"
(
  cd "$ROOT_DIR/apps/web"
  npm ci
  npm run lint
  npm run build
)

if [[ "$RUN_E2E" == "1" ]]; then
  step "Running frontend E2E tests"
  (
    cd "$ROOT_DIR/apps/web"
    npx playwright install chromium
    npm run test:e2e
  )
fi

step "Checking Swagger UI"
swagger_status="$(curl -sS -o /dev/null -w "%{http_code}" http://localhost:8080/swagger-ui.html)"
case "$swagger_status" in
  200|301|302|307|308) ;;
  *)
    echo "Unexpected HTTP status from /swagger-ui.html: $swagger_status" >&2
    exit 1
    ;;
esac
curl -fsS http://localhost:8080/swagger-ui/index.html >/dev/null
echo "Swagger endpoint is reachable (status: $swagger_status)."

step "Checking frontend app"
curl -fsS http://localhost:5173 >/dev/null
echo "Frontend endpoint is reachable."

echo
echo "Verification complete."
