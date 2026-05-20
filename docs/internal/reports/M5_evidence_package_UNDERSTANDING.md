# M5 Evidence Package 理解报告

## 到此为止这个项目是什么

到 M5 为止，OrderFlow 已经不是单纯“能跑的订单 demo”，而是一个可以被复查的分布式订单可靠性项目。它有 Spring Boot 后端、PostgreSQL 数据模型、Redis 幂等缓存、Kafka/Redpanda 风格事件路径、React TypeScript 运维台、CI、benchmark runner、public 技术文档和 internal 证据包。

用一句话理解：这个项目演示的是“订单工作流在重复提交、并发库存、异步事件失败、DLQ 和人工 retry 场景下，如何保持可解释、可恢复、可验证”。

## 这次 built 了什么

M5 没有新增订单业务功能，而是补齐工程证据层：

- 扩展 `.github/workflows/ci.yml`：从 backend-only 变成 backend regression、frontend regression、console e2e smoke、benchmark report smoke。
- 新增 benchmark report writer：benchmark 现在同时输出 JSON 和 Markdown。
- 新增 async reliability benchmark：可以跑 `direct` baseline 和 `outbox-kafka` improved。
- 扩展 order correctness benchmark：可以通过参数调整 repeated-submit 和 concurrent checkout 样本量。
- 新增 `scripts/benchmark/`：提供 correctness、async reliability、整包 evidence 的可复跑入口。
- 新增 `scripts/demo/`：提供 Docker Compose demo 启动和 seed data 入口。
- 新增 public docs：`benchmarks/README.md`、`docs/ARCHITECTURE.md`、`docs/milestones/M5_EVIDENCE_PACKAGE.md`。
- 更新 public docs：`README.md`、`docs/DEVELOPMENT.md`。
- 新增 internal evidence、cut-line、highlight inventory。

## 为什么重要

前四个 milestone 证明“功能存在”。M5 证明“别人可以复跑、review、看懂、截图、解释数字”。这很重要，因为工程项目不是只看代码量，而是看：

- CI 能不能自动守住回归。
- benchmark 数字是不是来自当前代码，而不是旧 commit 或口头描述。
- baseline/improved 是否都还在当前代码里可运行。
- 文档是否能让 reviewer 快速启动、测试、理解架构。
- internal evidence 是否保存了未来写简历、面试和项目讲解时需要的细节。

## 已经变成真实能力的关键词和 pattern

| 关键词 / Pattern | 当前真实证据 |
| --- | --- |
| GitHub Actions / CI/CD | `.github/workflows/ci.yml` 有 backend、frontend、e2e、benchmark smoke jobs |
| technical documentation | `README.md`、`docs/DEVELOPMENT.md`、`docs/ARCHITECTURE.md`、`benchmarks/README.md` |
| operational excellence | 运维台、health API、DLQ/manual retry、CI、benchmark evidence 形成闭环 |
| P95/P99 latency | async benchmark JSON/Markdown 输出 `p95CreateLatencyMs`、`p99CreateLatencyMs` |
| failure injection | async benchmark 覆盖 F06、F09、F11、F13、F14 的可运行注入场景 |
| idempotency key | correctness benchmark improved mode 输出 duplicateOrders = 0 |
| optimistic locking | correctness benchmark improved mode 输出 oversellCount = 0 |
| transactional outbox | async benchmark improved mode 输出 published/processed events |
| retry / DLQ / manual retry | async benchmark improved mode 输出 retryCount、dlqCount、manual retry status |
| baseline vs improved | `scripts/benchmark/* --mode ...` 保留当前代码中的对照模式 |

## 本轮已运行证据

本轮先运行了 smoke evidence package，随后又运行了 full evidence package：

```bash
./scripts/benchmark/run-evidence-package --smoke
./scripts/benchmark/run-evidence-package
./scripts/benchmark/order-correctness --mode improved
./scripts/benchmark/order-correctness --mode baseline
```

最后两条 correctness 命令是在修正 repeated-submit seed 数量后重跑，用来刷新 10K/200 的最终 correctness 报告。

结果文件位置：

- `benchmarks/results/full/order-correctness/improved.json`
- `benchmarks/results/full/order-correctness/baseline.json`
- `benchmarks/results/full/async-reliability/outbox-kafka.json`
- `benchmarks/results/full/async-reliability/direct.json`
- 对应 `.md` 文件同目录生成。

full 结果摘要：

| Suite | Mode | 关键结果 |
| --- | --- | --- |
| order correctness | improved | repeated submit 10000 次，logicalOrders = 1，duplicateOrders = 0；concurrent checkout 200 次，oversellCount = 0 |
| order correctness | baseline | repeated submit 10000 次，logicalOrders = 10000，duplicateOrders = 9999；concurrent checkout 200 次，oversellCount = 170 |
| async reliability | outbox-kafka | syntheticOrders = 10000，completedOrders = 10000，publishedEvents = 20000，processedEvents = 20000，retryCount = 4，dlqCount = 3 |
| async reliability | direct | syntheticOrders = 10000，completedOrders = 10000，outboxEvents = 0，dlqCount = 0 |

