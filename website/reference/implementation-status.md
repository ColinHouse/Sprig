# 实现状态摘要

> 本页是中文摘要；权威英文文档为
> [Implemented features](/en/reference/FEATURE_STATUS_IMPLEMENTED)，它随验证用的测试
> 一起维护。两者不一致时以英文文档和实际测试结果为准。仓库开发版本：`0.1.0-alpha.2`；已发布版本：`0.1.0-alpha.1`。

下表描述仓库内 Java stage-0 编译器**实际做到**的事情。

## 已实现并经过测试

| 功能 | 说明 |
|---|---|
| 函数、显式参数与返回类型、递归 | 生成为 Java 静态方法。 |
| 缩进块、`if`/`elif`/`else`、`while`、`for`、`break`/`continue` | 生成为 Java 控制流。 |
| `let`/`var`、局部类型推断、赋值规则 | `let` 重新赋值会报 `SPR-NAME-LET-ASSIGN`。 |
| 类：具名自动构造、字段默认值、方法 | 生成为 final Java 类。 |
| `enum` 与穷尽 `match` | Java enum 加 if 链。 |
| `variant`、类型化 case binder、穷尽 `match` | sealed interface 加嵌套 case 类。 |
| 缺失/重复/错误类型的 case、enum binder 拒绝 | `SPR-MATCH-*` 诊断。 |
| 可空类型 `T?`、判空与流分析收窄 | 装箱的可空局部变量。 |
| `List`/`MutableList`/`Map`/`MutableMap`、快照、索引、`in` | 运行时包装类，可变性类型分离。 |
| Lambda `fn(...) => expr`，参数 0–3 个，`map`/`filter`/`forEach` | `Fn0`–`Fn3` 匿名类。 |
| `throw`/`throws`/`try`/`catch`/`finally` | Java 异常；`Error.message`。 |
| 函数必须有返回值、不可达代码检查 | `SPR-FLOW-*`。 |
| 模块：文件导入、别名访问、只初始化一次、环检测 | 每个模块一个静态 `$init()`。 |
| JDK 互操作：导入、构造器、静态/实例方法与字段、重载 | 引用结果可空、参数保守非空。 |
| 受检 `Int`/`Int32`、显式整数除法、字面量范围 | `NumericOps` 运行时检查。 |
| `BigInt`、`Decimal`、IEEE `Float`/`Float32` 与显式转换 | 见[数值语义（英文）](/en/reference/NUMERIC_SEMANTICS)。 |
| `check`/`build`/`run`/`explain`/`codes`/`help`/`capabilities`/`api`/`doctor`、`--json`、`--syntax-only` | 单一 `bin/sprig` 可执行文件；新命令属于 alpha.2 开发版本。 |
| 显式本地 `--classpath` | `api`/`check`/`build`/`run` 使用同一 JAR 或目录路径；没有自动下载。 |

## 尚未实现

用户自定义泛型、继承与接口、`match` 表达式、嵌套/位置模式、源码中的函数类型、
`%=`、元组与解构、数组/变长参数、完整的 Java 泛型与注解互操作、文件 IO 库、
包清单与依赖管理、LSP/IDE 集成、增量检查、自举（stage-1）。

完整边界与原因见[已知限制](/reference/known-limitations)和英文的
[Known limitations](/en/reference/KNOWN_LIMITATIONS)。
