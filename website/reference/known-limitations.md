# 已知限制

> 本页是中文摘要；权威英文文档为
> [Known limitations](/en/reference/KNOWN_LIMITATIONS)。以下描述 Java stage-0
> 实现，而不是 v0.7 设计稿中提议的全部特性。

- 编译器用 Java 编写，先输出 Java 源码再调用 `javac`；它**不能编译自身**，也未自举。
- 本地/Git Sprig 包解析已实现；Maven 解析、发布/registry、标准库发行版、LSP/IDE、
  调试器和编辑器集成尚未实现。
- JVM 互操作覆盖常见导入类、构造器、字段、方法调用、重载与受检异常。Java 泛型签名、
  type-use 可空性注解、数组、变长参数与集合适配是有限或不支持的。Java 引用结果保守
  地视为可空，Java 引用参数保守地视为非空。
- `throws`/`catch` 已实现，但它们与 Java 异常类、顶层执行的关系仍是临时约定。
- Lambda 只有单表达式体，支持 0–3 个参数，不能声明 `throws`；调用受检异常操作必须在
  lambda 内部处理。
- 运行时数值错误会报告诊断，但**不总是携带算术表达式的精确源码范围**；JVM 库内部的
  运算不受 Sprig 受检整数规则保护。
- 浮点遵循 Java `float`/`double` 行为；项目不承诺跨 JVM 的位级一致性、数值稳定性、
  物理量单位或算法正确性。
- 构建与测试证据来自 macOS Apple Silicon 上的 OpenJDK 17.0.19 与 26.0.1，以及 Linux
  上的托管 CI；没有验证其他平台或处理器架构。
- Java 17 及以上为受支持运行时；更早的 JDK 未验证。

完整列表与英文原文见
[Known limitations](/en/reference/KNOWN_LIMITATIONS)。
