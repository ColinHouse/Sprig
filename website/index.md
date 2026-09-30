---
layout: home
hero:
  name: Sprig
  text: 少一点猜测，多一点可验证
  tagline: Sprig 不试图让编码 Agent 更聪明，而是尽量减少它需要猜测的内容。面向人和 Agent；Agent 友好，也应当方便审查。实验性 Alpha；JDK 17+。
  image:
    src: /logo-round.png
    alt: Sprig
  actions:
    - theme: brand
      text: 开始教程
      link: /tutorial
    - theme: alt
      text: 与 Agent 一起贡献
      link: /en/project/contributing
    - theme: alt
      text: GitHub
      link: https://github.com/ColinHouse/Sprig
features:
  - title: 先写能读懂的程序
    details: 显式函数类型、清晰绑定与小型 JVM 程序，让源码便于人类检查与讨论。
  - title: 用编译器查询代替猜测
    details: 能力、主题帮助、Java 签名和 JSON 诊断提供可复现的实现证据。
  - title: 让修改可审查
    details: 类型检查、穷尽 match、受检数值和锁定依赖帮助尽早暴露具体问题；它们不保证算法本身正确。
---

## 可以做什么？

仓库自动化、数据转换、小型 JVM 应用和编译器工具。源代码里程碑的
[showcases](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases)
包括仓库审计器、真实 Maven 库应用与源码分析器；各自 README 提供输入、命令和边界。

## 早期 dogfood：反馈比性能结论更重要

维护者报告曾用一个较低成本的编码模型尝试真实 Sprig 工作流。这是早期、轶事式的产品 dogfood：没有受控实验、等价 Java 对照实现、预注册任务集或生产力指标，因此不能据此声称 Sprig 优于 Java、提升了多少效率或消除了模型错误。它只支持一个较窄的观察：编译器查询和结构化诊断可以为修复提供具体证据。我们欢迎外部用户复现并报告体验。

## 从一个项目开始

[安装 SDK 或从源码构建](/guide/getting-started)，然后：

```bash
sprig version
sprig init my-tool
cd my-tool
sprig resolve
sprig run
```

输出：`Hello, Sprig!`。随后查询 `sprig capabilities --json`，修改
`src/main.spr`，用 `sprig check --json` 理解并修复错误。

## 发行状态与限制

公开 SDK 是 [v0.4.0-alpha.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.4.0-alpha.1)。
v0.4 在 v0.3 基础上加入 canonical formatter、显式模块 re-export、
表达式 match、Unicode code-point 字符串语义、`sprig api` 模块/项目内省、managed SDK
升级和一轮对抗正确性修复。已发布 SDK 的功能请查看[发行状态](/project/release-status)、发行资产和 capability 输出。
当前源码（尚未进入 v0.4.0-alpha.1 发行资产）还包含：显式 Java 具体泛型、opaque
数组与 `@std/jvm.spr` 显式集合适配器、`sprig wrap` wrapper 生成器、`sprig test`
项目测试与 schema-4 owner-relative 便携本地锁。真实 Fabric/Loom dogfood 的端到端
接线见 [Fabric / JVM 框架集成](/guide/fabric)。

Sprig 尚未自举，也不承诺生产可用；发布/registry、LSP、接口与泛型推断仍属未来工作。
见[已知限制](/en/reference/language/known-limitations)。

## 一起贡献

想用 Codex / Claude / ChatGPT 参与？挑选一个
[agent-friendly 任务](https://github.com/ColinHouse/Sprig/issues?q=is%3Aissue+is%3Aopen+label%3Aagent-friendly)，
让 Agent 读 AGENTS.md，运行验证、审查补丁并提交 PR。
[贡献指南（英文）](/en/project/contributing)提供完整短流程。欢迎 AI 辅助，提交者负责
理解变更、测试、许可与正确性。

Apache-2.0 · [English](/en/) · [语言导览](/guide/language-tour) ·
[工具与 JSON](/guide/tooling)
