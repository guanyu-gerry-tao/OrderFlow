# M2 Order Correctness Controls

## Summary

M2 adds correctness controls for two visible failure scenarios: repeated order submissions and concurrent checkout attempts. The default backend path now uses strict idempotency handling and optimistic inventory reservation. Baseline strategies remain available only for benchmark comparison.

## Implemented Scope

- `Idempotency-Key` support on `POST /api/orders`.
- PostgreSQL-backed idempotency records with request hashes, logical order ids, and response snapshots.
- Redis response caching for completed idempotent requests.
- PostgreSQL fallback when Redis is unavailable.
- Conflict response when the same idempotency key is reused with a different request body.
- Optimistic inventory reservation strategy using a versioned compare-and-set update.
- Baseline idempotency and naive inventory modes for benchmark comparison.
- M2 integration tests for repeated submissions, request conflicts, Redis cache behavior, Redis-unavailable fallback, and 200 concurrent checkout attempts.
- A Gradle benchmark task for baseline and improved correctness modes.

## Configuration

Default runtime settings use the improved path:

```text
ORDERFLOW_IDEMPOTENCY_MODE=strict
ORDERFLOW_IDEMPOTENCY_CACHE=redis
ORDERFLOW_INVENTORY_STRATEGY=optimistic-locking
```

Benchmark baseline mode uses:

```text
ORDERFLOW_IDEMPOTENCY_MODE=baseline
ORDERFLOW_IDEMPOTENCY_CACHE=disabled
ORDERFLOW_INVENTORY_STRATEGY=naive
```

## API Behavior

Repeated submissions with the same `Idempotency-Key` and the same request body return the same logical order response. Reusing the same key with a different request body returns a conflict response.

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-order-001" \
  -d '{"customerId":"customer-101","items":[{"sku":"SKU-M2","quantity":1}]}'
```

## Validation

Run the backend test suite:

```bash
DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./gradlew test --no-daemon
```

Run the correctness benchmark:

```bash
DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./gradlew benchmarkOrderCorrectness -Pmode=improved --no-daemon
DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./gradlew benchmarkOrderCorrectness -Pmode=baseline --no-daemon
```

The benchmark writes local JSON output under `benchmarks/results/order-correctness/`.

## Latest Local Benchmark Snapshot

The most recent local run produced:

| Mode | Repeated-submit attempts | Duplicate orders | Concurrent attempts | Oversell count |
| --- | ---: | ---: | ---: | ---: |
| improved | 20 | 0 | 200 | 0 |
| baseline | 20 | 19 | 200 | 166 |

These numbers are local synthetic benchmark evidence, not production metrics.

## Current Limitations

- The workflow remains synchronous.
- Kafka, transactional outbox, retry handling, dead-letter records, manual retry, and the frontend console are not part of M2.
- Redis is a cache only; PostgreSQL remains the source of truth for idempotency correctness.
- The improved concurrent benchmark rejects contended requests instead of trying to maximize every possible successful reservation under high contention.
