# 23. 工具链与 AI 助手

`sprig` 不只是一个"运行按钮"。它随时愿意回答：这个错是什么意思？这门语言有没有某个功能？这个 Java 方法在 Sprig 里怎么调？这一章学会向编译器提问，以及怎么让 AI 编程助手也养成先问编译器的习惯。

这一章你会学到：

- `check` 和 `run` 的分工，一条报错的每一部分怎么读；
- `explain`、`help`、`api`、`capabilities`、`doctor` 这些命令；
- 几乎所有命令都支持的 `--json` 输出；
- `fmt` 格式化，以及编辑器背后的 `sprig lsp`；
- `build --bundle` 把程序打成不依赖 Java 的目录；
- 和 AI 助手一起写 Sprig 的几条经验。

## 23.1 先 check，再 run

**为什么。** 运行一个程序之前，编译器已经能找出所有静态错误。改代码的循环是：改，`sprig check`，读报错，再改；只想看运行结果时才 `sprig run`。

看一个故意写错的小程序：

<<< @/snippets/book/ch23_broken.spr

```text
SPR-NUM-MIXED [TYPE] main.spr:1:7: Operator '+' has no implicit conversion between Int and Float (expected matching numeric families, actual Int and Float)
  hint: Convert the Int side: 1.toFloatExact().
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

一条报错由六部分组成，从上往下读：

- `SPR-NUM-MIXED`：**错误码**。稳定不变，可以拿去搜索或交给 `sprig explain`。
- `[TYPE]`：**类别**，说明问题出在编译的哪一步。常见的有 `SYNTAX`（语法）、`NAME`（名字）、`TYPE`（类型）、`FLOW`（控制流）、`RUNTIME`（运行时）、`JVM`（Java 互操作）。
- `main.spr:1:7`：**文件:行:列**。列从 1 开始数，1:7 就是第一行第 7 个字符，`+` 的位置。
- 一句话说明，后面括号里是 `expected`（期望什么）和 `actual`（实际得到什么）。
- `hint:`：一条可以直接照做的修法。
- 末尾的 `1 error(s)` 是汇总；`check` 会一次列出所有错误，不是遇到第一个就停。上面这个文件如果写两个错，两段报错都会打印出来。

## 23.2 看不懂就 explain

**为什么。** 错误码背后有完整的解释：为什么有这条规则、常见写法、安全的修法、正反例。`sprig explain 错误码` 打出来：

```text
$ sprig explain SPR-FLOW-THROWS
SPR-FLOW-THROWS: A recoverable error must be declared with throws or caught.
Why it matters: Checked errors are visible at every call site; nothing fails silently.
Common causes:
  - A call may throw Error or a checked Java exception that this function neither catches nor declares.
Safe fixes:
  - Wrap the call in try/catch, or add throws to the function signature and propagate.
Good:
  func load(path: String) -> String throws Error, IOException:
      return files.read_utf8(path)
Bad:
  func load(path: String) -> String:
      return files.read_utf8(path)
```

`sprig codes` 列出全部错误码，一行一个，带一句话说明。`explain --json` 给同样的内容，只是结构化。

## 23.3 忘了语法就 help

**为什么。** 语言手册就在编译器里，而且是和编译器一起测试的。`sprig help` 不带参数列出所有命令和主题（`language`、`types`、`strings`、`functions`、`classes`、`variants`、`match`、`nullability`、`errors`、`collections`、`numerics`、`modules`、`jvm`、`conform`、`generics`、`concurrency`、`projects`、`dependencies`、`agents`、`api`、`upgrade`、`fmt`、`testing`、`build`、`wrap`、`lsp`）。

随便挑一个主题：

```text
$ sprig help fmt
Sprig fmt (language 0.8-dev)
Syntax:
  sprig fmt file.spr
  sprig fmt .
  sprig fmt --check file.spr --json
Rules:
  - Canonical deterministic comment-preserving formatting
  - four spaces per block
  - no configuration
  - no aggressive wrapping
  - explicit command only
  - parse failures preserve original files