## Full-run 命令边界

完整目标命令已经实现，并且本轮已经运行通过：

完整 evidence package：

```bash
./scripts/benchmark/run-evidence-package
```

默认目标：

- correctness repeated submit：10000 次。
- correctness concurrent checkout：200 次。
- async reliability synthetic orders：10000 个。

如果后续机器环境或代码改变，应重新运行 full package，并把结果摘要更新到 `docs/internal/reports/final-evidence.md`。

## Tradeoff

- CI 跑 smoke，不跑 full load。原因是 CI 要快速发现回归，不能每次 PR 都跑 10K 级别 benchmark。
- async benchmark 在测试环境里使用 `recording` broker，而不是拉起真实 Redpanda。原因是 M5 benchmark smoke 的目标是稳定验证 outbox/retry/DLQ 逻辑和报告生成；真实 Docker Compose + Redpanda 仍然保留在本地 demo runtime。public docs 已明确这个边界，避免把 recording broker 结果说成真实 Kafka broker benchmark。
- benchmark results 继续 ignored。原因是每台机器每次运行的数字会变，public 仓库只保留 runner 和文档，internal report 保存关键摘要。

## Code Review 发现与修复

本轮按 `superpowers:requesting-code-review` 派出独立 reviewer 检查 `868ff77..dbba5b3` 的 M5 diff。结论是：方向正确、范围干净、没有 Critical，但合并前需要修 evidence package 的可信度缺口。

发生了什么：

- smoke 和 full benchmark 原本都写到同一组文件名，例如 `benchmarks/results/order-correctness/improved.json`。如果先跑 full 再跑 smoke，smoke 会覆盖 full 证据文件，导致 internal report 里的 10K 数字和本地 JSON 文件不一致。
- benchmark 测试原本主要断言 JSON / Markdown 文件存在，没有断言关键业务指标。这样未来即使 improved mode 退化出 duplicate orders 或 oversell，只要报告还能写文件，CI benchmark smoke 仍可能通过。
- async reliability benchmark 的 mode 名叫 `outbox-kafka`，但测试里为了稳定使用的是 `recording` broker。这个选择本身合理，但文档没有讲清楚，reviewer 可能误以为 benchmark 覆盖了真实 Redpanda/Kafka broker path。

为什么会这样：

- M5 一开始的重点放在“把报告生成链路接起来”，所以报告文件存在性先被验证了，但还没有把 benchmark 的 invariant 也放进测试断言。
- smoke/full 的输出目录没有拆开，是因为脚本入口先追求简单；但 evidence package 的核心价值是“证据可追溯”，简单路径反而会造成覆盖风险。
- `recording` broker 是为了 CI 稳定、快速、可重复；真实 Kafka/Redpanda 启动更重，适合 Docker Compose runtime smoke，不适合每次 benchmark report smoke。

怎么解决：

- `scripts/benchmark/* --smoke` 现在写入 `benchmarks/results/smoke/...`，full run 写入 `benchmarks/results/full/...`，避免 smoke 覆盖 full evidence。
- benchmark 测试增加 mode-aware sanity assertions：correctness improved 必须 `duplicateOrders = 0`、`oversellCount = 0`；baseline repeated submit 必须能暴露 duplicate；async outbox benchmark 必须完成所有 synthetic orders、清空 pending outbox、验证 manual retry success/failure 和 retry/DLQ 指标。
- public docs 和 internal docs 都补充说明：async benchmark 使用 recording broker 生成确定性证据；Docker Compose runtime 仍然是 Redpanda/Kafka-backed `outbox-kafka` 默认路径。
- benchmark 脚本增加缺值提示；correctness smoke 的并发请求从 25 调到 50，让 smoke 报告也更容易暴露 baseline oversell 对照。

## 还没有覆盖什么

- 没有做 M6 真实 AWS 部署。
- 没有把 10K full benchmark 结果提交成 tracked public artifact。
- 没有生成视频或截图包；这属于 M5 Stretch，不是 Minimum Acceptance。
- 没有把 ServiceObs 或 OperationAssistant 接成强 contract，只保留 OrderFlow 自己的 sample/demo 能力。

## 面试时可以怎么解释

可以这样说：

“我把订单系统的功能实现和工程证据分成两层。前面实现订单状态机、幂等、乐观锁、outbox、retry、DLQ 和 React 运维台；最后一个 evidence milestone 负责让这些能力可复跑：CI 会跑 backend/frontend/e2e/benchmark smoke，benchmark runner 可以在当前代码里切换 baseline 和 improved，并输出 JSON/Markdown 报告。这样我讲 0 duplicate、0 oversell、retry/DLQ/manual retry 的时候，不只是口头说，而是有命令、测试和报告路径支撑。”

更深入时可以解释：

- baseline 不是产品路径，只是为了证明机制价值。
- improved 才是默认 runtime。
- CI smoke 和 full benchmark 的目标不同：一个守回归，一个产证据。
- JSON 给机器读，Markdown 给 reviewer 读。
