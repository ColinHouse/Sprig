# 附录 B. 报错码速查

编译器的每个诊断都带一个固定的错误码，以 `SPR-` 开头。码的含义不会改变：新行为用新码。这份附录按类别列出编译器当前的 102 个码，每个码一行；码和数量都会随编译器更新，最新列表永远以 `sprig codes` 为准。

## B.1 怎么读一个错误码

报错的第一行有四个部分：

```text
SPR-NUM-DIVISION [TYPE] main.spr:2:7: Integer / would truncate; use a.divTrunc(b), or convert both operands explicitly (expected explicit division, actual Int / Int)
```

- `SPR-NUM-DIVISION`：错误码，稳定不变；
- `[TYPE]`：出错的阶段（词法 `LEX`、语法 `SYNTAX`、名字 `NAME`、类型 `TYPE`、流程 `FLOW`、运行时 `RUNTIME`……）；
- `main.spr:2:7`：文件、行号和列号（从 1 开始数）；
- 后面是消息；如果有 `hint:` 行，那是编辑器里"快速修复"也会用的建议。

想看完整的解释，把码交给 `sprig explain`：

```text
$ sprig explain SPR-NUM-DIVISION
SPR-NUM-DIVISION: Integer / is rejected; use divTrunc for deliberate truncation or explicit Float/Decimal arithmetic.
Why it matters: Integer / silently truncates in many languages; Sprig requires the intent to be explicit.
Common causes:
  - Integer division was written with / instead of divTrunc.
Safe fixes:
  - Use value.divTrunc(divisor) only when truncation is intended.
  - Convert to Float or Decimal when fractional arithmetic is intended.
Good:
  let q = (7).divTrunc(2)
Bad:
  let q = 7 / 2
```

`sprig explain <CODE> --json` 给出同一份信息，还带上出错阶段、相关章节（`relatedHelp`，可以直接 `sprig help 该主题`）和 `repair` 建议，适合编辑器和 AI 助手读取。`sprig check file.spr --json` 则把一次检查的全部诊断按结构化 JSON 输出。

下表按前缀分类；每条只写"什么时候出现"。怎么修、为什么这样设计，用 `sprig explain` 看。

### B.2 工具与命令行

| 错误码 | 什么时候出现 |
|---|---|
| `SPR-API-MEMBER` | `--member` 要找的声明的成员不存在。 |
| `SPR-API-TARGET` | `sprig api` 找不到要查看的 Sprig 模块或项目。 |
| `SPR-BUNDLE-JDEPS` | jdeps 无法分析打包的 JAR，找不到程序需要的 Java 模块。 |
| `SPR-BUNDLE-LAYOUT` | Java 安装或 classpath 的布局没法打 bundle：没有可链接的运行时、jlink 失败，或旧的 bundle 删不掉。 |
| `SPR-BUNDLE-TOOLS` | `build --bundle` 需要完整 JDK 的 jdeps 和 jlink，当前用的是 JRE。 |
| `SPR-CLI-OPTION` | 命令行缺少必需参数，或者给了未知选项。 |
| `SPR-WRAP-CHECK` | `sprig wrap` 生成的 Java 包装代码没有通过 Sprig 检查或格式化。 |

### B.3 词法与语法

| 错误码 | 什么时候出现 |
|---|---|
| `SPR-LEX-CHAR` | 输入里有 Sprig 词法不认识的字符。 |
| `SPR-LEX-INDENT-FIRST` | 文件的第一行代码必须从第 1 列开始。 |
| `SPR-LEX-INDENT-INCONSISTENT` | 减少缩进时必须退回到之前的某个缩进层级。 |
| `SPR-LEX-STRING` | 字符串没有收尾，或者含有非法转义。 |
| `SPR-LEX-TAB` | 不允许制表符；缩进只能用空格。 |
| `SPR-LEX-UNCLOSED` | `(`、`[` 或 `{` 开了没有关。 |
| `SPR-LEX-UNMATCHED` | 右括号没有对应的左括号。 |
| `SPR-SYNTAX-ERROR` | 记号序列不符合 Sprig 语法。 |

### B.4 名字、模块与导出

