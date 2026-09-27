# Web and SQLite Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** A typed Sprig HTTP library, explicit OpenAPI, and an actual SQLite ledger backend.
**Architecture:** Source function types expose existing FunctionType; concrete Fn0–Fn3 signatures bridge JVM calls. Small JDK HTTP/JDBC adapters handle platform lifetimes. Routing, schemas, SQL and application logic remain Sprig.
**Tech Stack:** ANTLR 4.13.2, JDK17, Python test drivers, SQLite JDBC, existing TS VS Code extension.
**Spec:** User attachment 4649f096-13c2-471f-b6a4-55dc2f2bc3a2, phases 0–8.
**Global constraints:** No Any/SAM magic, no numeric changes, no design-kit changes, no publication. Preserve baseline evidence and actual test results.

- [x] Baseline full verify and query evidence.
- [x] JSON explicit lookup and documentation drift repair.
- [x] Function type grammar/resolution/diagnostics and concrete JVM Fn bridge, with negative/runtime tests.
- [x] HTTP host experiment and Sprig web library with explicit schemas/OpenAPI and HTTP tests.
- [x] Thin JDBC adapter, pinned SQLite example and ledger HTTP integration.
- [x] VS Code terminal command and editor verification.
- [x] Independent review, full verification and engineering/design pressure reports.

## Decisions and evidence ledger

- Reused clean checkout after verifying plugin PR #30 merged; branch codex/web-sqlite starts at ad18618.
- Baseline verification runs before implementation changes; no source files changed yet.
- Function results use fn(A) -> R; nullable callable uses (fn(A) -> R)? to distinguish nullable result.
- Java static factory syntax is not added. Ordinary module response helpers are the canonical Sprig-facing API.

- Review repaired erased nullable T callback guards, invalid OpenAPI duplicates/templates, prefixed COMMIT bypass, and failed INSERT RETURNING snapshot commits; all have runtime regressions.
- Full gate first ran build/tests77/grammar26/snippets20 successfully, then found new Chinese website dead link. Fixed link; complete rerun passed with exit0, including VitePress, local links,17 editor tests and VSIX packaging.
- Actual Swagger CDN UI Execute GET200 observed; targeted JDK17 and JDK26 callable/web/SQLite tests passed.
- Development SDK includes library packages, excludes local locks/databases; relocated offline persistence/web lifecycle verified.
- Ruling: existing absolute alternate-symlink project discovery behavior is recorded, not changed in this application milestone; relative project commands work.
- Integration: retain implementation on codex/web-sqlite; no merge, push or publication action is part of this request.
