# Tooling and JSON

The stage-0 compiler ships one executable, `bin/sprig`, built by
`scripts/build.sh`. It has no daemon, language server or IDE integration.

## Commands

```text
check <file.spr> [--json] [--syntax-only]   parse and type-check
run   <file.spr> [--json] [--keep] [-- a b] compile and execute on the JVM
build <file.spr> [-d dir] [--json]          emit Java sources + .class files
help [topic] [--json]                      versioned language reference
capabilities [--json]                     implemented feature inventory
api <Java.Class> [--member NAME] [--classpath JAR] [--json] JVM signatures
doctor [--classpath JAR] [--json]         environment report
explain <SPR-CODE> [--json]                 structured diagnostic explanation
codes [--json]                              list every diagnostic code
version
```

`check`, `build`, `run`, and `api` accept repeated `--classpath` values for
local JARs/directories. They use the same resolved path. No dependency is
downloaded. The latest published archive is alpha.1; these additional
commands are in the alpha.2 development tree.
Use `sprig api java.time.LocalDate --json` to inspect real JDK signatures.

- `check` stops before code generation. `--syntax-only` stops even earlier,
  after lexing, layout and parsing.
- `run` accepts program arguments after `--` and `--keep` for inspecting
  generated files.
- `build` writes generated Java and `.class` files to `-d` (default
  `sprig-build`). A failed check produces no class files.
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
  "toolVersion": "sprig-compiler 0.1.0-alpha.2",
  "command": "run",
  "exitCode": 0,
  "programOutput": "Hello, Ada!\n",
  "environment": {"classpath": []},
  "diagnostics": []
}
```

A failed check (path shortened here; the real `uri` is a `file:` URI):

```json
{
  "schemaVersion": 1,
  "toolVersion": "sprig-compiler 0.1.0-alpha.2",
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

Positions are zero-based. CLI option and tooling errors use exit code `2`;
source and runtime failures normally use `1`. `run` forwards the program's
process status, so an explicit exit may also return `2` or another value. A
nonzero exit without a JVM exception is reported as `SPR-PROGRAM-EXIT`, with
the child status in JSON `data.programExitCode`.

## What the tooling does not do yet

The following are **proposed, not implemented**:

- an LSP / IDE language server,
- `sprig fmt`, `sprig test`,
- package manifests, Maven dependency resolution or a module registry,
- incremental checking.

The historical [Agent tool protocol](/en/reference/AGENT_TOOL_PROTOCOL)
contains further proposals. Check `sprig capabilities --json` for current
behavior, and [JVM interop](/en/reference/JVM_INTEROP) for `api` boundaries.

## For agent-assisted workflows

- `sprig check --json` is the cheapest reliable gate before `run`: parse,
  resolve and type-check without generating or executing code.
- Diagnostics carry stable codes, a phase (`LEX`, `SYNTAX`, `NAME`, `TYPE`,
  `FLOW`, `JVM`, `RUNTIME`), a range and often a `hint` naming the next fix.
- `run --json` keeps program output separate from diagnostics, so a failing
  program still yields a parseable result.
- The repository's own quality gates are deliberate: see the
  [AI-assisted development disclosure](/en/project/ai-disclosure).
