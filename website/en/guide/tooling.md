# Tooling and JSON

The stage-0 compiler ships one executable, `bin/sprig`, built by
`scripts/build.sh`. It has no daemon, language server or IDE integration.

## Commands

```text
check <file.spr> [--json] [--syntax-only]   parse and type-check
run   <file.spr> [--json] [--keep] [-- a b] compile and execute on the JVM
build <file.spr> [-d dir] [--json]          emit Java sources + .class files
explain <SPR-CODE>                          explain a diagnostic code
codes [--json]                              list every diagnostic code
version
```

- `check` stops before code generation. `--syntax-only` stops even earlier,
  after lexing, layout and parsing.
- `run` accepts program arguments after `--` and `--keep` for inspecting
  generated files.
- `build` writes generated Java and `.class` files to `-d` (default
  `build/out`). A failed check produces no class files.
- `explain` and `codes` document the stable diagnostic vocabulary in
  [Diagnostic codes](/en/reference/DIAGNOSTIC_CODES).

## JSON results

With `--json`, stdout contains exactly one JSON document, including when the
program itself fails. `programOutput` carries what the program printed, and
`diagnostics` carries structured errors.

A successful run:

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

A failed check (path shortened here; the real `uri` is a `file:` URI):

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

Positions are zero-based. Exit codes distinguish outcomes: `0` success, `1`
source or program failure, `2` internal/IO failure. Exit code `2` with
`SPR-JVM-INTERNAL` means a tooling problem, not a problem in your source file.

## What the tooling does not do yet

The following are **proposed, not implemented**:

- `sprig api` (querying JDK/JVM signatures for agents),
- an LSP / IDE language server,
- `sprig fmt`, `sprig test`,
- package manifests, Maven dependency resolution or a module registry,
- incremental checking.

The proposal for these interfaces is kept as
[Agent tool protocol](/en/reference/AGENT_TOOL_PROTOCOL); treat it as a design
document, not as a description of available commands.

## For agent-assisted workflows

- `sprig check --json` is the cheapest reliable gate before `run`: parse,
  resolve and type-check without generating or executing code.
- Diagnostics carry stable codes, a phase (`LEX`, `SYNTAX`, `NAME`, `TYPE`,
  `FLOW`, `JVM`, `RUNTIME`), a range and often a `hint` naming the next fix.
- `run --json` keeps program output separate from diagnostics, so a failing
  program still yields a parseable result.
- The repository's own quality gates are deliberate: see the
  [AI-assisted development disclosure](/en/project/ai-disclosure).
