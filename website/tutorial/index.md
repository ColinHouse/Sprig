# Sprig 程序设计语言

欢迎。这本书从零开始讲 Sprig：一门跑在 JVM 上、语法像 Python 的小语言，写错的时候编译器会指出错在哪一行、为什么错、可以怎么改。

书里的每一段代码都真实跑过，代码下面的输出就是它打印的内容；每一段"故意写错"的代码也都真实地被编译器拒绝过，报错信息原样照录。你不需要事先会写程序，也不需要会 Java。

## Sprig 是什么样的语言

先看一个完整的程序：

<<< @/snippets/book/ch09_errors.spr

```text
42
failed: not a number: abc
done
```

十几行里已经有 Sprig 的几个核心主张：

- **缩进就是结构。** 没有花括号，也没有分号，一行写一件事。
- **类型写在签名上，推断留给局部。** 函数的参数和返回值都要写类型；函数体里的 `let value = ...` 不用。
- **可能失败的事写进类型。** `throws Error` 是签名的一部分，调用方要么处理，要么也声明。没有悄悄传出去的异常。
- **可能为空的值也写进类型。** 要判断过 `== null` 之后才能当成非空的值用。
- **没有隐式转换，没有"非零即真"，没有兜底分支，也没有继承。** 这些在后面的章节里会一个个遇到。

这个程序用到了函数、Error、`try`/`catch`/`finally`，现在看不懂没关系：第 7 章讲函数，第 14 章讲错误处理。

## 这本书写给谁

写给没写过程序的人，也写给从别的语言过来的人。

- 如果这是你的第一门语言：按顺序读，每章先看例子、再看输出、最后做练习。练习的参考答案是可以折叠的，先自己想。
- 如果你会 Python、JavaScript 或 Java：前几章可以快一点，但第 3 章（数字）和第 13 章（可空值）讲的是 Sprig 和其他语言不一样的地方，别跳过。[附录 D](/tutorial/appendix-d-from-other-languages) 把你的旧习惯一条条映射到 Sprig，可以边读边查。

## 怎么读这本书

一共 24 章，分成三个部分，后面还有附录。

每一章的开头列出这一章你会学到什么，结尾有小结和动手练习。书里的约定：

- 代码块下面紧跟的 `text` 块是运行结果。
- 标着"故意写错"的小节里，代码**不能**通过编译，下面的 `text` 块是编译器的真实报错。报错开头的 `SPR-...` 是错误码，`sprig explain 错误码` 会给出完整解释。
- 书中的每个完整程序都是仓库里真实存在的 `.spr` 文件，可以用 `sprig run 文件名` 直接运行。
- 报错来自编译器，不是手工抄的；如果换了别的版本，措辞可能不同，错误码不变。

## 需要哪个版本的编译器

这本书使用 0.8 语言，编译器在 `sprig capabilities` 中将它写成 `language 0.8-dev`。SDK 的发布版本与语言版本是两回事。

