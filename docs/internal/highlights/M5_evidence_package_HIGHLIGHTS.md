# M5 Evidence Package Highlights

## 已实现亮点

- [已实现] 把 CI 从 backend-only 扩展成四条 reviewer 能理解的流水线：backend regression、frontend regression、console e2e smoke、benchmark report smoke。证据：`.github/workflows/ci.yml`。
- [已实现] benchmark runner 现在同时输出 JSON 和 Markdown，兼顾机器可读和人类 review。证据：`backend/src/test/java/com/orderflow/benchmark/BenchmarkReportWriter.java`。
- [已实现] order correctness benchmark 支持 full target 参数：10000 repeated-submit attempts、200 concurrent checkout attempts。证据：`backend/build.gradle`、`scripts/benchmark/order-correctness`。
- [已实现] async reliability benchmark 支持 direct baseline 和 outbox-kafka improved。证据：`backend/src/test/java/com/orderflow/benchmark/AsyncReliabilityBenchmarkTest.java`、`scripts/benchmark/async-reliability`。
- [已实现] outbox-kafka full benchmark 产出 10000/10000 completed orders、20000 published events、20000 processed events、retryCount 4、DLQ count 3、P95 3ms、P99 5ms。证据：`benchmarks/results/async-reliability/outbox-kafka.json`。
- [已实现] correctness full benchmark 产出 improved repeated submit 10000 次 duplicateOrders = 0，concurrent checkout 200 次 oversellCount = 0。证据：`benchmarks/results/order-correctness/improved.json`。
- [已实现] baseline 对照仍可运行：baseline repeated submit 10000 次产生 logicalOrders = 10000、duplicateOrders = 9999；concurrent checkout 200 次 oversellCount = 173，说明 idempotency 和 optimistic-locking improved path 的价值。证据：`benchmarks/results/order-correctness/baseline.json`。
- [已实现] public architecture doc 使用 Mermaid 解释 runtime、state machine、outbox recovery flow。证据：`docs/ARCHITECTURE.md`。
- [已实现] demo seed script 能创建 demo inventory 和 sample order，并读取 health API。证据：`scripts/demo/seed-data.sh`。
- [已实现] public/private 边界继续保持：public docs 只讲工程系统、命令和架构；internal docs 才记录简历/面试理解。证据：M5 隐私扫描。

## 面试可讲素材

- 可以讲“我不是只实现功能，还补了 evidence package，让每个关键 claim 都有命令和报告路径”。
- 可以讲 baseline/improved 的区别：baseline 是为了证明机制价值，默认 runtime 永远走 improved。
- 可以讲 CI 分层：backend/frontend/e2e/benchmark smoke 分开，失败时更容易定位。
- 可以讲 benchmark output 设计：JSON 适合后续自动汇总，Markdown 适合 reviewer 直接读。
- 可以讲工程取舍：CI 不跑 full 10K，因为 PR 检查关注回归速度；full benchmark 留给本地 evidence generation。

## 部分实现或后续可加强

- [已实现] Full 10K benchmark 命令已实现并已运行；最终展示前如果代码变化，应重新运行 full package 刷新数字。
- [未实现] screenshots/video capture；这是 M5 Stretch。
- [未实现] AWS deployment / CloudWatch evidence；这是 M6。
- [未实现] ServiceObs ingestion 或 OperationAssistant workflow；本项目只输出自身证据，不建立强 contract。