| 错误码 | 什么时候出现 |
|---|---|
| `SPR-MODULE-EXPORT` | `export` 的目标未知、不合法，或者和可见名字冲突。 |
| `SPR-MODULE-EXPORT-ORDER` | 顺序必须是 import、export、普通声明和语句。 |
| `SPR-NAME-DUPLICATE` | 同一个命名空间里有两个同名声明。 |
| `SPR-NAME-DUPLICATE-MEMBER` | 类或 variant 声明了重复的成员。 |
| `SPR-NAME-FIELD-SHADOW` | 参数或局部变量不能和当前类的字段同名。 |
| `SPR-NAME-FORWARD-REFERENCE` | 顶层代码使用了在它下面才运行的顶层绑定。 |
| `SPR-NAME-IMPORT` | 导入的文件或类无法解析。 |
| `SPR-NAME-IMPORT-CYCLE` | Sprig 模块互相导入形成环。 |
| `SPR-NAME-LET-ASSIGN` | `let` 绑定和 `let` 字段不能再赋值。 |
| `SPR-NAME-MODULE` | 模块导入或别名有问题。 |
| `SPR-NAME-NOT-A-TYPE` | 把值用在了需要类型的地方。 |
| `SPR-NAME-NOT-A-VALUE` | 把类型名或模块名当成了值。 |
| `SPR-NAME-UNRESOLVED` | 名字在当前作用域链里没有声明。 |

### B.5 调用与构造

| 错误码 | 什么时候出现 |
|---|---|
| `SPR-CALL-ARITY` | 实参个数不对。 |
| `SPR-CALL-DUPLICATE-FIELD` | 同一个命名字段给了两次。 |
| `SPR-CALL-MISSING-FIELD` | 必填字段没有给。 |
| `SPR-CALL-NAMED-REQUIRED` | Sprig 类和 variant 的构造必须用命名参数。 |
| `SPR-CALL-POSITIONAL-REQUIRED` | 普通函数和 JVM 调用必须用位置参数。 |
| `SPR-CALL-UNKNOWN-FIELD` | 命名参数对不上任何字段。 |

### B.6 类型与泛型

| 错误码 | 什么时候出现 |
|---|---|
| `SPR-GENERIC-CONSTRAINT` | `requires` 写了未知的能力、把契约或类当成了约束、位置不对，或者类型实参在需要 `Comparable` 的地方不满足。 |
| `SPR-TYPE-ASSIGN` | 赋的值和目标类型不匹配。 |
| `SPR-TYPE-CALLABLE-THROWS` | 函数值的 `throws` 子句用在不接受它的位置：只有 `fn(...) -> R throws Error` 这一种，不能当不带 `throws` 的类型用。 |
| `SPR-TYPE-CAPTURE` | lambda 捕获了 `var` 局部变量；先复制到 `let` 再捕获。 |
| `SPR-TYPE-CONDITION` | 条件必须是 `Bool`；Sprig 没有“非零即真”。 |
| `SPR-TYPE-FUNCTION-ARITY` | 函数类型和 lambda 只支持 0 到 3 个带类型的参数。 |
| `SPR-TYPE-GENERIC-ARGS-REQUIRED` | 泛型调用或构造需要写出 `[类型]`：实参说不清某个类型参数，或者两个实参说法矛盾。 |
| `SPR-TYPE-GENERIC-ARITY` | 泛型声明的类型实参个数不对；按声明顺序全部给出。 |
| `SPR-TYPE-GENERIC-NULLABLE` | 这个类型参数在声明里带 `?` 使用，所以它的实参必须非空。 |
| `SPR-TYPE-INFER` | 不给标注就无法推断类型。 |
| `SPR-TYPE-MISMATCH` | 期望类型和实际类型不兼容。 |
| `SPR-TYPE-NOT-CALLABLE` | 被调用的东西不可调用，或者把方法名当成了值。 |
| `SPR-TYPE-NULL` | `null` 只能赋给显式的可空类型 `T?`。 |
| `SPR-TYPE-NULLABLE` | 可能为 `null` 的值用在了必须非空的地方；先判断 `null`。 |
| `SPR-TYPE-OPERAND` | 运算符或方法不支持这个操作数类型。 |
| `SPR-TYPE-RETURN` | 返回值和声明的返回类型不匹配。 |
| `SPR-TYPE-UNIT` | `Unit` 只能作为函数或方法的结果，不能当字段、参数、集合元素或普通值。 |