Example (Sprig source repository): docs/tooling/formatter.md
```

每个主题都有语法、规则和一个来自仓库、真实编译过的例子。加上 `--json` 就是给程序读的版本。

## 23.4 不确定 Java 怎么调就 api

**为什么。** Java 方法的签名一个 `?` 能改变你的写法。`sprig api 类名 --member 方法名` 直接告诉你 Sprig 看到的样子：

```text
$ sprig api java.time.LocalDate --member parse
Java API: java.time.LocalDate
constructors:
staticMethods:
  public static java.time.LocalDate java.time.LocalDate.parse(java.lang.CharSequence) => parse(CharSequence) -> LocalDate?
  public static java.time.LocalDate java.time.LocalDate.parse(java.lang.CharSequence,java.time.format.DateTimeFormatter) => parse(CharSequence, DateTimeFormatter) -> LocalDate?
instanceMethods:
fields:
```

箭头左边是 Java 的声明，右边是 Sprig 的签名；`LocalDate?` 末尾的问号告诉你结果要判空（第 21 章）。

Sprig 自己的模块也能查：

```text
$ sprig api @std/lists.spr --member sort_by
Sprig module: @std/lists.spr
path: .../std/lists.spr
declarations:
  function sort_by
    # Stable: items with equal keys keep their input order. Keys use the same
    # order as MutableList.sort(), so Float keys put -0.0 before 0.0 and NaN last.
    sort_by(items: List[T], key: fn(T) -> K throws Error): List[T]
```

`api` 只读签名，从不执行程序。

## 23.5 一切都有 JSON

**为什么。** 人读文字，编辑器、脚本和 AI 助手读结构化输出。`sprig check --json` 对 23.1 那个错误给出（在一个临时目录里把文件叫 `main.spr`）：

```json
{
  "schemaVersion": 1,
  "toolVersion": "sprig-compiler 0.7.1-beta.1",
  "command": "check",
  "exitCode": 1,
  "environment": {"classpath": []},
  "diagnostics": [
    {
      "code": "SPR-NUM-MIXED",
      "phase": "TYPE",
      "severity": "error",
      "uri": "file:///private/tmp/sprig-probe/ch23/main.spr",
      "range": {"start": {"line": 0, "character": 6}, "end": {"line": 0, "character": 13}},
      "message": "Operator '+' has no implicit conversion between Int and Float",
      "expectedType": "matching numeric families",
      "actualType": "Int and Float",
      "hint": "Convert the Int side: 1.toFloatExact().",
      "relatedHelp": "numerics",
      "repair": {"kind":"make-numeric-conversion-explicit","machineApplicable":false},
      "related": [],
      "suggestedEdits": []
    }
  ]
}
```

文字里有的，JSON 里都有，而且更精确：`range` 的字符位置是给编辑器画波浪线用的（注意 JSON 里的 `line` 和 `character` 都从 0 数起，而 `sprig check` 打印的行列从 1 数起，所以同一个位置是 `1:7` 和 `"line": 0, "character": 6`），`relatedHelp` 给出可以继续运行 `sprig help numerics` 的主题，`repair.kind` 是机器可读的修法分类。

第二个常用命令是 `sprig capabilities`，它把"这门语言有什么、没有什么"一次说清楚：

```text
$ sprig capabilities
Sprig compiler 0.7.1-beta.1 / language 0.8-dev
JDK minimum: 21
Commands: help, version, check, build, run, test, codes, explain, capabilities, api, wrap, doctor, init, resolve, add, remove, search, publish, project, deps, upgrade, fmt, lsp
Types: Int, Int32, BigInt, Float, Float32, Decimal, Bool, String, Unit
Implemented: typed functions, one-line class declarations, contract classes (methods without bodies) with conform, function references (named, module and method functions, and print, as values), source function types fn(A) -> R, function types that throw Error, rethrows functions, classes, enums, variants, match statements, match expressions, if expressions, explicit declaration reexports, nullable types, typed catch, local modules, lambdas up to three parameters, generic blocks with one or more type parameters, explicit Type[Arg] generic uses, type arguments inferred from call arguments, Equatable capability, Comparable capability, Sprig function values passed as Java functional interfaces with up to three parameters, Java varargs calls, declared foreign JVM conformance (conform Class to ImportedInterface), Java class conformance with a parent view, error classes (conform C to Error(message))
Unsupported: inference from the expected type, variance, inheritance, Java functional interfaces with more than three parameters, Java type-variable varargs (T...), varargs declarations in Sprig, annotations, decorators, macros, reflection-based schemas, block lambdas, tuples, destructuring, string interpolation, Char type, async/await, wildcard match, pipeline, operator overloading, arrays
Use 'sprig help <topic>' for syntax and rules.
```

`capabilities --json` 还给每个"没有的功能"配一段替代方案。比如问"有元组吗"（`tuples` 节选）：

```json
"tuples": {
  "supported": false,
  "alternatives": [
    "a one-line class with named fields: class Pair(first: Int, second: Int), built as Pair(first=1, second=2)",
    "variants with named fields"
  ],
  "helpTopic": "language"
}
```

另外两个查环境的命令：

```text
$ sprig doctor
schemaVersion: 1
compilerVersion: 0.7.1-beta.1
languageVersion: 0.8-dev
jdkMinimum: 21
javaVersion: 26.0.1
javaVendor: Oracle Corporation
javacAvailable: true
platform: Mac OS X aarch64
cacheRoot: /Users/wu/.sprig
...
```

`doctor` 告诉你用的 JDK 能不能编译、jlink 工具在不在（打包要用）、缓存目录在哪。项目里还有 `sprig project --json` 和 `sprig deps --json`：

```text
$ sprig project --json
{"schemaVersion":1,"command":"project","exitCode":0,"project":{"schemaVersion":1,"root":".../demo","name":"demo","version":"0.1.0","language":"0.8","source":"src","entry":"src/main.spr","binaries":[],"exports":[],"manifest":".../demo/sprig.toml","lockfile":".../demo/sprig.lock","lockStatus":"current","sprigDependencies":[],"jvmDependencies":[],"dependencyResolution":"not-needed","cacheRoot":"/Users/wu/.sprig","gitAvailable":true}}
```

## 23.6 格式化：fmt

**为什么。** 格式统一以后，diff 里只剩真正的改动。`sprig fmt` 有唯一的规范格式，没有配置项。

```sprig
func   add( a :Int,b:Int )->Int:
      return a+b
