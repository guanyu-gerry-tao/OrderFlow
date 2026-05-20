#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MODE="${1:-manifest}"

usage() {
  cat <<'USAGE'
Usage:
  scripts/smoke/run-api-worker-smoke.sh manifest
  scripts/smoke/run-api-worker-smoke.sh compose

Modes:
  manifest  Validate Docker Compose service wiring and Kubernetes manifests.
  compose   Start the local API/worker stack, create one order, and check async completion.
USAGE
}

wait_for_http() {
  local url="$1"
  local label="$2"

  for _ in $(seq 1 60); do
    if curl --fail --silent --show-error "$url" >/dev/null; then
      return 0
    fi
    sleep 2
  done

  echo "Timed out waiting for ${label}: ${url}" >&2
  return 1
}

run_manifest_validation() {
  cd "$ROOT_DIR"
  docker compose config >/tmp/orderflow-compose-config.yaml

  if command -v kubectl >/dev/null 2>&1; then
    if kubectl apply --dry-run=client --validate=false -f k8s/; then
      return 0
    fi
    echo "kubectl dry-run failed; falling back to static YAML manifest validation." >&2
  else
    echo "kubectl not found; using static YAML manifest validation." >&2
  fi

  ruby -ryaml -e '
    files = ARGV
    abort("No Kubernetes YAML files found") if files.empty?
    files.each do |file|
      docs = YAML.load_stream(File.read(file))
      abort("#{file} does not contain any YAML documents") if docs.empty?
      docs.each_with_index do |doc, index|
        next if doc.nil?
        ["apiVersion", "kind", "metadata"].each do |key|
          abort("#{file} document #{index + 1} is missing #{key}") unless doc.key?(key)
        end
        metadata = doc["metadata"]
        abort("#{file} document #{index + 1} metadata is not a map") unless metadata.is_a?(Hash)
        abort("#{file} document #{index + 1} is missing metadata.name") unless metadata.key?("name")
      end
    end
  ' k8s/*.yaml
}

run_compose_smoke() {
  cd "$ROOT_DIR"
  export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-orderflow_m7_smoke}"
  export ORDERFLOW_POSTGRES_PORT="${ORDERFLOW_POSTGRES_PORT:-15432}"
  export ORDERFLOW_REDIS_PORT="${ORDERFLOW_REDIS_PORT:-16379}"
  export ORDERFLOW_REDPANDA_PORT="${ORDERFLOW_REDPANDA_PORT:-19093}"
  export ORDERFLOW_API_PORT="${ORDERFLOW_API_PORT:-18080}"
  export ORDERFLOW_WORKER_PORT="${ORDERFLOW_WORKER_PORT:-18081}"
  local api_base_url="http://localhost:${ORDERFLOW_API_PORT}"
  local worker_base_url="http://localhost:${ORDERFLOW_WORKER_PORT}"

  if [[ "${ORDERFLOW_KEEP_SMOKE_STACK:-0}" != "1" ]]; then
    trap 'docker compose down --remove-orphans >/dev/null 2>&1 || true' EXIT
  fi

  docker compose down --remove-orphans >/dev/null 2>&1 || true
  docker compose up --build -d postgres redis redpanda order-api order-worker

  wait_for_http "${api_base_url}/actuator/health/readiness" "order-api readiness"
  wait_for_http "${worker_base_url}/actuator/health/readiness" "order-worker readiness"

  curl --fail --silent --show-error \
    -X POST "${api_base_url}/api/inventory/seed" \
    -H "Content-Type: application/json" \
    -d '{"sku":"SKU-M7-SMOKE","availableQuantity":5}' >/dev/null

  local response
  response="$(curl --fail --silent --show-error \
    -X POST "${api_base_url}/api/orders" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: m7-smoke-order" \
    -d '{"customerId":"customer-m7-smoke","items":[{"sku":"SKU-M7-SMOKE","quantity":1}]}')"
  local order_id
  order_id="$(printf '%s' "$response" | sed -n 's/.*"orderId":"\([^"]*\)".*/\1/p')"
  if [[ -z "$order_id" ]]; then
    echo "Could not parse orderId from create-order response: $response" >&2
    return 1
  fi

  for _ in $(seq 1 60); do
    local order
    order="$(curl --fail --silent --show-error "${api_base_url}/api/orders/${order_id}")"
    if printf '%s' "$order" | grep -q '"status":"COMPLETED"'; then
      curl --fail --silent --show-error "${api_base_url}/api/operations/health" >/dev/null
      echo "M7 API/worker compose smoke passed for order ${order_id}."
      return 0
    fi
    sleep 2
  done

  echo "Order ${order_id} did not reach COMPLETED through the worker." >&2
  return 1
}

case "$MODE" in
  manifest)
    run_manifest_validation
    ;;
  compose)
    run_compose_smoke
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac
