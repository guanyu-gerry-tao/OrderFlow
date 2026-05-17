# OrderFlow

OrderFlow is a distributed order management system with a local backend foundation for order workflow execution. The current implementation focuses on a synchronous happy path: create an order, reserve inventory, authorize a simulated payment, complete the order, and expose an auditable order timeline.

## Implemented Capabilities

- Order creation, inventory reservation, payment simulation, and explicit order state transitions.
- Audit logs and order timelines for workflow visibility.
- Minimal backend CI for the Gradle test suite and backend jar build smoke.

## Planned Capabilities

- Idempotency handling for repeated submissions.
- Optimistic inventory locking for concurrent checkout scenarios.
- Transactional outbox, event consumers, retry handling, dead-letter records, and manual retry flows.
- A React TypeScript operations console for orders, inventory, failed events, and service health.
- Repeatable tests and benchmark reports for correctness and reliability scenarios.

## Tech Stack

- Backend: Java 17 target, Spring Boot, Spring Data JPA, Flyway, and PostgreSQL.
- Testing: JUnit and Testcontainers for the current backend workflow.
- Infrastructure: Docker Compose for local PostgreSQL and backend runtime.
- CI: GitHub Actions for backend tests and a backend jar build smoke.
- Planned later: Redis, Kafka or a Kafka-compatible local broker, React, TypeScript, frontend tests, expanded CI, and benchmark automation.

## Current Status

The first backend milestone is implemented. The backend exposes REST APIs to seed inventory, create an order, fetch order details, and fetch an order timeline. The workflow is synchronous in this milestone; asynchronous event processing, idempotency, concurrency controls, retry handling, dead-letter records, and the frontend console are planned for later milestones.

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
  -d '{"customerId":"customer-101","items":[{"sku":"SKU-M1","quantity":2}]}'
```
