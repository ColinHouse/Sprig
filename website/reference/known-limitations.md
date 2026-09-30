# 已知限制

> 本页是中文摘要；权威英文文档为
> [Known limitations](/en/reference/KNOWN_LIMITATIONS)。以下描述 Java stage-0
> 实现，而不是历史设计稿中提议的全部特性。

- 编译器用 Java 编写，先输出 Java 源码再调用 `javac`；它**不能编译自身**，也未自举。
- 本地/Git/Maven 解析及小型 std 层已实现；VS Code 有独立桌面适配器，提供高亮、
  编译器诊断与运行。发布/registry、LSP 和调试器尚未实现。
- JVM 互操作覆盖常见导入类、构造器、字段、方法调用、重载与受检异常，并保留具体 Java
  泛型实参（`ArrayList[String]`、显式泛型方法）；数组作为不透明值传递，集合通过
  `@std/jvm.spr` 显式快照/拷贝。wildcard、变长参数、source 数组语法、泛型推断与
  Java type-use 可空性注解解释不在 profile 内，并给出结构化原因。Java 引用结果保守
  地视为可空，Java 引用参数保守地视为非空。
- `throws`/`catch` 已实现，但它们与 Java 异常类、顶层执行的关系仍是临时约定。
- `sprig wrap` 生成普通可编辑源码，默认拒绝覆盖（需 `--force`），并在写出前同一
  classpath 下检查；`sprig test` 顺序运行、固定 30 秒超时、无并行/覆盖率/快照。
- 源码函数类型 `fn(A) -> R` 与 Sprig 自有 `Fn0`–`Fn3` JVM 桥接支持 0–3 个参数；
  参数/结果不变、不能声明 throws，不支持任意 Java SAM 接口。
- Lambda 只有单表达式体，支持 0–3 个参数，不能声明 `throws`；调用受检异常操作必须在
  lambda 内部处理。
- 运行时受检整数的 `Int`/`Int32` 溢出报告稳定诊断、最近语句范围与
  `data.origin="checked-arithmetic"`（范围是语句而非子表达式）；JVM 库内部的运算不受
  Sprig 受检整数规则保护。
- 浮点遵循 Java `float`/`double` 行为；项目不承诺跨 JVM 的位级一致性、数值稳定性、
  物理量单位或算法正确性。
- 构建与测试证据来自 macOS Apple Silicon 上的 OpenJDK 17.0.19 与 26.0.1，以及 Linux
  上的托管 CI；没有验证其他平台或处理器架构。
- Java 17 及以上为受支持运行时；更早的 JDK 未验证。

完整列表与英文原文见
[Known limitations](/en/reference/KNOWN_LIMITATIONS)。
