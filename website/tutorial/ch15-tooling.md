# 15. 工具链与 AI 助手

Sprig 只有一个命令行程序 `sprig`。这一章讲怎么让它替你回答问题，而不是去猜；以及为什么这些命令对 AI 编程助手同样好用。

## 15.1 先 check，再 run

`sprig check` 一次列出所有错误，不生成任何东西。改代码时的循环是：改，`check`，读报错，改。只在想看运行结果时才 `run`。

报错的格式固定：

```text
错误码 [类别] 文件:行:列: 一句话说明 (expected ..., actual ...)
  hint: 怎么改
```

类别有 `SYNTAX`、`NAME`、`TYPE`、`FLOW`、`RUNTIME` 等，告诉你问题出在编译的哪一步。

## 15.2 看不懂就 explain

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

每个错误码都有这样一段：为什么有这条规则、常见的写错方式、安全的修法、正反例。`sprig codes` 列出全部错误码。

## 15.3 忘了语法就 help

```text
$ sprig help nullability
Sprig nullability (language 0.8-dev)
Syntax:
  let value: String? = null
  if value != null:
      print(value)
  ...
Rules:
  - Only T? admits null
  - check before dereference
  - Java reference results are conservatively nullable, so each Java call result needs its own check, except toString(), which is a String
  - an immutable binding narrows inside if x != null, on the right side of and, in each elif and the else by every earlier condition ...
```

`sprig help` 不带参数列出所有主题：`numerics`、`nullability`、`errors`、`match`、`generics`、`collections`、`strings`、`functions`、`classes`、`jvm`、`dependencies`、`concurrency` 等。每个主题的例子都是仓库里真实编译过的文件。

## 15.4 不确定 Java 怎么调就 api

第 13 章用过：`sprig api java.time.LocalDate --member parse` 给出方法在 Sprig 里的签名，包括返回值的问号。对 Sprig 模块也一样：`sprig api @std/lists.spr` 列出每个声明和它上方的注释。

## 15.5 一切都有 JSON

几乎每条命令都接受 `--json`。`sprig check --json` 对第 8 章那个故意写错的例子给出：

```json
{
  "schemaVersion": 1,
  "command": "check",
  "exitCode": 1,
  "diagnostics": [
    {
      "code": "SPR-NUM-MIXED",
      "phase": "TYPE",
      "severity": "error",
      "uri": "file:///.../main.spr",
      "range": {"start": {"line": 1, "character": 6}, "end": {"line": 1, "character": 15}},
      "message": "Operator '+' has no implicit conversion between Int? and Int",
      "expectedType": "matching numeric families",
      "actualType": "Int? and Int",
      "hint": "count may be null (Int?): check it first with 'if count != null:', ...",
      "relatedHelp": "numerics",
      "repair": {"kind": "make-numeric-conversion-explicit", "machineApplicable": false}
    }
  ]
}
```

错误码、精确到列的范围、期望和实际类型、修改建议、相关的 help 主题，全是字段。编辑器、脚本和 AI 助手不用从一段文字里猜问题在哪。

另外几个常用的：

- `sprig capabilities --json`：这个编译器实现了哪些功能、**没有**哪些功能（`unsupportedSyntax` 里明确列着 `inheritance`、`string interpolation`、`tuples` 等）以及每个没有的功能的替代写法（`featureGuidance`）。
- `sprig doctor --json`：JDK、编译器、缓存目录的状态。
- `sprig project --json`、`sprig deps --json`：项目和依赖信息。

## 15.6 格式化和编辑器

`sprig fmt 文件或目录` 把代码整理成统一格式，保留注释，没有配置项；`--check` 只检查不修改。

[VS Code 插件](/guide/editor)提供高亮、保存时检查和一键运行，背后是 `sprig lsp` 语言服务器。诊断、悬停、跳转到定义都来自同一个编译器，和命令行看到的完全一致。

## 15.7 和 AI 助手一起写

Sprig 从设计之初就考虑了 AI 编程助手作为主要用户之一。几条经验：

- **让助手先问编译器。** 开始写代码前运行 `sprig capabilities --json` 和相关的 `sprig help 主题 --json`，它就知道这门语言有什么、没有什么、该怎么写，不会把别的语言的习惯带进来。
- **用 check --json 的输出修错。** 错误码加 `hint` 加 `range`，足够定位和修改。看不懂的码交给 `explain --json`。
- **写测试，包括 compile_fail 测试。** 第 12 章讲的 `tests/compile_fail/` 让"编译器应该拒绝这种写法"也成为可以验证的断言。本书每个"故意写错"的例子都是这样被验证的。
- **报错是给人读的，也是给机器读的。** 同一个诊断有文字形式和 JSON 形式，内容一致。

更多见[和 AI 助手一起写代码](/guide/agent-workflow)和[工具与 JSON](/guide/tooling)。

## 小结

- `check` 看错误，`explain` 看错误码，`help` 看语法，`api` 看签名。
- 全部支持 `--json`，结构里有错误码、位置、类型、修法。
- `capabilities` 告诉你语言有什么和没有什么。

最后一章把全书的内容合成一个程序：[项目：记账小工具](/tutorial/ch16-project-ledger)。