print(add(1,  2))
```

```text
$ sprig fmt --check messy.spr
Would format /private/tmp/sprig-probe/fmt/messy.spr
$ sprig fmt messy.spr
Formatted /private/tmp/sprig-probe/fmt/messy.spr
```

结果：

```sprig
func add(a: Int, b: Int) -> Int:
    return a + b
print(add(1, 2))
```

唯一没被重写的"格式"是注释里的文字；`--check` 只检查不修改，`--json` 会列出哪些文件需要改（`changedFiles`）。它可以对整个目录运行：`sprig fmt .`。

## 23.7 编辑器：sprig lsp

编辑器里的诊断、悬停提示、跳转定义、补全、重命名，全部来自同一个编译器，通过 `sprig lsp` 语言服务器提供。`sprig help lsp` 的规则列表就是它的功能清单：

```text
$ sprig help lsp
Sprig lsp (language 0.8-dev)
Syntax:
  sprig lsp
  sprig lsp --stdio
  sprig lsp --classpath build/classes/java/main
Rules:
  - Language Server Protocol over standard input and output, for any LSP client
  - diagnostics, hover, go to definition, references, document symbols, completion, formatting, rename and quick fixes come from the same parser, resolver and checker as sprig check
  - a file inside a sprig.toml project uses that project's locked dependencies and classpath, and nothing is resolved or downloaded
  - unsaved buffers of open files are read instead of the files on disk
  - when the current text does not parse or resolve, requests return nothing rather than a guess, and the outline keeps the last version that parsed
  - rename covers local variables and parameters only and is checked by compiling the renamed text
  - a quick fix is a code action that applies one of the suggestedEdits of a diagnostic under the cursor, computed from the current text, never from diagnostics the client sends
  - references search the open files and the files of the same project that mention the name
  - positions use UTF-16 code units, as LSP requires
