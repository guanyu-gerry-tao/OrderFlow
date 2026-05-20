# M5 Cut Line

## 本轮完成的 Core / Minimum Acceptance

- CI 从 backend-only 扩展到 backend、frontend、e2e、benchmark smoke。
- order correctness benchmark 可生成 JSON + Markdown。
- async reliability benchmark 可生成 JSON + Markdown。
- baseline / improved mode 保持在当前代码中可运行。
- demo seed/script 已提供，不引入其它项目强 contract。
- public docs 已补充工程中性架构、benchmark、demo、CI 信息。
- internal evidence package、理解报告、highlight inventory 已创建。
- full evidence package 已运行，并刷新了 10K repeated-submit、200 concurrent checkout、10K async synthetic order 数字。
- code review 后已补强 evidence 可信度：smoke/full 输出隔离、benchmark 关键指标断言、recording broker 文档边界。

## 本轮没有做的 Stretch

- 没有生成 screenshots / video capture。
- 没有把 Mermaid 图导出成图片，只在 public architecture docs 中保留 Mermaid。
- 没有做 cloud notes 或真实 AWS 部署。
- 没有把 demo script 扩展成复杂的一键验收器；它只负责启动和 seed。

## 原因

M5 的 Minimum Acceptance 是可复跑证据包和 CI 收口。截图、视频、云部署和更漂亮的 demo 都会扩大范围，而且不是证明当前订单可靠性主线所必需。

## 后续建议

如果继续做 M6，先确认当前 full evidence package 数字是否仍然是最新，再开始 AWS 部署。这样 cloud benchmark 可以和本地 improved baseline 做对比，而不是混着改。
