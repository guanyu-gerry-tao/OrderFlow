# OrderFlow

OrderFlow is a distributed order management system with a local backend foundation for reliable order workflow execution. The current implementation can create an order, reserve inventory, authorize a simulated payment, complete the order, replay repeated submissions by idempotency key, prevent concurrent inventory oversell, publish workflow events through a transactional outbox, retry failed event processing, move exhausted failures into a dead-letter table, and expose an auditable order timeline.

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
- Minimal backend CI for the Gradle test suite and backend jar build smoke.

## Planned Capabilities

- A React TypeScript operations console for orders, inventory, failed events, and service health.
- Repeatable tests and benchmark reports for asynchronous reliability scenarios.

## Tech Stack

- Backend: Java 17 target, Spring Boot, Spring Data JPA, Spring Data Redis, Spring Kafka, Flyway, PostgreSQL, Redis, and Redpanda.
- Testing: JUnit and Testcontainers for backend workflow, cache, concurrency, outbox, retry, and DLQ behavior.
- Infrastructure: Docker Compose for local PostgreSQL, Redis, Redpanda, and backend runtime.
- CI: GitHub Actions for backend tests and a backend jar build smoke.
- Planned later: React, TypeScript, frontend tests, expanded CI, and async reliability benchmark automation.

## Current Status

The first three backend milestones are implemented. The backend exposes REST APIs to seed inventory, create an order, fetch order details, fetch an order timeline, list DLQ records, and manually retry a DLQ event. The default runtime event mode is `outbox-kafka`; direct synchronous processing remains available only for tests and benchmark-style comparisons.

## Local Development

Run the backend tests:

```bash
./gradlew test
```

If Docker Desktop exposes a custom socket on macOS, set `DOCKER_HOST` before running Testcontainers:

```bash
DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./gradlew test --no-daemon
```

Start PostgreSQL, Redis, Redpanda, and the backend locally:

```bash
docker compose up --build
```

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

Run the correctness benchmark modes:

```bash
./gradlew benchmarkOrderCorrectness -Pmode=improved
./gradlew benchmarkOrderCorrectness -Pmode=baseline
```

Local benchmark results are written under `benchmarks/results/order-correctness/`, which is ignored by Git.
