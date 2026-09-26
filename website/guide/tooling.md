# 工具与 JSON

stage-0 编译器只提供一个可执行文件 `bin/sprig`，由 `scripts/build.sh` 生成。
没有守护进程、语言服务器或 IDE 集成。

## 命令

```text
check <file.spr> [--json] [--syntax-only]   parse and type-check
run   <file.spr> [--json] [--keep] [-- a b] compile and execute on the JVM
build <file.spr> [-d dir] [--json]          emit Java sources + .class files
help [topic] [--json]                      带版本的语言参考
capabilities [--json]                     已实现能力清单
api <Java.Class> [--member NAME] [--classpath JAR] [--json] 查询 JVM 签名
doctor [--classpath JAR] [--json]         环境检查
explain <SPR-CODE> [--json]                 结构化诊断说明
codes [--json]                              list every diagnostic code
version
```

`check`、`build`、`run`、`api` 可重复使用 `--classpath` 指定本地 JAR 或目录；
四个命令采用同一解析路径，不自动下载依赖。这些新增命令属于 alpha.2 开发版本；
目前公开发行包仍是 alpha.1。
例如用 `sprig api java.time.LocalDate --json` 查询实际 JDK 签名。

- `check` 在代码生成之前停止；`--syntax-only` 更早，只做词法、缩进与解析。
- `run` 支持在 `--` 之后传递程序参数，`--keep` 用于保留生成的中间文件。
- `build` 把生成的 Java 与 `.class` 写入 `-d`（默认 `sprig-build`）；检查失败时不会
  留下 class 文件。
- `explain` 与 `codes` 对应[诊断码（英文）](/en/reference/DIAGNOSTIC_CODES)。

## JSON 结果

加 `--json` 后，stdout 恰好包含一个 JSON 文档，即使程序本身运行失败也是如此。
`programOutput` 保存程序输出，`diagnostics` 保存结构化错误。

成功运行：

```json
{
  "schemaVersion": 1,
  "toolVersion": "sprig-compiler 0.2.0-alpha.1",
  "command": "run",
  "exitCode": 0,
  "programOutput": "Hello, Ada!\n",
  "environment": {"classpath": []},
  "diagnostics": []
}
```

失败的检查（此处缩短了路径；实际 `uri` 是 `file:` URI）：

```json
{
  "schemaVersion": 1,
  "toolVersion": "sprig-compiler 0.2.0-alpha.1",
  "command": "check",
  "exitCode": 1,
  "environment": {"classpath": []},
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

位置从 0 开始计数。CLI 参数与工具错误使用退出码 `2`，源码和运行时失败通常使用
`1`。`run` 会原样转发程序进程状态，因此程序显式退出也可能返回 `2` 或其他值；
非零状态且没有 JVM 异常诊断时会报告 `SPR-PROGRAM-EXIT`，并在 JSON 的
`data.programExitCode` 中记录程序状态。诊断 code 用于区分工具失败与程序退出。

## 尚未提供的工具

以下能力都是**提案，尚未实现**：

- LSP / IDE 语言服务器、
- `sprig fmt`、`sprig test`、
- 包清单、Maven 依赖解析或模块仓库、
- 增量检查。

历史[Agent 工具协议（英文，提案）](/en/reference/AGENT_TOOL_PROTOCOL)
还包含未来接口。当前能力以 `sprig capabilities --json` 为准；`api` 边界见
[JVM 互操作（英文）](/en/reference/JVM_INTEROP)。

## 面向 agent 的工作流

- 在 `run` 之前，`sprig check --json` 是最便宜的可靠门槛：解析、解析名称、类型检查，
  不生成也不执行代码。
- 诊断带有稳定码、阶段（`LEX`、`SYNTAX`、`NAME`、`TYPE`、`FLOW`、`JVM`、`RUNTIME`）、
  范围，并且常常带有指出下一步修复的 `hint`。
- `run --json` 把程序输出与诊断分开，程序失败时仍能得到可解析的结果。
- 仓库的质量门槛是有意设计的，见
  [AI 辅助开发声明（英文）](/en/project/ai-disclosure)。