当前已发布的 [v0.8.0-beta.1 SDK](https://github.com/ColinHouse/Sprig/releases/tag/v0.8.0-beta.1)包含本书使用的语法和标准模块，需要 JDK 21+。直接安装 SDK 即可开始，不需要先构建编译器。第 1 章会一步步说明[安装和版本检查](/tutorial/ch01-tools)。网站随 `main` 分支维护；后续版本差异见[发布状态](/project/release-status)，实际功能以 `sprig capabilities --json` 为准。

## 章节地图

### 第一部分：从零开始写程序

| 章 | 你会学到 |
|---|---|
| [1. 准备工具](/tutorial/ch01-tools) | 终端、JDK、编译器、编辑器，以及怎么确认版本 |
| [2. 第一个程序与读懂报错](/tutorial/ch02-first-program) | `print`、注释、报错的四个部分、`sprig explain` |
| [3. 值、变量和算术](/tutorial/ch03-values) | `let`/`var`、类型、整数和浮点数的规则、`Bool` |
| [4. 文字](/tutorial/ch04-text) | 转义、常用字符串方法、码点、`@std/text` |
| [5. 做决定：if](/tutorial/ch05-if) | 条件、`elif`、条件表达式 |
| [6. 重复：循环](/tutorial/ch06-loops) | `while`、`for`、`range`、`break`/`continue` |
| [7. 函数](/tutorial/ch07-functions) | 参数、返回值、作用域、递归、命名约定 |
| [8. 列表](/tutorial/ch08-lists) | `List` 和 `MutableList`、常用方法、排序 |
| [9. 映射和集合](/tutorial/ch09-maps-sets) | `Map`、计数套路、`@std/sets` |
| [10. 小项目：猜数字](/tutorial/ch10-project-guess) | 读输入、命令行参数、随机数 |

### 第二部分：用类型描述世界

| 章 | 你会学到 |
|---|---|
| [11. 类和对象](/tutorial/ch11-classes) | 字段、方法、一行类、同一性 |
| [12. 枚举、variant 和 match](/tutorial/ch12-enums-variants) | 封闭的类型集合、穷尽匹配、递归 variant |
| [13. 可空值](/tutorial/ch13-nullable) | `T?`、收窄、`or_else` |
| [14. 错误处理](/tutorial/ch14-errors) | `throws`、错误类、重新抛出、`finally` |
| [15. 函数作为值](/tutorial/ch15-functions-as-values) | lambda、函数引用、捕获、`throws` 的函数类型 |
| [16. 泛型与契约类](/tutorial/ch16-generics-contracts) | `generic`、`requires`、契约类和 `conform` |
| [17. 数字进阶](/tutorial/ch17-numbers) | `Int32`/`Float32`、转换表、`Decimal`/`BigInt`、`@std/math` |

### 第三部分：做真正的程序

| 章 | 你会学到 |
|---|---|
| [18. 模块、项目和依赖](/tutorial/ch18-modules-projects) | `import`、`sprig.toml`、锁文件、依赖 |
| [19. 测试](/tutorial/ch19-testing) | `@std/test`、`sprig test`、compile-fail 测试 |
| [20. 标准库实用篇](/tutorial/ch20-stdlib) | 文件、JSON、日期时间、正则 |
| [21. 调用 Java](/tutorial/ch21-java) | `import` Java 类、可空返回值、集合适配 |
| [22. 并发](/tutorial/ch22-concurrency) | 虚拟线程、任务、channel、锁 |
| [23. 工具链与 AI 助手](/tutorial/ch23-tooling) | LSP、`fmt`、结构化查询、和助手一起写 |
| [24. 综合项目：记账](/tutorial/ch24-project-ledger) | 把整本书用在一个完整程序上 |

### 附录

| 附录 | 内容 |
|---|---|
| [A. 运算符与优先级](/tutorial/appendix-a-operators) | 全部运算符、优先级表、结合性 |
| [B. 报错码速查](/tutorial/appendix-b-error-codes) | 稳定的 `SPR-` 错误码分组列表 |
| [C. 关键字和内置函数](/tutorial/appendix-c-keywords) | 关键字、内置函数、内置方法 |
| [D. 从 Python、JavaScript、Java 过来](/tutorial/appendix-d-from-other-languages) | 旧习惯到 Sprig 的对照 |
| [E. Sprig 没有的东西](/tutorial/appendix-e-not-in-sprig) | 有意省略的功能和替代写法 |
| [F. Java 互操作进阶](/tutorial/appendix-f-advanced-java) | 通配、注解、父类、`wrap` |

## 这本书不讲什么

它不是语言规范，也不是完整的 API 列表。语法的逐条速查见[语言速查](/guide/language-tour)，数值、可空性和泛型等契约的精确表述见[参考](/reference/index)；Fabric 模组、Gradle 集成、Web 与 SQLite 这些专题各有自己的指南。

准备好了就从[第 1 章：准备工具](/tutorial/ch01-tools)开始。之后按顺序走，别跳章；每一章都只用前面讲过的东西。
