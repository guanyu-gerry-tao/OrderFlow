# OrderFlow M5 Evidence Package

## 证据范围

这个文件记录 M5 之后项目已有的工程证据。它是 internal 文件，可以服务简历、面试和后续复盘；public 文档只保留中性的复跑命令和架构说明。

## 当前本地验证结果

本轮实际运行：

```bash
./scripts/benchmark/run-evidence-package --smoke
./scripts/benchmark/run-evidence-package
./scripts/benchmark/order-correctness --mode improved
./scripts/benchmark/order-correctness --mode baseline
```

说明：先跑 smoke 验证 benchmark runner、baseline/improved mode 切换、JSON/Markdown report generation；随后跑 full package。最后又单独重跑 correctness improved/baseline，是因为修正了 repeated-submit benchmark 的 seed 数量，确保 10K baseline 对照不被库存不足低估。

## Benchmark Full 数字

| 指标 | 本轮结果 | 证据 |
| --- | --- | --- |
| repeated submit duplicate orders | improved: 0 / 10000；baseline: 9999 / 10000 | `benchmarks/results/full/order-correctness/improved.json`，`baseline.json` |
| concurrent checkout oversell count | improved: 0；baseline: 170 | `benchmarks/results/full/order-correctness/improved.json`，`baseline.json` |
| async completed orders | outbox-kafka: 10000 / 10000；direct: 10000 / 10000 | `benchmarks/results/full/async-reliability/outbox-kafka.json`，`direct.json` |
| outbox published / processed events | outbox-kafka: 20000 published，20000 processed | `benchmarks/results/full/async-reliability/outbox-kafka.json` |
| async retry count | outbox-kafka: 4 | `benchmarks/results/full/async-reliability/outbox-kafka.json` |
| DLQ count | outbox-kafka: 3 after injected recovery scenarios | `benchmarks/results/full/async-reliability/outbox-kafka.json` |
| P95/P99 create latency | outbox-kafka full: P95 2ms，P99 4ms；direct full: P95 4ms，P99 6ms | `benchmarks/results/full/async-reliability/*.json` |

## Full Evidence 命令

完整 evidence package 命令：

```bash
./scripts/benchmark/run-evidence-package
```

默认 full target：

- 10000 repeated-submit attempts。
- 200 concurrent checkout attempts。
- 10000 async synthetic orders。

说明：本轮已运行 full package。后续如果代码或机器环境变化，应重新运行并刷新本文件。

## Failure Scenario Coverage Matrix

| ID | 场景 | 当前证据状态 |
| --- | --- | --- |
| F01 | duplicate submit same key/body | 已实现；M2 integration test + correctness benchmark |
| F02 | same idempotency key different body | 已实现；M2 conflict test |
| F03 | Redis unavailable fallback | 已实现；M2 Redis fallback test |
| F04 | concurrent checkout limited inventory | 已实现；M2 concurrency test + benchmark |
| F05 | optimistic lock conflict | 已实现；M2 concurrency path 间接覆盖 |
| F06 | payment timeout | 已实现；M3 test + async benchmark failureRecovery |
| F07 | payment permanent failure / compensation | 已有 M3/M4 相关路径，但不是 M5 新增 |
| F08 | inventory reservation failure | 已由 workflow failure path 和 UI timeline 支撑 |
| F09 | outbox publisher crash before publish | 已实现；publish failure retry/DLQ test + async benchmark |
| F10 | Kafka temporarily unavailable | 本地 Kafka runtime 存在；M5 未新增真实 broker outage benchmark |
| F11 | consumer crash during processing | 已实现；M3 test + async benchmark |
| F12 | malformed event payload | 已有 DLQ/error handling 基础；M5 未新增 malformed payload benchmark |
| F13 | manual retry succeeds from DLQ | 已实现；M3 test、M4 UI、async benchmark |
| F14 | manual retry still fails | 已实现；M3 test + async benchmark |
| F15 | frontend API failure | 已实现；M4 frontend component/e2e coverage |

## Hard Stack / Keyword Coverage

| Stack / Keyword | Evidence |
| --- | --- |
| Java / Spring Boot | `backend/`，`./gradlew test`，backend CI |
| PostgreSQL | Flyway migrations、repositories、Testcontainers |
| Redis | idempotency response cache、Redis fallback test、health visibility |
| Kafka / Redpanda | Docker Compose runtime、outbox publisher、event broker abstraction |
| React / TypeScript | `frontend/` console、typed API client、tests、build |
| Docker | `compose.yaml`，backend/frontend Dockerfiles，demo script |
| GitHub Actions | expanded `.github/workflows/ci.yml` |
| CI/CD | backend/frontend/e2e/benchmark smoke jobs |
| technical documentation | README、DEVELOPMENT、ARCHITECTURE、benchmarks README、milestone docs |
| operational excellence | operations console、health API、DLQ/manual retry、evidence package |

## 已知限制

- Full 10K evidence command 已实现并已在本轮运行。
- CI benchmark smoke 是小样本，目的不是压测，而是保证 runner 和报告格式不坏。
- Smoke evidence 写入 `benchmarks/results/smoke/`，full evidence 写入 `benchmarks/results/full/`，避免快速检查覆盖完整证据。
- Async benchmark 使用 recording broker 断言 outbox/retry/DLQ 语义；真实 Redpanda/Kafka 路径通过 Docker Compose runtime 保留。
- M5 没有做云部署；AWS runtime 属于 M6。
- public 文档不能引用本文件里的内部定位或简历表达。
