# Order Correctness Benchmark

This benchmark compares the M2 baseline and improved correctness modes in the current codebase.

Run the improved mode:

```bash
./gradlew benchmarkOrderCorrectness -Pmode=improved
```

Run the baseline mode:

```bash
./gradlew benchmarkOrderCorrectness -Pmode=baseline
```

Results are written to `benchmarks/results/order-correctness/`, which is intentionally ignored by Git because each run is local evidence.

The output includes duplicate order count, oversell count, successful orders, failed reservations, and duration for repeated-submit and concurrent-checkout scenarios.
