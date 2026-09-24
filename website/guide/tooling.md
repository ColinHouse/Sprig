# 工具与 JSON

stage-0 编译器只提供一个可执行文件 `bin/sprig`，由 `scripts/build.sh` 生成。
没有守护进程、语言服务器或 IDE 集成。

## 命令

```text
check <file.spr> [--json] [--syntax-only]   parse and type-check
run   <file.spr> [--json] [--keep] [-- a b] compile and execute on the JVM
build <file.spr> [-d dir] [--json]          emit Java sources + .class files
explain <SPR-CODE>                          explain a diagnostic code
codes [--json]                              list every diagnostic code
version
```

- `check` 在代码生成之前停止；`--syntax-only` 更早，只做词法、缩进与解析。
- `run` 支持在 `--` 之后传递程序参数，`--keep` 用于保留生成的中间文件。
- `build` 把生成的 Java 与 `.class` 写入 `-d`（默认 `build/out`）；检查失败时不会
  留下 class 文件。
- `explain` 与 `codes` 对应[诊断码（英文）](/en/reference/DIAGNOSTIC_CODES)。

## JSON 结果

加 `--json` 后，stdout 恰好包含一个 JSON 文档，即使程序本身运行失败也是如此。
`programOutput` 保存程序输出，`diagnostics` 保存结构化错误。

成功运行：

```json
{
  "schemaVersion": 1,
  "toolVersion": "sprig-compiler 0.1.0-alpha.1",
  "command": "run",
  "exitCode": 0,
  "programOutput": "Hello, Ada!\n",
  "diagnostics": []
}
```

失败的检查（此处缩短了路径；实际 `uri` 是 `file:` URI）：

```json
{
  "schemaVersion": 1,
  "toolVersion": "sprig-compiler 0.1.0-alpha.1",
  "command": "check",
  "exitCode": 1,
  "diagnostics": [
    {
      "code": "SPR-MATCH-NONEXHAUSTIVE",
      "phase": "FLOW",
      "severity": "error",
      "uri": "file:///project/tests/semantics/missing_case.spr",
      "range": {
        "start": { "line": 4, "character": 4 },
        "end": { "line": 6, "character": 29 }
      },
      "message": "Missing case: Expr.Add",
      "hint": "Add 'case Expr.Add:' (there is no default case)",
      "related": [],
      "suggestedEdits": []
    }
  ]
}
```

位置从 0 开始计数。退出码区分结果：`0` 成功，`1` 源码或程序失败，`2` 内部/IO
失败。退出码 `2` 且带 `SPR-JVM-INTERNAL` 表示工具自身的问题，而不是源码的问题。

## 尚未提供的工具

以下能力都是**提案，尚未实现**：

- `sprig api`（供 agent 查询 JDK/JVM 签名）、
- LSP / IDE 语言服务器、
- `sprig fmt`、`sprig test`、
- 包清单、Maven 依赖解析或模块仓库、
- 增量检查。

这些接口的草案保存在
[Agent 工具协议（英文，提案）](/en/reference/AGENT_TOOL_PROTOCOL)，请把它当作设计文档，
而不是可用命令。

## 面向 agent 的工作流

- 在 `run` 之前，`sprig check --json` 是最便宜的可靠门槛：解析、解析名称、类型检查，
  不生成也不执行代码。
- 诊断带有稳定码、阶段（`LEX`、`SYNTAX`、`NAME`、`TYPE`、`FLOW`、`JVM`、`RUNTIME`）、
  范围，并且常常带有指出下一步修复的 `hint`。
- `run --json` 把程序输出与诊断分开，程序失败时仍能得到可解析的结果。
- 仓库的质量门槛是有意设计的，见
  [AI 辅助开发声明（英文）](/en/project/ai-disclosure)。
