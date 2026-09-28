# 示例

下面的教程都是可执行文档：每个程序位于 `website/snippets/tutorial/`，文档门禁在每次
验证时运行它并与已检入的 `.out` oracle 逐字节比较。清单是真实文件，不是重新录入的
副本；中文和英文页面包含同一份 Sprig 源码。想要看“用 Sprig 能做出什么”，请前往
[应用程序示例](#应用程序示例)。

## Hello world

<<< @/snippets/tutorial/hello.spr

<<< @/snippets/tutorial/hello.out

## FizzBuzz

<<< @/snippets/tutorial/fizzbuzz.spr

<<< @/snippets/tutorial/fizzbuzz.out

<details>
<summary>为什么输出是这样</summary>

程序遍历 `range(1, 16)`，每行打印一个值：3 的倍数替换为 `Fizz`，5 的倍数替换为
`Buzz`，15 的倍数替换为 `FizzBuzz`。

</details>

## Shapes：类、variant 与 match

<<< @/snippets/tutorial/shapes.spr

<<< @/snippets/tutorial/shapes.out

这个例子组合了带默认值的类、sealed `variant`、`enum`、两个穷尽 `match`、可空返回
类型以及不可变集合。

## 词频统计

<<< @/snippets/tutorial/word_count.spr

<<< @/snippets/tutorial/word_count.out

## 数值精度策略

<<< @/snippets/tutorial/numeric_science.spr

<<< @/snippets/tutorial/numeric_science.out

均值使用 binary64 `Float`，金额式的值使用 `Decimal`，因此 `0.1 + 0.2` 精确等于
`0.3`；最后一行把浮点误差显式展示出来，而不是隐藏它。

## 应用程序示例

`examples/` 存放有独立用途的程序与项目，而不是语法演示。先看
[示例总览](https://github.com/ColinHouse/Sprig/blob/main/examples/README.md)：

- [mini_web](https://github.com/ColinHouse/Sprig/tree/main/examples/mini_web)：类型化路由、JSON、OpenAPI。
- [sqlite](https://github.com/ColinHouse/Sprig/tree/main/examples/sqlite)：锁定版本的 Maven JDBC 驱动与持久化预处理 SQL。
- [ledger](https://github.com/ColinHouse/Sprig/tree/main/examples/ledger)：精简的账户/交易 HTTP 后端，支持重启后持久化。
- [sqlite_migrations](https://github.com/ColinHouse/Sprig/tree/main/examples/sqlite_migrations)：有序、事务化的 SQLite migrations。
- [json_select](https://github.com/ColinHouse/Sprig/tree/main/examples/json_select)：多文件 JSON CLI。
- [agent_tools](https://github.com/ColinHouse/Sprig/tree/main/examples/agent_tools)：用 Sprig 编写的编译器 API 与诊断工具。
- [showcases](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases)：仓库审计器、Maven 工具和源码分析器。

## 测试套件中的更大程序

- `tests/runtime/`：19 个带 golden stdout 的端到端程序，覆盖算术、函数、控制流、
  类、variant、enum、可空性、错误、集合、lambda、字符串、模块、断言、格式化与
  JVM 互操作。
- `tests/visitor/ast_visitor.spr`：完全用 Sprig 编写的小型 AST 解释器，包含四个
  visitor 类（打印、求值、化简、节点计数）；给 variant 增加 case 会让漏掉它的
  visitor 编译失败。
- `tests/visitor/mini_pipeline.spr`：自举可行性切片实验。
- `tests/numeric/`：受检算术、转换与精度测试，并带有独立的 Python oracle。
