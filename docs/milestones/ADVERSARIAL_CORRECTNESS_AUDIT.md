# Adversarial correctness audit — final report

- Start: `main` at `ad3c8321c2d13ba7bf6c2f208be5d7ccd8e09d5a`, 2026-09-28.
- Local branch: `codex/adversarial-correctness-audit`. Compiler 0.3.0-alpha.1, language 0.8-dev.
- Scope: contract-derived attacks on the current toolchain. No new features, no spec
  changes, no release/tag/publication, no weakened tests. Windows remains an
  experimental, non-blocking preview (issue #33).

Result: 8 confirmed defects fixed (three S0, one S1, four S2), 2 design decisions
left open, 2 attack candidates rejected as not-a-bug. Every fix has a regression
under `tests/adversarial/current/`, wired into `scripts/test.py`.

## Fixed defects

### A01 — S0 — inferred module binding types escaped function checking

Reproducer family: `tests/adversarial/current/semantics/global_generic.spr`,
`global_wrong.spr`, `global_lambda_bad.spr`, `global_field_bad.spr`.
An unannotated top-level `let box = Box[String](value="bad")` could be returned
from a function declared `-> Box[Int]`; `sprig check` exited 0, javac succeeded
and the JVM raised a generated `String -> Long` cast failure. The scalar version
(`let box = "bad"` returned as `Int`) passed `check` and failed only in javac.

Cause: function bodies were checked before unannotated top variables, and
`narrowedType` substituted `ERROR`, which assignability accepted.
Fix: `TypeChecker.check` pre-infers every unannotated top-level binding in its
own expression context before any body; cycles are reported as `SPR-TYPE-INFER`,
and inference diagnostics stop the walk instead of publishing `ERROR`.
Commit `52e53c6`. Regression: `check_semantics.py` (`global_*`, 35 attacks).

### A02 — S2 — default initializer effects missed by earlier callers

Reproducers: `default_earlier_caller.spr`, `forward_default_effect.spr`,
`default_lambda_effect.spr`. A class declared after its caller, or an `Outer`
default constructing a later `Inner` whose default calls a `throws Error`
function, let `Error` escape a caller that did not declare it; `check` exited 0.
The existing `tests/review_cases/default_checked_exception.spr` only covered the
declaration-before-caller order, which already worked.

Cause: default effects were discovered in declaration order and never propagated
through constructors whose defaults are checked later.
Fix: a discovery pass records the omitted-default dependency graph, then a
worklist propagates effects transitively before normal checking; lambda bodies
are excluded from the graph. The normal pass still validates each expression.
Commit `52e53c6`. Regression: `default_*` cases plus the existing correctness
checks.

### A03 — design-required — named argument evaluation order

`Pair(b=mark("B"), a=mark("A"))` traces `AB`: written order is not preserved.
The docs define declaration order for defaults but not explicit named-argument
evaluation order. No semantic change made without that decision.

### A04 — not-a-bug — mutable nullable binding

Assigning `null` inside `if x != null` for a mutable binding and then
dereferencing is rejected `SPR-TYPE-NULLABLE`, matching the documented
immutable-only narrowing.

### A05 — not-a-bug — lambda syntax and assignment grammar

Explicit lambda return annotations and call-expression assignment targets are
outside the grammar; the supported `fn(...) => expression` form keeps immutable
nullable narrowing into the closure.

### A06 — S0 — generated Java namespace collisions

Reproducers: `check_type_names.py` and `names/`. Two `model.spr` modules in
different directories produced one generated class name; a module basename
containing `$2` collided with the duplicate-basename suffix; a class named
`M_main` collided with the entry holder; variant nested binary names could
collide with module holders. A program expected to print `1,2,3` silently
printed `1,3,3` with check/javac/run all reporting success.

Cause: basename-derived generated names were not reserved against each other.
Fix: module holders are named first and reserved; type names are allocated with
uniqueness checks that include module names and variant case binary names.
Commit `222ca11`. Regression: `check_type_names.py` (36 checks: same basenames,
sanitized names, aliases, 40-file re-export chains, 40 reference-tree
metamorphisms, formatter identity, `api` origin without initialization).

### A07 — S2 — definite completion through `finally`

`func f() -> Int: try: return 1; finally: print("finally")` was rejected with
`SPR-FLOW-MISSING-RETURN`; a `finally` that itself returns was rejected too.
Baseline boolean predicates required at least one `catch` and ignored `finally`.

Fix: replace the predicates with completion sets (`NORMAL/RETURN/THROW/BREAK/
CONTINUE`); an abrupt `finally` overrides the pending outcome, a normally
completing `finally` preserves it; loops stay conservative.
Commit `52e53c6`. Regression: `finally_return`, `finally_exits`,
`finally_nested`, `finally_catch`, `finally_break_override`,
`finally_continue_override`, `finally_missing_return`, `finally_unreachable`.

### A08 — S2 — bare-CR lexical positions

The grammar explicitly accepts `\r`, but ANTLR advances line/column only for
LF, so CR-only sources produced incorrect INDENT/DEDENT diagnostics all on
line 0. Fix: the bare-CR NEWLINE alternative updates the lexer line/column;
CRLF is untouched because the alternative action applies only to `'\r'`.
Commit `cd0b493`. Regression: `check_layout_lines.py`, 16 attacks across
LF/CRLF/CR/mixed with and without a final newline, asserting independent parser
acceptance, static/JVM results, formatter bytes, idempotence, post-format
execution and exact diagnostic coordinates for `@` and tab.

### A09 — design-required — module read before initialization

An annotated global initialized by a function that reads a later global sees the
Java default `null` field and throws at runtime. Source-order execution is
documented, but the forward-read policy and interprocedural initialization
checking are not defined. No behavior may be assumed safe until that decision.

### A10 — S1 — compiler bridges shadowed source methods

With a Java generic base (`Bridge extends Base<String>`), `sprig check` resolved
the erased compiler bridge `Object value()` instead of the source method
`String value()`, rejected valid code with `Java class Object has no member
'length'`, and bound erased `Object` arguments for `Sink`/`InterfaceSink`
inherited methods. Reproduced on the baseline build by restoring only
`JvmMetadata.java`: 4 of 8 harness checks fail.

Fix: `JvmMetadata.unsupportedReason` marks public bridges superseded by a
source method, including bridges forwarding a substituted inherited signature
through arbitrary generic parents, so overload resolution selects the source
method.
Commit `e5d02e2`. Regression: `check_jvm_bridges.py` against an independent
`javac`/`java` oracle (source methods, overloads, inherited and interface
bridges, character/boxed/null adapters, erased-bridge argument rejection).

### A11 — S2 — unnamed-package imports deferred to javac

`import Bridge as Bridge` for a default-package class passed `sprig check`
because Java reflection can load the class, then failed inside javac because
generated code lives in `package sprig.user` and Java forbids referencing the
unnamed package from a named package. Fix: the compiler rejects such imports at
check time with `SPR-JVM-CLASS` and a move-to-a-package hint; docs updated.
Commit `e5d02e2`. Regression: `unnamed-package-rejected-before-javac`.

### A12 — S0 (security) — hostile SDK ZIP entries

`check_install_failures.py` showed a symlinked `sprig-install.json` (or linked
directory) in a correctly checksummed archive could make the installer write
outside the staging directory; a sentinel outside `HOME` was overwritten. Also
covered: duplicate entries, `..` traversal, absolute paths, `./` aliases,
backslash paths, truncated ZIPs, wrong root, failing smoke test and wrong
version.

Fix: reject link entries before extraction, reject unsafe/duplicate paths, walk
the extracted tree for links, and keep the previous SDK, `current` link,
launcher and staging state untouched on any failure.
Commit `fb04897`. Regression: 12 hostile inputs, each asserting the sentinel,
previous SDK and filesystem state are preserved and the old SDK still executes.

## Verified, no new defect found

- Seeded generators/metamorphic: `check_properties.py` builds 24 variants/
  expression trees with an independent Python integer oracle, exact JVM output,
  formatter idempotence and 24 static missing-case rejections.
- Library composition and fresh discovery: `check_sdk_composition.py` installs a
  locally served release SDK into a clean HOME, then runs discovery, `init`,
  `resolve`, `check`, `fmt`, `build`, `run`, `project`, `deps`, `api`,
  `explain`, `upgrade --check`, CLI/files/JSON/time, web and SQLite with an
  independent `sqlite3` assertion, plus the agent report from the installed SDK.
- Resolver/locks/cache: existing `tests/project_deps` and `tests/maven` cover
  stale locks, duplicate aliases, traversal, transitive scope, offline cache,
  moved Git branches and corrupted Maven artifacts; hostile install inputs are
  new.
- Formatter/reexports/expression match: existing formatter, re-export and match
  suites plus the new identity attacks and byte/idempotence/failure-preservation
  checks in `check_layout_lines.py`.
- `sprig api`/`check` do not execute user code: asserted by the 40-module facade
  chain (`api-no-init`) and the SDK composition run.

## Verification evidence

- Baseline (before fixes): `./scripts/verify.sh` was green at 95 summary gates
  and 31 grammar cases, and CI `main` was green, which is why these defects had
  survived.
- `./scripts/verify.sh` after fixes: passed — build, `scripts/test.py` **102
  gates/cases passed, 0 failed**, `tools/test-grammar.py` 31 cases, docs
  snippets 20/20, editor TextMate/CLI/JVM/VSIX.
- `python3 scripts/package-alpha.py --skip-build` and
  `python3 tools/check-sdk-archive.py`: passed (36 documentation links, three
  showcases, offline help/api/doctor/check/run/probe/init/resolve/project).
- Direct baseline reproduction: reverting `JvmMetadata.java` alone leaves
  `check_jvm_bridges.py` at 4/8 failures with the `Object.length` diagnostic;
  the unnamed-package case cannot pass on baseline by construction.
- Raw logs are outside the tree; commands and totals are reproducible from the
  suites wired into `scripts/test.py`.

## Open design decisions

- A03: explicit named-argument evaluation order.
- A09: module forward-read/initialization policy and enforcement.

## Commit map

| Commit | Content |
| --- | --- |
| `52e53c6` | A01, A02, A07 and the semantic regression suite |
| `222ca11` | A06 and the generated-name identity suite |
| `cd0b493` | A08 and the newline/layout suite |
| `e5d02e2` | A10, A11, docs and the JVM bridge suite |
| `fb04897` | A12 and the hostile installer suite |