Example (Sprig source repository): docs/tooling/lsp.md
```

VS Code 用户装仓库自带的 Sprig 插件就有这些功能。因为命令行和编辑器用同一个编译器，编辑器里看到的报错和 `sprig check` 完全一致。

## 23.8 打包：build --bundle

**为什么。** `sprig run` 每次启动都要先编译，而且要求对方机器上有 Java。交付给别人的程序用 `sprig build --bundle`：它把程序、Sprig 运行时、锁定的依赖和一个裁剪过的 Java 运行时（由 `jlink` 生成）放进一个目录。

先写一个 `hello.spr`，然后：

```text
$ sprig build hello.spr --bundle --archive
Built hello.spr -> /private/tmp/sprig-probe/bundle/sprig-build
  Java sources: /private/tmp/sprig-probe/bundle/sprig-build/java
  Classes:      /private/tmp/sprig-probe/bundle/sprig-build/classes
  Main class:   sprig.user.$M_hello
  Bundle:       /private/tmp/sprig-probe/bundle/sprig-build/hello
    run it with /private/tmp/sprig-probe/bundle/sprig-build/hello/bin/hello (or hello.cmd on Windows)
    lib/ 87 KB; runtime/ 63 MB, modules java.base, java.net.http, java.sql, jdk.httpserver
    the bundle runs only on Mac OS X aarch64, the platform that built it
  Archive:      /private/tmp/sprig-probe/bundle/sprig-build/hello.zip
```

输出目录长这样：

```text
sprig-build/hello/
  README.txt
  bin/hello           Linux、macOS 的启动脚本
  bin/hello.cmd       Windows 的启动脚本
  lib/hello.jar       程序自己的类
  lib/sprig-runtime.jar
  runtime/            jlink 生成的 Java 运行时
```

启动脚本从**你当前所在的目录**运行程序，原样转发参数，退出码就是程序的退出码：

```text
$ ./sprig-build/hello/bin/hello
hello from a bundle
```

几条要知道的限制：

- 程序先被检查、编译；有错误就什么都不写，打包过程也不会运行程序。
- 运行时镜像**只能**在生成它的操作系统和 CPU 架构上跑（Linux x86-64 上打的包不能拿到 macOS 或 ARM Linux 上用）；要给每个平台各打一次。
- 打包需要完整的 JDK（有 `jdeps`、`jlink` 和 `jmods/`），`sprig doctor` 能提前告诉你缺不缺；缺了会报 `SPR-BUNDLE-TOOLS` 并说明修法。
- `--archive` 再打一个 zip 便于分发（上面的例子是 29 MB）；不加时只有目录。
- 不做跨平台打包、GraalVM 原生镜像和安装包。

只想看生成的 Java 代码时用 `sprig build app.spr --emit-java-only`：它跑完静态检查就写 Java，不调用 `javac`。

## 23.9 和 AI 助手一起写

Sprig 从设计之初就把 AI 编程助手当成主要用户之一：报错有稳定的错误码和精确的位置，一切命令都有 JSON。编译器的 `agents` 主题就是给助手看的：

```text
$ sprig help agents
Sprig agents (language 0.8-dev)
Syntax:
  sprig build file.spr --emit-java-only -d generated --json
  sprig capabilities --json
  sprig help match --json
  sprig api java.time.LocalDate --member of --json
  sprig api src/main.spr --json
  sprig api @pkg/module.spr --member Type.member --json
  sprig api . --json
  sprig check file.spr --json
  sprig explain SPR-CODE --json
  sprig upgrade --check
Rules:
  - Query current capabilities before generating syntax
  - use diagnostics to repair code
  - do not guess unsupported features
  - api inspects Java classes and checked Sprig modules/projects with resolved signatures and never executes application code
