# OrderFlow

OrderFlow is a distributed order management system with a local backend foundation and an operations console for reliable order workflow execution. The current implementation can create an order, reserve inventory, authorize a simulated payment, complete the order, replay repeated submissions by idempotency key, prevent concurrent inventory oversell, publish workflow events through a transactional outbox, retry failed event processing, move exhausted failures into a dead-letter table, expose an auditable order timeline, and inspect workflow health from a React console.

## Implemented Capabilities

- Order creation, inventory reservation, payment simulation, and explicit order state transitions.
- Audit logs and order timelines for workflow visibility.
- Idempotency-key handling for repeated order submissions.
- PostgreSQL-backed idempotency records with Redis response caching and PostgreSQL fallback when Redis is unavailable.
- Optimistic inventory reservation strategy for concurrent checkout attempts.
- Baseline and improved correctness benchmark modes for repeated-submit and concurrent-checkout scenarios.
- Transactional outbox records for order workflow events.
- Kafka-compatible local event publishing through Redpanda in Docker Compose.
- Event consumer processing for order-created and inventory-reserved workflow events.
- Retry metadata with retry count, next attempt time, and last error for publish and consumer failures.
- Dead-letter records and a manual retry API for exhausted event-processing failures.
- React TypeScript operations console for order creation, order timeline inspection, inventory visibility, failed-event recovery, service health, and live SSE health snapshots.
- Typed frontend API client layer that centralizes backend calls and request error handling.
- Frontend unit tests and Playwright e2e tests for the core console workflows.
- Minimal backend CI for the Gradle test suite and backend jar build smoke.

## Planned Capabilities

- Expanded CI that runs backend, frontend, and e2e regression checks.
- Repeatable tests and benchmark reports for asynchronous reliability scenarios.

## Tech Stack

- Backend: Java 17 target, Spring Boot, Spring Data JPA, Spring Data Redis, Spring Kafka, Flyway, PostgreSQL, Redis, and Redpanda.
- Frontend: React, TypeScript, Vite, and Playwright.
- Testing: JUnit and Testcontainers for backend workflow, cache, concurrency, outbox, retry, and DLQ behavior.
- Infrastructure: Docker Compose for local PostgreSQL, Redis, Redpanda, backend, and frontend runtime.
- CI: GitHub Actions for backend tests and a backend jar build smoke.
- Planned later: expanded CI and async reliability benchmark automation.

## Current Status

The first four milestones are implemented. The backend exposes REST APIs to seed inventory, create an order, list and fetch order details, fetch an order timeline, list inventory, list DLQ records, manually retry a DLQ event, fetch operations health, and stream live SSE health snapshots. The frontend console consumes those APIs through a typed client layer. The default runtime event mode is `outbox-kafka`; direct synchronous processing remains available only for tests and benchmark-style comparisons.

## Local Development

Run the backend tests:

```bash
./gradlew test
```

If Docker Desktop exposes a custom socket on macOS, set `DOCKER_HOST` before running Testcontainers:

```bash
DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./gradlew test --no-daemon
```

Install frontend dependencies:

```bash
cd frontend
npm install
```

Run frontend checks:

```bash
cd frontend
npm test
npm run build
npm run e2e
```

Start PostgreSQL, Redis, Redpanda, backend, and frontend locally:

```bash
docker compose up --build
```

The backend listens on `http://localhost:8080`. The console is served on `http://localhost:5173`.

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

List dead-letter events and manually retry one if a failure has exhausted retries:

```bash
curl http://localhost:8080/api/dlq

curl -X POST http://localhost:8080/api/dlq/<dead-letter-event-id>/retry
```

Open the operations health API or SSE stream:

```bash
curl http://localhost:8080/api/operations/health
curl http://localhost:8080/api/realtime/events
```

Run the correctness benchmark modes:

```bash
./gradlew benchmarkOrderCorrectness -Pmode=improved
./gradlew benchmarkOrderCorrectness -Pmode=baseline
```

Local benchmark results are written under `benchmarks/results/order-correctness/`, which is ignored by Git.
