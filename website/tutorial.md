# Sprig 入门教程

这门语言面向人和编码 Agent。Sprig 不试图让编码 Agent 更聪明，而是尽量减少它需要猜测的内容。对人也一样：**Agent 友好，也应当方便审查。**教程从可运行的小程序开始，每章都引用仓库中的真实源码；文档门禁会编译、运行并核对输出。

已发布实验性 Beta SDK 为 v0.5.0-beta.1。本教程针对发行 SDK 验证；开始前请看[发行状态](/project/release-status)，并用 `sprig capabilities --json` 查询安装版本的准确能力。

## 1. 安装并运行第一段程序

安装发行页提供的 SDK 和 JDK 17 或更高版本。SDK 不包含 JDK。创建项目后，`sprig run` 会检查 Sprig、生成 Java、调用 `javac`，再启动 JVM。

```sh
sprig init hello
cd hello
sprig resolve
sprig run
```

预期输出：`Hello, Sprig!`。下面是这个程序的实际源码：

<<< @/snippets/tutorial/hello.spr

## 2. 绑定与类型

局部变量可从初始值推断类型。`let` 不能重新绑定，`var` 可以。条件必须是 `Bool`；Sprig 不会把整数或字符串当作真值。

<<< @/snippets/variables.spr

练习：添加一个不可变的 `name`，再计算一条问候语。故意把 `String` 赋给 `Int`，观察编译器给出的错误码，然后修正它。

## 3. 显式函数

每个函数参数和返回类型都要写明。调用者因此能从签名看清输入、输出与可能失败的地方。

<<< @/snippets/functions.spr

练习：添加一个接收两个 `Int` 并返回较大值的函数。为边界值写两个断言。

## 4. 用类组织数据

类字段有明确类型；构造函数使用字段名。默认值可以省略对应实参，未知或缺少必填字段会报错。

<<< @/snippets/classes.spr

练习：为 `Rectangle` 加入面积方法。确认字段类型不匹配时，编译器会在运行前拒绝程序。

## 5. 集合与小型转换

`List[T]` 是只读集合，`MutableList[T]` 用于需要修改的阶段。显式转换会创建新的外层集合。下面的词频程序展示从文本到有序结果的完整小任务。

<<< @/snippets/tutorial/word_count.spr

练习：忽略大小写，并说明标点是如何处理的。若需要完整自然语言分词，应选择专门的文本库。

## 6. 失败要写进类型

会失败的函数声明 `throws`。调用方必须捕获错误或继续声明它，失败不会悄悄变成默认值。

<<< @/snippets/errors.spr

练习：把一个缺失配置项当作 `Error` 抛出；在命令行边界捕获并打印有用信息。

## 7. 变体、match 与可空性

`variant` 表达有限的分支集合。`match` 必须覆盖每种分支，分支绑定会保留负载的静态类型。可空值要先判空收窄。

<<< @/snippets/variants.spr

<<< @/snippets/nullable.spr

练习：给表达式 AST 新增一个 `Negate` 分支，再检查每个 visitor 是否都处理了它。

## 8. 构建本地 Task Tracker

接下来我们把前面学到的类型、集合、异常处理和文件/JSON API 组合成一个本地命令行任务清单。程序只读写 UTF-8 JSON 文件，不依赖网络服务。先创建项目：

```sh
sprig init task-tracker
cd task-tracker
# 将下面的源码保存为 src/main.spr
sprig resolve
sprig run -- add "Read the Sprig tutorial"
sprig run -- add "Build a small tool"
sprig run -- list
sprig run -- done 1
sprig run -- list
```

下面是完整源码。它保存数据到当前工作目录的 `tasks.json`；设置 `SPRIG_TASKS_FILE` 可指定另一个本地文件。JSON 解码会检查字段类型，文件操作使用标准库的 UTF-8 API。

<<< @/snippets/tutorial/task_tracker.spr

该例适合学习与小型单进程数据，不提供并发写入协调。仓库中的[同一源码和独立运行验收](https://github.com/ColinHouse/Sprig/tree/main/examples/task-tracker)会检查添加、列出、完成和损坏 JSON 的行为。

## 9. 让编译器提供证据

当程序不通过检查时，先读稳定诊断，而不是猜测类型规则。这个反例故意把文本放进 `Int`：

<<< @/snippets/tutorial/type_error.spr

预期诊断为 `SPR-TYPE-ASSIGN`，期望类型为 `Int`，实际类型为 `String`。通过 `sprig check --json <file>` 可取得机器可读位置与类型信息；用 `sprig explain SPR-TYPE-ASSIGN --json` 查询修复说明。修正源程序后再次检查，不要让工具替你插入可能改变含义的转换。

```sprig
let count: Int = 3
```

Agent 的短工作流是：读取 `AGENTS.md` 和当前参考 → `sprig capabilities --json` → 编写最小修改 → `sprig check --json` / `sprig test` → 检查 diff。具体命令是否存在，以所装版本的 capability 输出为准。

## 10. 调用普通 Java API

Sprig 编译到 JVM，能显式导入受支持的 Java 类与成员。遇到不确定的签名时，先查询本机 JDK，而不是根据记忆补全重载：

```sh
sprig api java.time.LocalDate --json
```

接着看[真实互操作示例](https://github.com/ColinHouse/Sprig/blob/main/website/snippets/jvm_interop.spr)和[JVM 互操作指南](/guide/jvm-interop)。复杂框架仍由 Gradle、Maven 或 Loom 管理 classpath；Sprig 负责应用逻辑，必要时用窄 Java glue 连接当前未支持的 API 形状。

## 下一步

- [语言导览](/guide/language-tour)：按主题快速查阅语法和当前实现边界。
- [项目与依赖](/guide/projects)：本地包、Git/Maven 依赖、锁文件。
- [示例项目](https://github.com/ColinHouse/Sprig/tree/main/examples)：从 Task Tracker 继续到 JSON CLI、SQLite、Web 和 Maven。
- [Agent 工作流与诊断参考](/en/reference/tooling/agent-guide)：查询工具、稳定诊断和可复现修复。
- [功能状态与已知限制](/en/reference/language/feature-status)：了解已实现能力与明确限制。
