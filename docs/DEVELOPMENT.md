# Development Notes

OrderFlow currently has a Spring Boot backend foundation with correctness controls, reliable asynchronous event processing, and a React TypeScript operations console. This document records the public development commands and conventions that are real in the current codebase.

## Repository Status

- Backend runtime code has landed under `backend/`.
- Frontend operations console code has landed under `frontend/`.
- Public documentation should distinguish implemented backend behavior from planned later milestones.
- The current runnable runtime depends on PostgreSQL, Redis, and Redpanda.
- The backend can run as `order-api`, `order-worker`, or backward-compatible `all` mode through `ORDERFLOW_RUNTIME_ROLE`.
- PostgreSQL remains the source of truth for idempotency records; Redis is a short-lived response cache.
- The console default flow uses `/api/checkout-sessions`: start checkout creates a `PENDING_PAYMENT` order and backend-derived payment idempotency key, while confirm payment transitions the order to `CREATED` and starts the outbox workflow.
- `POST /api/orders` remains available for benchmark/API compatibility and still uses the order-creation `Idempotency-Key` boundary.
- The default event mode is `outbox-kafka`; direct synchronous processing is kept for tests and benchmark-style comparison only.
- Outbox and DLQ metadata are stored in PostgreSQL so retry state is visible even when event processing fails.
- The console calls backend APIs through `frontend/src/api/` and uses SSE for live health snapshots.
- Private planning material belongs in ignored private documentation and should not be referenced from public-facing files.

## Expected Local Development Flow

- Keep changes scoped to the active milestone.
- Use `./gradlew test` for the backend test suite.
- Use `npm test`, `npm run build`, and `npm run e2e` from `frontend/` for the console checks.
- Use `docker compose up --build` for the local PostgreSQL, Redis, Redpanda, `order-api`, `order-worker`, and frontend runtime.
- Use `./scripts/smoke/run-api-worker-smoke.sh manifest` to validate Docker Compose service wiring and Kubernetes manifests.
- Use `./scripts/benchmark/run-evidence-package --smoke` for a quick benchmark report-generation check.
- Use `./scripts/benchmark/run-evidence-package` for the full local benchmark evidence package.
- Use `./scripts/demo/seed-data.sh` after the Docker Compose stack is up to seed sample inventory and one sample order.
- Prefer reproducible local dependencies through Docker Compose.
- Keep public docs in sync with implemented behavior, not aspirational behavior.

## Testing Expectations

- Add tests with each feature rather than saving coverage for the end.
- Use integration tests when behavior depends on database transactions, message brokers, cache behavior, or concurrency.
- The current integration tests use Testcontainers with PostgreSQL and Redis where needed.
- M2 tests cover repeated-submit idempotency, same-key request conflicts, Redis response caching, Redis-unavailable fallback, and 200 concurrent checkout attempts.
- M3 tests cover transactional outbox writes, broker publishing, consumer processing, publish-failure retry metadata, consumer-crash retry metadata, payment-timeout DLQ routing, and manual retry recovery.
- M4 backend tests cover operations-console read APIs for order filtering, inventory visibility, and health counters.
- M4 frontend tests cover page-level error fallback, loading/empty/error/retry UI states, order timeline navigation, and manual retry.
- M4 Playwright tests cover browser workflows for order creation to timeline and failed-event manual retry.
- M5 benchmark smoke covers JSON and Markdown report generation for correctness and async reliability suites.
- M8 backend tests cover checkout session creation, 15-minute TTL, duplicate confirm replay, gateway response timeout retry, expired checkout conflict, legacy order idempotency compatibility, and one outbox event after checkout confirm.
- M8 frontend tests cover checkout session start, active session restore after reload, backend-derived payment idempotency key display, and repeated confirm calls that keep the same payment attempt while request attempts change.
- On Docker Desktop for macOS, `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./gradlew test --no-daemon` may be needed when the default socket is not detected.
- Keep benchmark and comparison modes runnable in the current codebase when they are used to prove an engineering mechanism.
- Baseline modes should stay isolated to test, benchmark, or evaluation profiles. Default runtime paths should use the improved implementation.

## Benchmark Evidence

Benchmark reports are generated under `benchmarks/results/`, which is ignored by Git. Each run writes JSON for machine-readable evidence and Markdown for review. Full reports use `benchmarks/results/full/`; CI and quick local smoke reports use `benchmarks/results/smoke/` so they do not overwrite full evidence.

Correctness benchmark:

```bash
./scripts/benchmark/order-correctness --mode improved
./scripts/benchmark/order-correctness --mode baseline
```

Async reliability benchmark:

```bash
./scripts/benchmark/async-reliability --mode outbox-kafka
./scripts/benchmark/async-reliability --mode direct
```

The full evidence package defaults to the larger local targets documented in `benchmarks/README.md`. CI uses smoke-sized runs so pull requests still get fast report-generation coverage.

The async reliability benchmark uses the recording broker for deterministic report generation. The default Docker Compose runtime still uses Redpanda/Kafka for the `outbox-kafka` service path.

## Event Processing Modes

Default local runtime:

```text
ORDERFLOW_RUNTIME_ROLE=api|worker
ORDERFLOW_EVENT_MODE=outbox-kafka
ORDERFLOW_EVENT_BROKER=kafka
SPRING_KAFKA_BOOTSTRAP_SERVERS=redpanda:9092
```

Direct mode is used by legacy workflow tests and comparison paths:

```text
ORDERFLOW_EVENT_MODE=direct
ORDERFLOW_EVENT_BROKER=recording
```

The direct mode is not the normal runtime path. It exists so the synchronous M1/M2 behaviors remain testable while the default service uses the reliable outbox path.

`ORDERFLOW_RUNTIME_ROLE=all` is the compatibility mode used by tests and single-process experiments. Docker Compose and Kubernetes use separate `api` and `worker` roles.

## Operations Console

The console is a Vite React TypeScript app under `frontend/`. Its local development server defaults to `http://localhost:5173` and calls the backend at `http://localhost:8080/api`.

The order workflow in the console is intentionally two-step:

1. `Start checkout` calls `POST /api/checkout-sessions`, stores the returned checkout session id in browser storage, and displays the stable pending order, payment attempt, and backend-derived authorize idempotency key.
2. `Confirm payment` calls `POST /api/checkout-sessions/{checkoutSessionId}/confirm`. The backend creates a new request-attempt log for each HTTP call, reuses the same business payment attempt for retries, and only enqueues the order workflow after authorization succeeds.

Refreshing the console during an active checkout uses the stored checkout session id to restore the same pending order. The browser does not generate payment idempotency keys.

Run the console locally:

```bash
cd frontend
npm install
npm run dev
```

Run console checks:

```bash
cd frontend
npm test
npm run build
npm run e2e
```

The Playwright configuration uses port `5178` for tests so it does not accidentally reuse another local Vite app on `5173`.

## PR And Milestone Workflow

- Work should move in logical milestone-sized changes.
- Each milestone should have a clear done state, validation notes, and any known limitations.
- Public PR descriptions should focus on engineering behavior, validation, and remaining risks.
- Private milestone notes may capture additional planning context, but public files should remain project-neutral.

## Public And Private Documentation Boundary

- Public files must be written in English and should only describe the engineering project.
- Private planning, local strategy, and non-public decision context must stay in ignored private files.
- Public documentation must not claim planned features, benchmark numbers, or deployment paths as complete before they are implemented and verified.