### B.7 控制流与运行时

| 错误码 | 什么时候出现 |
|---|---|
| `SPR-FLOW-BREAK` | `break` 只能出现在循环里。 |
| `SPR-FLOW-CATCH-NEVER-THROWN` | catch 写了受检 Java 异常，但 try 块里没有任何东西可能抛出它。 |
| `SPR-FLOW-CONTINUE` | `continue` 只能出现在循环里。 |
| `SPR-FLOW-MISSING-RETURN` | 非 Unit 函数必须在每条路径上都返回。 |
| `SPR-FLOW-RETHROWS` | `rethrows` 需要一个函数类型声明了 `throws Error` 的参数，而且函数自己不能抛别的。 |
| `SPR-FLOW-THROWS` | 可恢复错误必须用 `throws` 声明，或者就地捕获。 |
| `SPR-FLOW-THROWS-UNUSED` | 函数声明了受检 Java 异常，但函数体永远抛不出它。 |
| `SPR-FLOW-UNREACHABLE` | 语句跟在一条必然退出的语句后面。 |
| `SPR-PROGRAM-EXIT` | Sprig 程序以非零状态退出。 |
| `SPR-RUNTIME-ERROR` | 运行时未捕获的 Sprig Error 值；消息就是 Error 的内容。 |
| `SPR-RUNTIME-EXCEPTION` | 运行时未捕获的 JVM 异常，带 Sprig 源码位置；`--stacktrace` 看完整 JVM 堆栈。 |

### B.8 数字

| 错误码 | 什么时候出现 |
|---|---|
| `SPR-NUM-CONVERSION` | 隐式数值转换可能丢精度或越界，必须用显式方法。 |
| `SPR-NUM-DIVISION` | 整数 `/` 被拒绝；有意截断用 `divTrunc`，Decimal 用 `divide`。 |
| `SPR-NUM-MIXED` | 二元运算混用了整数和浮点，或者二进制浮点和十进制。 |
| `SPR-NUM-RANGE` | 数字字面量超出目标范围，或者小到变成 0。 |

### B.9 类、契约与集合

| 错误码 | 什么时候出现 |
|---|---|
| `SPR-CLASS-ABSTRACT` | 契约类（方法没有函数体的类）声明了字段、混有带体方法、是泛型的，或者被构造了。 |
| `SPR-COLLECTION-IMMUTABLE` | List/Map 是只读的；要改先 `toMutableList()`/`toMutableMap()`。 |
| `SPR-CONFORM-EFFECTS` | 实现方法声明了 Java 接口方法不允许的受检异常。 |
| `SPR-CONFORM-MEMBER` | 类方法没有精确匹配它要见证或覆盖的 Java 方法，或者缺少必需的抽象方法。 |
| `SPR-CONFORM-OVERLOAD` | Java 接口要求重载的抽象方法，Sprig 类表示不了。 |
| `SPR-CONFORM-PARENT` | 父类视图（`as NAME`）只能调用继承来的方法；它不是值、没有字段，也不能调用抽象方法。 |
| `SPR-CONFORM-SOURCE` | `conform` 左边必须是本模块声明的、非泛型、带方法体的 Sprig 类；没有追溯 conform，契约也不能 conform。 |
| `SPR-CONFORM-TARGET` | `conform` 目标必须是导入的 public、非泛型、非 sealed 的 Java 接口；带括号时是 public、非 final、非泛型的 Java 类（内置 `Error` 也在此列）。 |

### B.10 枚举、variant 与 match

