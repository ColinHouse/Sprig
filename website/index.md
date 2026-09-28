---
layout: home
hero:
  name: Sprig
  text: 一起编写可预测的 JVM 工具
  tagline: 小型、显式的语言，面向工具、自动化和可靠应用代码。查询编译器、修改、检查、理解并修复。实验性 Alpha；JDK 17+。
  image:
    src: /logo-round.png
    alt: Sprig
  actions:
    - theme: brand
      text: 五分钟开始
      link: /guide/getting-started
    - theme: alt
      text: 与 Agent 一起贡献
      link: /en/project/contributing
    - theme: alt
      text: GitHub
      link: https://github.com/ColinHouse/Sprig
features:
  - title: 可读的工具与 AST
    details: 缩进式语法、显式泛型、封闭 variant 与穷尽 match，适合命令行工具、配置处理与源码分析。
  - title: 可查询的编译器反馈
    details: capabilities、主题帮助、JVM 签名和稳定 JSON 诊断，让人与编码 Agent 共享可验证的语言依据。
  - title: 显式的运行边界
    details: 受检整数、精确 Decimal/BigInt、保守 Java 可空性与可复现依赖锁，让失败清晰可见。
---

## 可以做什么？

仓库自动化、数据转换、小型 JVM 应用和编译器工具。源代码里程碑的
[showcases](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases)
包括仓库审计器、真实 Maven 库应用与源码分析器；各自 README 提供输入、命令和边界。

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
v0.4 在 v0.3 的基础上加入 canonical formatter、显式模块 re-export、表达式 match、
Unicode code-point 字符串语义、`sprig api` 模块/项目内省、managed SDK 升级和一轮
对抗正确性修复。已发布 SDK 的功能请查看[发行状态](/project/release-status)、发行资产
和 capability 输出。
Sprig 尚未自举，也不承诺生产可用；发布/registry、LSP、接口与泛型推断仍属未来工作。
见[已知限制](/reference/known-limitations)。

## 一起贡献

想用 Codex / Claude / ChatGPT 参与？挑选一个
[agent-friendly 任务](https://github.com/ColinHouse/Sprig/issues?q=is%3Aissue+is%3Aopen+label%3Aagent-friendly)，
让 Agent 读 AGENTS.md，运行验证、审查补丁并提交 PR。
[贡献指南（英文）](/en/project/contributing)提供完整短流程。欢迎 AI 辅助，提交者负责
理解变更、测试、许可与正确性。

Apache-2.0 · [English](/en/) · [语言导览](/guide/language-tour) ·
[工具与 JSON](/guide/tooling)
