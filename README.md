# OrderFlow

OrderFlow is a distributed order management system with a local backend foundation for order workflow execution. The current implementation focuses on a synchronous order flow with correctness controls: create an order, reserve inventory, authorize a simulated payment, complete the order, replay repeated submissions by idempotency key, prevent concurrent inventory oversell, and expose an auditable order timeline.

## Implemented Capabilities

- Order creation, inventory reservation, payment simulation, and explicit order state transitions.
- Audit logs and order timelines for workflow visibility.
- Idempotency-key handling for repeated order submissions.
- PostgreSQL-backed idempotency records with Redis response caching and PostgreSQL fallback when Redis is unavailable.
- Optimistic inventory reservation strategy for concurrent checkout attempts.
- Baseline and improved correctness benchmark modes for repeated-submit and concurrent-checkout scenarios.
- Minimal backend CI for the Gradle test suite and backend jar build smoke.

## Planned Capabilities

- Transactional outbox, event consumers, retry handling, dead-letter records, and manual retry flows.
- A React TypeScript operations console for orders, inventory, failed events, and service health.
- Repeatable tests and benchmark reports for asynchronous reliability scenarios.

## Tech Stack

- Backend: Java 17 target, Spring Boot, Spring Data JPA, Spring Data Redis, Flyway, PostgreSQL, and Redis.
- Testing: JUnit and Testcontainers for backend workflow, cache, and concurrency behavior.
- Infrastructure: Docker Compose for local PostgreSQL, Redis, and backend runtime.
- CI: GitHub Actions for backend tests and a backend jar build smoke.
- Planned later: Kafka or a Kafka-compatible local broker, React, TypeScript, frontend tests, expanded CI, and async reliability benchmark automation.

## Current Status

The first two backend milestones are implemented. The backend exposes REST APIs to seed inventory, create an order, fetch order details, and fetch an order timeline. The workflow is still synchronous; asynchronous event processing, retry handling, dead-letter records, manual retry, and the frontend console are planned for later milestones.

## Local Development

Run the backend tests:

```bash
./gradlew test
```

If Docker Desktop exposes a custom socket on macOS, set `DOCKER_HOST` before running Testcontainers:

```bash
DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./gradlew test --no-daemon
```

Start PostgreSQL and the backend locally:

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

Run the correctness benchmark modes:

```bash
./gradlew benchmarkOrderCorrectness -Pmode=improved
./gradlew benchmarkOrderCorrectness -Pmode=baseline
```

Local benchmark results are written under `benchmarks/results/order-correctness/`, which is ignored by Git.