| 错误码 | 什么时候出现 |
|---|---|
| `SPR-MATCH-DUPLICATE` | match 分支重复了同一个 case。 |
| `SPR-MATCH-ENUM-BINDER` | 不带载荷的 enum case 不能写 `as name`。 |
| `SPR-MATCH-INFERENCE` | match 表达式的分支给不出非空结果类型，需要显式标注。 |
| `SPR-MATCH-NONEXHAUSTIVE` | 每个 enum/variant case 都必须有分支；没有 default。 |
| `SPR-MATCH-RESULT` | match 表达式的分支结果类型和期望（或第一个非空分支）不一致。 |
| `SPR-MATCH-SCRUTINEE` | match 只能匹配非空的 enum 或 variant 值。 |
| `SPR-MATCH-UNKNOWN-CASE` | case 名字在匹配的类型上不存在。 |
| `SPR-MATCH-WRONG-TYPE` | 分支属于另一个 enum/variant。 |

### B.11 Java 互操作

| 错误码 | 什么时候出现 |
|---|---|
| `SPR-JVM-AMBIGUOUS` | 这些实参类型下 Java 重载有歧义。 |
| `SPR-JVM-CLASS` | 导入的 Java 类加载不了，或者它位于无名包。 |
| `SPR-JVM-CLASSPATH` | `--classpath` 项不存在、为空，或者不是 JAR/目录。 |
| `SPR-JVM-COMPILE` | 生成的 Java 源码没有通过 javac；可能是编译器 bug。 |
| `SPR-JVM-INTERNAL` | 编译器或工具内部故障。 |
| `SPR-JVM-MEMBER` | 没有 Java 方法、构造器或字段匹配这次调用。 |

### B.12 项目与依赖

| 错误码 | 什么时候出现 |
|---|---|
| `SPR-DEP-CHECKSUM` | 锁定的依赖字节和记录的 SHA-256 不符；不要使用损坏的缓存。 |
| `SPR-DEP-CYCLE` | Sprig 项目依赖构成环。 |
| `SPR-DEP-GIT` | Git 依赖操作失败：缺 git、远端、引用或版本。 |
| `SPR-DEP-MAVEN` | JVM（Maven）依赖操作失败。 |
| `SPR-DEP-NOT-FOUND` | 声明的 Sprig 依赖或模块找不到。 |
| `SPR-DEP-OFFLINE` | 离线模式需要的依赖资源不在缓存里。 |
| `SPR-DEP-REGISTRY` | 包注册表读不了，或者没有列出请求的包或版本。 |
| `SPR-PROJECT-ENTRY` | 项目入口不存在，或者 `--bin` 指定的名字未知。 |
| `SPR-PROJECT-LOCK-MISSING` | 项目没有 `sprig.lock`；先运行 `sprig resolve`。 |
| `SPR-PROJECT-LOCK-SCHEMA` | 锁文件的 schema 版本不受这个编译器支持。 |
| `SPR-PROJECT-LOCK-STALE` | 编译器身份、`sprig.toml` 或某个依赖的清单和锁不一致；重新 `resolve`。 |
| `SPR-PROJECT-MANIFEST` | `sprig.toml` 缺失、格式错，或者缺少必需字段。 |
| `SPR-PROJECT-NOT-EXPORTED` | 从依赖导入的模块没有被该依赖导出。 |
| `SPR-PROJECT-UNSUPPORTED` | 项目或依赖需要这个编译器还不支持的项目特性或语言版本。 |

## 相关章节

- [第 2 章：第一个程序与读懂报错](/tutorial/ch02-first-program)：报错的四个部分和 `sprig explain`。
- [第 7 章：函数](/tutorial/ch07-functions)：`SPR-FLOW-*` 里的返回和调用问题。
- [第 12 章：枚举、variant 和 match](/tutorial/ch12-enums-variants)：`SPR-MATCH-*`。
- [第 13 章：可空值](/tutorial/ch13-nullable)：`SPR-TYPE-NULL`、`SPR-TYPE-NULLABLE`。
- [第 14 章：错误处理](/tutorial/ch14-errors)：`SPR-FLOW-THROWS`、`SPR-RUNTIME-ERROR`。
- [第 18 章：模块、项目和依赖](/tutorial/ch18-modules-projects)：`SPR-MODULE-*`、`SPR-PROJECT-*`、`SPR-DEP-*`。
- [第 21 章：调用 Java](/tutorial/ch21-java)：`SPR-JVM-*`。
