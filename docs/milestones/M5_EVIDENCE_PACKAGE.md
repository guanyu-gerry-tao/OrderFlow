# M5 Evidence Package

## Summary

M5 expands OrderFlow from implemented backend/frontend capabilities into a repeatable evidence package. The project now has CI coverage for backend, frontend, mocked e2e, and benchmark report smoke checks. Benchmark runners can generate JSON and Markdown evidence for order correctness and async reliability modes. Public technical documentation now explains the architecture, benchmark commands, local demo path, and baseline/improved mode boundaries.

## Implemented Scope

- Expanded GitHub Actions workflow with backend regression, frontend regression, console e2e smoke, and benchmark report smoke jobs.
- Order correctness benchmark report generation for baseline and improved modes.
- Async reliability benchmark report generation for direct and outbox-kafka modes, using the recording broker for deterministic benchmark evidence.
- JSON and Markdown output for benchmark reports.
- Benchmark scripts under `scripts/benchmark/`.
- Local demo startup and seed-data scripts under `scripts/demo/`.
- Public benchmark documentation under `benchmarks/README.md`.
- Public architecture documentation under `docs/ARCHITECTURE.md`.
- README and development documentation updates for the implemented CI, benchmark, and demo commands.

## Benchmark Commands

Run the full local evidence package:

```bash
./scripts/benchmark/run-evidence-package
```

Run the smaller smoke package:

```bash
./scripts/benchmark/run-evidence-package --smoke
```

Run individual suites:

```bash
./scripts/benchmark/order-correctness --mode improved
./scripts/benchmark/order-correctness --mode baseline
./scripts/benchmark/async-reliability --mode outbox-kafka
./scripts/benchmark/async-reliability --mode direct
```

Full reports are written to `benchmarks/results/full/`. Smoke reports are written to `benchmarks/results/smoke/`. Both are ignored by Git.

## CI Coverage

The GitHub Actions workflow now runs:

- `./gradlew test --no-daemon`
- `./gradlew :backend:bootJar --no-daemon -x test`
- `npm test`
- `npm run build`
- `npm run e2e`
- benchmark smoke commands for correctness and async reliability baseline/improved modes

## Demo Commands

Start the local stack:

```bash
./scripts/demo/run-local-demo.sh
```

Seed local demo data after the backend is reachable:

```bash
./scripts/demo/seed-data.sh
```

## Current Limitations

- Full benchmark runs are intentionally local and can take longer than CI smoke runs.
- Benchmark output is synthetic local evidence, not production traffic.
- Benchmark reports use the current codebase modes. Baseline modes remain isolated to benchmark/evaluation paths.
- M5 does not add cloud deployment or real cloud benchmark evidence.
