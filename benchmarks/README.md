# OrderFlow Benchmarks

OrderFlow keeps benchmark modes in the current codebase so reliability claims can be re-run without comparing against older commits. Baseline modes are only for benchmark and evaluation paths. The default runtime and demo path use the safer improved implementation.

## Suites

| Suite | Baseline mode | Improved mode | Main metrics |
| --- | --- | --- | --- |
| Order correctness | Naive repeated submit and naive inventory reservation | Strict idempotency, Redis response cache, and optimistic locking | Duplicate orders, oversell count, successful orders, failed reservations, duration |
| Async reliability | Direct synchronous workflow | Transactional outbox, event publishing, retry metadata, DLQ, and manual retry | Completed workflows, published events, processed events, retry count, DLQ count, manual retry result, P95/P99 create latency |

## Full Evidence Run

Run the complete local evidence package:

```bash
./scripts/benchmark/run-evidence-package
```

The full run defaults to:

- `10000` repeated-submit attempts for the correctness suite.
- `200` concurrent checkout attempts for the correctness suite.
- `10000` synthetic order workflows for the async reliability suite.

## Smoke Run

Run a smaller version for CI or quick local validation:

```bash
./scripts/benchmark/run-evidence-package --smoke
```

Smoke mode proves the report generation path and benchmark mode switching without running the full load target.

## Individual Commands

Order correctness:

```bash
./scripts/benchmark/order-correctness --mode improved
./scripts/benchmark/order-correctness --mode baseline
```

Async reliability:

```bash
./scripts/benchmark/async-reliability --mode outbox-kafka
./scripts/benchmark/async-reliability --mode direct
```

Override sample sizes when needed:

```bash
./scripts/benchmark/order-correctness --mode improved --repeated-submit-attempts 10000 --concurrent-attempts 200
./scripts/benchmark/async-reliability --mode outbox-kafka --synthetic-orders 10000
```

## Output

Reports are written under `benchmarks/results/`, which is ignored by Git because each run is local evidence. Each suite writes:

- A JSON report for machine-readable evidence.
- A Markdown summary for review and documentation.

The reports are synthetic benchmark evidence from the local codebase. They are not production traffic or production performance numbers.
