# 技术参考（英文）

Sprig 的**权威技术参考目前只有英文版本**，它们由仓库根目录的文档在站点构建时自动
生成，只有一份权威来源，避免多语言手工同步产生偏差。中文页面提供实现状态与限制的
摘要，并链接到对应英文原文。

## 语言契约

| 文档 | 内容 |
|---|---|
| [Language specification (v0.7 design)](/en/reference/LANGUAGE_SPEC) | 语言设计契约；描述目标语义，未全部实现。 |
| [Quick reference](/en/reference/QUICK_REFERENCE) | 当前编译器支持的简明示例。 |
| [Numerical semantics](/en/reference/NUMERIC_SEMANTICS) | **规范文档**：整数范围、溢出、除法、转换、Decimal/BigInt、IEEE 浮点。 |
| [Numeric design decisions](/en/reference/NUMERIC_DESIGN_DECISIONS) | 数值规则的设计取舍与剩余工作。 |
| [Grammar](/en/reference/grammar) | `.g4` 语法的权威说明与构建方式。 |

## 实现与诊断

| 文档 | 内容 |
|---|---|
| [Implemented features](/en/reference/FEATURE_STATUS_IMPLEMENTED) | 已实现功能及其对应测试。 |
| [Known limitations](/en/reference/KNOWN_LIMITATIONS) | 当前边界。 |
| [Diagnostic codes](/en/reference/DIAGNOSTIC_CODES) | 稳定诊断码清单。 |
| [JVM interop](/en/reference/JVM_INTEROP) | `sprig api`、classpath、可空与不支持的 Java 边界。 |
| [Stage-1 roadmap](/en/reference/STAGE1_ROADMAP) | 自举路线的计划，未实现。 |
| [Agent tool protocol](/en/reference/AGENT_TOOL_PROTOCOL) | 历史设计提案；`sprig api` 已实现，LSP 等仍未实现。 |

中文摘要见[实现状态摘要](/reference/implementation-status)与
[已知限制](/reference/known-limitations)。
