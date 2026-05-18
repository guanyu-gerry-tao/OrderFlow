#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${ORDERFLOW_API_BASE_URL:-http://localhost:8080/api}"

curl -fsS -X POST "${API_BASE_URL}/inventory/seed" \
  -H "Content-Type: application/json" \
  -d '{"sku":"SKU-DEMO-PRIMARY","availableQuantity":50}'

curl -fsS -X POST "${API_BASE_URL}/inventory/seed" \
  -H "Content-Type: application/json" \
  -d '{"sku":"SKU-DEMO-LOW-STOCK","availableQuantity":3}'

curl -fsS -X POST "${API_BASE_URL}/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-order-primary-001" \
  -d '{"customerId":"demo-customer-001","items":[{"sku":"SKU-DEMO-PRIMARY","quantity":2}]}'

curl -fsS "${API_BASE_URL}/operations/health"
