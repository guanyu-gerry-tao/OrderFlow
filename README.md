# OrderFlow

OrderFlow is a production-style e-commerce order management system built around reliable checkout workflows. It combines a Java/Spring Boot backend, PostgreSQL, Redis, Kafka-compatible event processing, an API/worker service split, Kubernetes manifests, and a React operations console.

The project focuses on a practical backend problem: how to create orders, reserve inventory, authorize payment, publish workflow events, recover failed async work, and give operators enough visibility to understand what happened.

## What It Demonstrates

- REST API design for checkout, inventory reservation, payment authorization, order timelines, service health, and manual recovery.
- Database schema design for orders, inventory reservations, idempotency records, audit logs, outbox events, and dead-letter records.
- Reliability patterns including idempotency keys, optimistic locking, transactional outbox, idempotent consumers, retry metadata, DLQ routing, and manual replay.
- Multi-service runtime separation between `order-api` for synchronous REST traffic and `order-worker` for outbox publishing, Kafka consumption, retry, and DLQ recovery.
- A React/TypeScript operations console for order creation, order inspection, failed-event triage, manual retry, inventory state, and service health.
- Repeatable local benchmarks and smoke checks that validate duplicate-submit handling, concurrent checkout behavior, async event recovery, and Kubernetes manifest health-check coverage.

## Architecture

```text
React Operations Console
        |
        v
order-api (Spring Boot REST + SSE)
        |
        +--> PostgreSQL: orders, inventory, idempotency, audit logs, outbox, DLQ
        +--> Redis: cached idempotent responses
        |
        v
Transactional Outbox
        |
        v
Redpanda / Kafka-compatible broker
        |
        v
order-worker (publisher + consumer + retry + DLQ recovery)
```

The default local runtime uses the outbox/Kafka path. Direct synchronous processing is kept only for tests and benchmark comparisons, so the normal demo path exercises the reliable async workflow.

## Implemented Capabilities

### Order Workflow

- Create orders through REST APIs.
- Reserve inventory with optimistic locking.
- Record state transitions for create, reserve, authorize, cancel, retry, and recovery flows.
- Store audit logs and order timelines for later inspection.
- Handle repeated submissions through idempotency keys and cached responses.

### Async Reliability

- Write order workflow events into a transactional outbox.
- Publish outbox events through a Kafka-compatible broker using Redpanda locally.
- Process order-created and inventory-reserved events in a worker service.
- Track retry count, next attempt time, and last error for failed publish or consumer work.
- Route exhausted failures into a dead-letter table.
- Requeue failed events through a manual retry API.

### Operations Console

- Create orders and seed inventory from the UI.
- Inspect order timelines, inventory state, failed events, and retry status.
- View service health and live SSE health snapshots.
- Use a typed frontend API client layer with loading, empty, error, and retry states.

### Deployment Evidence

- Docker Compose stack for PostgreSQL, Redis, Redpanda, `order-api`, `order-worker`, and frontend.
- Kubernetes manifests for `order-api`, `order-worker`, PostgreSQL, Redis, and Redpanda.
- Readiness and liveness probes for the API/worker split.
- Smoke script that validates service wiring and manifest structure.

## Validation

The project includes backend, frontend, e2e, smoke, and benchmark checks:

- JUnit and Testcontainers tests for backend workflows, cache fallback, concurrency, outbox, retry, and DLQ behavior.
- Frontend unit tests for operations-console states.
- Playwright e2e tests for the main console workflows.
- GitHub Actions checks for backend tests, frontend tests, frontend build, mocked e2e smoke, and benchmark report smoke.
- Benchmark scripts for repeated-submit correctness, concurrent checkout correctness, and async reliability.

Example benchmark targets include repeated-submit and concurrent-checkout scenarios with 10K duplicate submissions, 200 concurrent checkout attempts, and 20K outbox events. Benchmark output is written as local JSON and Markdown evidence under ignored `benchmarks/results/` paths.

## Tech Stack

- Backend: Java 17 target, Spring Boot, Spring Data JPA, Spring Data Redis, Spring Kafka, Flyway.
- Data: PostgreSQL and Redis.
- Eventing: transactional outbox with Redpanda/Kafka-compatible local runtime.
- Frontend: React, TypeScript, Vite, Playwright.
- Infrastructure: Docker Compose and Kubernetes manifests.
- Testing and delivery: JUnit, Testcontainers, Playwright, GitHub Actions.

## Local Development

Run backend tests:

```bash
./gradlew test
```

If Docker Desktop exposes a custom socket on macOS, set `DOCKER_HOST` before running Testcontainers:

```bash
DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./gradlew test --no-daemon
```

Install frontend dependencies and run frontend checks:

```bash
cd frontend
npm install
npm test
npm run build
npm run e2e
```

Start the local stack:

```bash
docker compose up --build
```

Local endpoints:

- API: `http://localhost:8080`
- Worker actuator health: `http://localhost:8081`
- Console: `http://localhost:5173`

Seed inventory and create an order:

```bash
curl -X POST http://localhost:8080/api/inventory/seed \
  -H "Content-Type: application/json" \
  -d '{"sku":"SKU-M1","availableQuantity":10}'

curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-order-001" \
  -d '{"customerId":"customer-101","items":[{"sku":"SKU-M1","quantity":2}]}'
```

Inspect failed events and retry one:

```bash
curl http://localhost:8080/api/dlq
curl -X POST http://localhost:8080/api/dlq/<dead-letter-event-id>/retry
```

Run local benchmark evidence:

```bash
./scripts/benchmark/order-correctness --mode improved
./scripts/benchmark/order-correctness --mode baseline
./scripts/benchmark/async-reliability --mode outbox-kafka
./scripts/benchmark/async-reliability --mode direct
```

Run a quick smoke package:

```bash
./scripts/benchmark/run-evidence-package --smoke
./scripts/smoke/run-api-worker-smoke.sh manifest
```

## Documentation

- `docs/ARCHITECTURE.md` explains the state flow, outbox recovery flow, API/worker split, and benchmark boundaries.
- `benchmarks/README.md` explains benchmark modes and report output.
- `k8s/README.md` explains local Kubernetes manifests and validation.
