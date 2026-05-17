# M1 Backend Foundation

## Summary

M1 establishes the OrderFlow backend foundation and a synchronous happy-path order workflow. The backend can seed inventory, create an order, reserve inventory, simulate payment authorization, complete the order, and return an audit timeline for the workflow.

## Implemented Scope

- Java and Spring Boot backend module under `backend/`.
- PostgreSQL schema managed by Flyway.
- Docker Compose runtime for PostgreSQL and the backend service.
- Minimal GitHub Actions backend CI.
- REST APIs for inventory seeding, order creation, order lookup, and order timeline lookup.
- Explicit order state machine with the M1 happy path:
  - `CREATED`
  - `INVENTORY_RESERVED`
  - `PAYMENT_AUTHORIZED`
  - `COMPLETED`
- Repository/service/controller layering for order, inventory, payment, and audit behavior.
- Simulated payment authorization for the happy path.
- Audit log records for order state transitions.

## API Surface

```text
POST /api/inventory/seed
POST /api/orders
GET  /api/orders/{orderId}
GET  /api/orders/{orderId}/timeline
```

## Validation

The milestone was validated with:

```bash
DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./gradlew test --no-daemon
./gradlew :backend:bootJar --no-daemon
docker compose up --build -d
```

The GitHub Actions workflow at `.github/workflows/ci.yml` runs on pull requests and pushes to `main`. It checks out the repository, sets up Java 17, runs the Gradle backend test suite, and builds the backend jar as a lightweight smoke check.

Manual API validation covered:

- Seeding one SKU with available inventory.
- Creating an order for that SKU.
- Confirming the created order reached `COMPLETED`.
- Fetching the order by id.
- Fetching the timeline with the four expected state transitions.

## Current Limitations

This milestone intentionally does not implement repeated-submit idempotency, Redis caching, optimistic inventory locking, asynchronous events, transactional outbox, retry handling, dead-letter records, manual retry flows, benchmarks, or the frontend operations console. Those are reserved for later milestones.

## Notes

Testcontainers is pinned through the Testcontainers BOM so the PostgreSQL integration test can run against current Docker Desktop environments. The Dockerfile uses non-Alpine base images because the Alpine variants did not resolve for the local platform during validation.
