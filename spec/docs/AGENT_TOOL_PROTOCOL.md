# Agent tool protocol (v0.7 design proposal)

> Historical Sprig v0.7 design kit. Current implementation: [feature status](../../docs/FEATURE_STATUS_IMPLEMENTED.md).

The v0.2.0-alpha.1 compiler implements a subset of this proposal:
`help`, `capabilities`, `api`, `doctor`, and structured `explain`. This file is
the historical design target; current command behavior is described by
`sprig help --json` and `docs/FEATURE_STATUS_IMPLEMENTED.md`. LSP and package
management remain proposals.

CLI surface (proposed):

```sh
sprig check src/main.spr --json
sprig api java.time.LocalDate --json
sprig test --json
sprig fmt src/main.spr
```

Checks should share the compiler frontend with the future IDE language server; no duplicate semantic engine for agents. JSON diagnostics MUST report schema version, stable error code, phase (`LEX`, `SYNTAX`, `NAME`, `TYPE`, `FLOW`, `JVM`, `RUNTIME`), URI, zero-based start/end positions, message, related symbol spans, actual/expected type and *optional* suggested edits. Never invent a fix based only on vague parse recovery. Each API query must identify the project/JDK/dependency version and overload signatures from pinned bytecode metadata. Indexing should not execute third-party static initializers.

Example *target* diagnostic payload (not actual tool output):

```json
{
  "schemaVersion": 1,
  "toolVersion": "future",
  "diagnostics": [
    {
      "code": "SPR-MATCH-NONEXHAUSTIVE",
      "phase": "TYPE",
      "uri": "file:///project/src/main.spr",
      "range": {"start": {"line": 3, "character": 4}, "end": {"line": 3, "character": 14}},
      "message": "Missing case: Expr.Add",
      "related": [],
      "suggestedEdits": []
    }
  ]
}
```

All suggestions are optional and must preserve programmer intent. The Agent must report the difference between parser acceptance, static checking, `javac` success, and behavior tests. Benchmark new code generation versus the v0.6 design with identical tasks, time/tool/token budgets; do not assert improvement from syntax design alone.
