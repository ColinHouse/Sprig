---
layout: home

hero:
  name: Sprig
  text: 面向 JVM 的缩进式静态类型语言
  tagline: sealed variant、穷尽 match、受检数值和显式 JVM 互操作。目前由仓库内的 Java stage-0 编译器实现。
  image:
    src: /logo-round.png
    alt: Sprig 吉祥物
  actions:
    - theme: brand
      text: 快速开始
      link: /guide/getting-started
    - theme: alt
      text: 语言导览
      link: /guide/language-tour
    - theme: alt
      text: GitHub
      link: https://github.com/ColinHouse/Sprig

features:
  - title: 每种操作只有一种写法
    details: 缩进分块、func 声明函数、类和 variant 用具名构造、普通函数与 JVM 调用用位置参数。没有别名，没有管道操作符。
  - title: 静态而诚实的类型
    details: 可空类型 T? 与流分析收窄，不可变与可变集合类型分离，数值不做隐式提升，没有 truthiness，也没有 Any 逃生口。
  - title: sealed variant 与穷尽 match
    details: variant 声明封闭的 sum type，字段具名且不可变；match 会检查缺失、重复和错误的分支。给 variant 增加一个 case，漏掉它的 visitor 就编译失败。
  - title: 受检数值与显式互操作
    details: Int/Int32 溢出会报错而不是回绕，整数除法必须显式；BigInt/Decimal 精确，Float 保持 IEEE 754。Java 引用结果视为可空，使用前必须判空。
---

## 当前阶段：`0.1.0-alpha.1`

Sprig 仍是实验性项目。编译器是 Java 编写的 stage-0 实现：解析 `.spr`
源码、做类型检查、生成 Java 源码、调用 `javac` 并在 JVM 上运行。它
**尚未自举**，目前没有包管理器、语言服务器、IDE 插件或标准库发行版。

::: info 发行状态
**`v0.1.0-alpha.1` 已作为 prerelease 发布**：在
[Releases](https://github.com/ColinHouse/Sprig/releases/tag/v0.1.0-alpha.1)
下载 ZIP 与 `.sha256` 校验文件（包含编译器、runtime、ANTLR 与启动脚本，**不含 JDK**），
也可以按[快速开始](/guide/getting-started)从源码构建。这是 Alpha 版本，不是稳定版；
当前状态与验证范围见[发布状态](/project/release-status)。
:::

## 第一个程序

<<< ../examples/hello.spr

```bash
git clone https://github.com/ColinHouse/Sprig.git
cd Sprig
./scripts/build.sh
./bin/sprig run examples/hello.spr
```

```text
Hello, Ada!
```

需要 JDK 17 或更新版本；首次构建会从 Maven Central 下载 ANTLR 4.13.2
并校验固定的 SHA-256。更多命令见[快速开始](/guide/getting-started)与
[工具与 JSON](/guide/tooling)。

## 从哪里开始

- [快速开始](/guide/getting-started)：环境要求、构建、Hello World 与命令行。
- [语言导览](/guide/language-tour)：绑定、函数、类、variant、集合、可空性与错误。
- [示例](/examples)：由当前编译器实际执行过的程序。
- [实现状态摘要](/reference/implementation-status)：已经实现、部分支持与尚未实现的功能。
- [已知限制](/reference/known-limitations)：Alpha 阶段明确的边界。
- [英文技术参考](/reference/index)：语言规范、数值语义、诊断码等权威文档（英文）。

## 项目

源码与问题反馈：<https://github.com/ColinHouse/Sprig>。项目采用
Apache-2.0 许可证；欢迎 AI 辅助贡献，但必须通过构建、测试与审查，并说明你
实际验证过的内容。详见[参与贡献（英文）](/en/project/contributing)与
[AI 辅助开发声明（英文）](/en/project/ai-disclosure)。
