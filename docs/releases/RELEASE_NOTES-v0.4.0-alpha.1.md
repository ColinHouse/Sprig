# Sprig v0.4.0-alpha.1 — formatter, re-exports, match expressions and a correctness pass

Compiler **0.4.0-alpha.1**, **JDK 17+**, **Apache-2.0**. Experimental Alpha for
trying small tools and contributing, not production migration. Publication runs
through the exact-tag release workflow; the executed evidence is recorded in the
[release validation record](https://github.com/ColinHouse/Sprig/blob/main/docs/RELEASE_VALIDATION.md).

## Highlights since v0.3.0-alpha.1

- **Canonical formatter** — `sprig fmt` preserves comments and trivia, formats a
  file or a project, supports `--check`, and replaces files atomically. The
  grammar now retains comments for the formatter while semantic layout stays
  strict.
- **Explicit module re-exports** — `export alias.Symbol` forwards original
  declarations through module facades without wrapper code; collisions, cycles
  and package boundaries are diagnosed, and `sprig api` reports the originating
  module.
- **Expression `match`** — `match` can be used as an expression with strict
  contextual result types, shared exhaustive/binder rules, side-effect-safe
  scrutinee evaluation, and checked effects.
- **Adversarial correctness pass** — nine confirmed defects fixed with
  independent regressions: inferred global binding soundness, generated Java
  type-name collisions, default field effect propagation, `finally` completion
  analysis, bare-CR layout positions, JVM source-vs-bridge method resolution,
  unnamed-package imports rejected before `javac`, managed-installer ZIP
  hardening (links, traversal, duplicate entries), and indexed-assignment
  key/index type checking (writes now enforce the same compatibility the read
  path already enforced).
- **Managed SDK install and upgrade** — versioned user-scoped installs under
  `~/.sprig` with checksum + smoke verification, atomic `current` switch and
  `sprig upgrade`/`upgrade --check` on Linux/macOS; source checkouts and
  unmanaged ZIPs are refused with actionable diagnostics.
- **Unicode code-point strings** — `length`, indexing, `charAt`, `codeAt`,
  `substring`, `indexOf` and iteration count Unicode code points; there is no
  `Char` type, and the JVM boundary remains a single UTF-16 unit.
- **`sprig api` for Sprig and Java** — compiler-resolved module/project metadata
  with `--member`, plus existing Java class inspection; neither executes
  application code. `capabilities` gained feature guidance and `explain` richer
  repair metadata.
- **Libraries and applications** — `sprig-cli`, `sprig-sqlite` (including
  migrations) and `sprig-web`, with the mini-web, ledger, sqlite-migrations,
  json-select and agent-tool examples, all exercised by the release gates.
- **Agent-facing diagnostics** — every stable `SPR-*` code has a concise
  meaning plus structured `sprig explain --json` guidance (causes, safe fixes,
  related codes, help topic), and capability claims point to executable
  regression evidence enforced by a consistency gate. Repair metadata remains
  guidance only: `machineApplicable` stays `false`, with no automatic semantic
  rewrites.
- **Repository hygiene** — milestone plans and blind-trial evidence are archived,
  audit regressions are permanent under `tests/adversarial/regressions/`, and
  `docs/README.md` defines the documentation trust order.

## Try it

Extract `sprig-v0.4.0-alpha.1-jdk.zip` after checking its `.sha256`, add `bin`
to PATH, and install JDK17+ (the SDK does not bundle Java):

```text
sprig version
sprig init my-tool
cd my-tool
sprig resolve
sprig run
sprig fmt --check
```

On Windows use `bin\sprig.cmd`; on Unix `bin/sprig`. See the
[formatter](../FORMATTER.md), [re-exports](../MODULE_REEXPORTS.md),
[match expressions](../MATCH_EXPRESSIONS.md) and
[known limitations](../KNOWN_LIMITATIONS.md) contracts.

## Compatibility and limits

No existing syntax, numeric, nullability, generic or effect contract was
redesigned. Lock schema 3 and the project model are unchanged. Publishing and
registry/authentication, LSP/IDE, generic inference/variance, interfaces and
self-hosting remain absent; Windows stays an experimental, non-blocking preview.
Types do not prove numerical stability, and Java generic/array/varargs interop
remains limited. `sprig api` and `sprig check` never execute application code.