Example (Sprig source repository): docs/tooling/agent-guide.md
```

几条实用经验：

- **让助手先问编译器。** 开始写之前运行 `sprig capabilities --json` 和相关的 `sprig help 主题 --json`，它就不会把别的语言的语法带进来（比如写 `async` 或者元组）。
- **用 `check --json` 的输出修错。** 错误码、`range`、`hint` 足够定位和修改；看不懂的码交给 `explain --json`。
- **写测试，包括 compile_fail 测试。** 第 19 章讲过怎么让"编译器应该拒绝这种写法"也成为一个断言；本书每个"故意写错"都是这样被验证的。
- **注意升级命令。** `sprig upgrade --check` 查询有没有新版本；本书用的源码 checkout 会直接告诉你它不是托管安装：

```text
$ sprig upgrade --check
sprig upgrade: this is a source checkout; update it with Git and rebuild with scripts/build.sh
```

## 本章小结

- 循环是 `check`（列全部错误）→ 读报错 → 改；`run` 只在看结果时用。
- 报错格式：错误码 `[类别] 文件:行:列` 说明 `(expected, actual)` + `hint`。
- `explain` 查错误码，`help` 查语言，`api` 查签名，`capabilities` 查"有什么、没有什么"。
- 几乎所有命令都有 `--json`；编辑器、脚本和 AI 助手读结构化输出。
- `fmt` 统一格式（`--check` 只检查）；`sprig lsp` 给编辑器提供和命令行一致的诊断。
- `build --bundle` 打出自带 Java 运行时的目录，平台要分别打；`--emit-java-only` 只生成 Java。

## 动手练习

**练习 1（简单）。** 用 `sprig explain` 查第 22 章见过的 `SPR-TYPE-CAPTURE`，写出它建议的修法。提示：命令是 `sprig explain SPR-TYPE-CAPTURE`。

::: details 参考答案
```text
$ sprig explain SPR-TYPE-CAPTURE
SPR-TYPE-CAPTURE: A lambda captures a var local; copy it into a let binding first.
Why it matters: Lambdas capture immutable bindings, avoiding shared mutable state surprises.
Common causes:
  - A lambda body reads a var local.
Safe fixes:
  - Copy the var into a let binding before the lambda.
```

修法就是答案里最后一行的意思：把 `var` 先复制进一个 `let`，lambda 捕获那个 `let`。
:::

**练习 2（简单）。** 用 `sprig api` 查 `java.lang.String.strip()` 在 Sprig 里返回什么。它带问号吗？带问号意味着什么？

::: details 参考答案
```text
$ sprig api java.lang.String --member strip
Java API: java.lang.String
constructors:
staticMethods:
instanceMethods:
  public java.lang.String java.lang.String.strip() => strip() -> String?
fields:
```

返回 `String?`，带问号：Java 方法返回的引用一律当作可能为 `null`，调用之后要先判断再用（第 21 章）。
:::

**练习 3（中等）。** 用 `sprig capabilities --json` 回答：这个编译器支持元组（tuples）吗？如果不支持，它建议用什么替代？提示：在 JSON 里找 `unsupportedSyntax` 和 `featureGuidance`。

::: details 参考答案
`tuples` 出现在 `unsupportedSyntax` 里，也就是不支持。`featureGuidance` 给的两个替代是：

```json
"tuples": {
  "supported": false,
  "alternatives": [
    "a one-line class with named fields: class Pair(first: Int, second: Int), built as Pair(first=1, second=2)",
    "variants with named fields"
  ],
  "helpTopic": "language"
}
```

也就是用一行类或带字段的 variant 代替（第 11、12 章）。
:::

**练习 4（稍难）。** 给一个只 `print("hi")` 的程序打包并运行它，不经过 `sprig run`。提示：`sprig build hi.spr --bundle`，然后用输出目录里的 `bin` 脚本。

::: details 参考答案
```text
$ sprig build hi.spr --bundle
...
  Bundle:       .../sprig-build/hi
    run it with .../sprig-build/hi/bin/hi (or hi.cmd on Windows)
    ...
$ ./sprig-build/hi/bin/hi
hi
```

启动脚本在当前目录运行程序，不需要 PATH 上有 Java。注意只能在本机平台运行，换平台要重新打。
:::

下一章：[综合项目：记账](/tutorial/ch24-project-ledger)——把全书的内容合成一个完整程序。
