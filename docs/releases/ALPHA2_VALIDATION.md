# Sprig v0.1.0-alpha.2 candidate audit

Audit date: 2026-09-26. The latest published prerelease is still
`v0.1.0-alpha.1`. No alpha.2 tag or GitHub release was created. This report
describes the tested development candidate and does not treat earlier reports
as test evidence.

## Candidate and scope

- Audited repository: `/Users/wu/Desktop/cashTradeManagement/Sprig-alpha2`,
  branch `alpha2-rc-audit`, base commit
  `0171d8d66edb200122bc27b925785c09f903b597`, tree
  `2def148c2dafb9926323ca420bc1ee81a7219800`.
- The base tree matches GitHub `main` at
  `788c9888001c805ddff66ee6dadde8068d9e314e` when checked. That main commit's
  JDK 17, JDK 26 and documentation workflows passed. Those hosted results are
  evidence for the base only; the audit changes still require their own hosted
  CI run.
- No `.g4` grammar, keyword, or Sprig v0.7 language design file was changed.
  Work is limited to compiler tooling, metadata, docs, tests, packaging and the
  stage-1 probe.

## Findings and repairs

### P1 — fixed before alpha.2

- CLI parsing silently accepted foreign options and arguments in `version`,
  `codes`, `explain`, `api`, `doctor`, `capabilities`, `check`, `build`, and
  `run`. Missing `-d` operands and misplaced arguments could be ignored or
  misclassified. Command-owned option checks now produce `SPR-CLI-OPTION` and
  status 2; `run` arguments must follow `--`, and option-looking application
  arguments after `--` stay application-owned. A table-driven test exercises
  19 invalid command/option combinations and valid command boundaries.
- `run` already forwarded JVM process status, including 7, while docs claimed
  only 0/1/2. The contract is now explicit: CLI syntax/tooling failures use 2,
  source/runtime failures normally use 1, and `run` forwards the program's
  status. A nonzero program exit without a JVM exception is `SPR-PROGRAM-EXIT`
  with `data.programExitCode`; process status may overlap the CLI range. Real
  `System.exit(0/1/7)` programs are exercised in text and JSON modes.
- The functions help example implied that `main` is an automatic entry point;
  the class example also had a dedented field. Help examples now link to real
  `.spr` files. The tooling suite checks and runs every `.spr` help example.
- Full `sprig api CLASS` output was unnecessarily large for targeted Agent
  questions. `--member NAME` filters the existing metadata model while
  retaining overloads; an empty result has `SPR-JVM-MEMBER`. Metadata now
  distinguishes `signatureSupported` and `interopLevel` (`direct`,
  `erased-generic`, `unsupported`). Backward-compatible `usableFromSprig`
  means the compiler can bind and emit the raw signature; it does not promise
  generic element safety.
- The catalog now has a capability-to-fixture mapping checked by the Agent
  tooling suite. CLI/help/catalog and exit-code docs were reconciled.

### P2 — known limits, not release blockers

- Java arrays and varargs remain unsupported. Generic Java APIs can have an
  erased raw signature but Sprig does not infer exact `List[T]`/`Map[K,V]`
  safety. `interopLevel` states this distinction; no unsafe adapter was added.
- There is no Java collection bridge, Maven resolver, package manager, LSP or
  IDE integration. Stage-1 host file IO remains a narrow Java service.
- The Sprig-written frontend probe is still only 568 lines and is not a
  self-hosted compiler. It now parses typed function declarations, parameters
  and `return`, checks parameter/name scope, and prints those AST nodes. Its
  test runs through compiler check, javac, JVM output, malformed inputs and
  exhaustive visitor evolution. This small extension exposed no need to
  change v0.7 syntax; function calls, return type checking and a full compiler
  remain future implementation work.
- Sprig's checked integer arithmetic does not make floating-point algorithms
  stable. Conditioning, cancellation, accumulation error, physical units and
  Java library numeric behavior still require algorithm-specific judgment.

## Build and verification evidence

Commands were run locally on macOS Apple Silicon with Microsoft OpenJDK
17.0.19 and Oracle OpenJDK 26.0.1. The compiler builds with
`javac --release 17`.

| Command | Result |
|---|---|
| `./scripts/build.sh` | Pass on JDK 17 and JDK 26; regenerates ANTLR and compiles compiler/runtime |
| `./scripts/test.sh` | 103/103 on JDK 17 and JDK 26. Includes 46 semantic cases, 66 numeric checks, 22 correctness regressions, 467 recovery/fuzz checks (435 truncation prefixes), 52 acceptance cases, 13 JSON cases, 10 check/build/run consistency cases, 77 Agent/JVM tooling checks, CLI rejection/process/API contracts and stage-1 probe |
| `ANTLR_JAR="$PWD/tools/antlr-4.13.2-complete.jar" ./tools/test-grammar.sh` | 21 syntax cases passed; syntax-only |
| `./scripts/check-docs.sh` | 17 executable snippets, catalog/docs consistency and VitePress build passed |
| `./scripts/package-alpha.sh` | Created `dist/sprig-v0.1.0-alpha.2-jdk.zip` |
| `python3 tools/check-sdk-archive.py` | Unpacked and tested outside checkout on JDK 17 and JDK 26: version, doctor, capabilities, all help example paths, API, check, run and stage-1 probe |
| `unzip -t dist/sprig-v0.1.0-alpha.2-jdk.zip` | No archive errors; 92 entries, 2.2 MiB; final SHA-256 `d1ced6dc2fad776c0a3035846f868b2bcacdbd1a38ae0f6f0048becb2f4159cd` |

The package intentionally omits raw maintainer acceptance/blind-test evidence
and `ALPHA2_VALIDATION.md`; this material remains in the repository. The SDK
keeps the compiler, runtime, ANTLR, user docs, working examples, and the
stage-1 probe. The archive is a development build from a dirty tree, not a
published release artifact.

## Context-free Agent exercises

The first frozen exercise remains at `acceptance/agent_blind_alpha2/REPORT.md`;
its raw files were not overwritten. It completed 10/10 tasks after one repair
and ran its 128-line program.

The second archive-only exercise completed all 10 tasks against the packaged
SDK using JDK 26. It ran against the archive before the final documentation
clarification below; that edit did not change compiler code. The final archive
was separately smoke-tested on JDK 17 and JDK 26. Its authored 160-line program compiled against a locally
built third-party JAR and ran successfully. It also exercised API discovery,
nullability, overload selection, lambdas, numeric diagnostics, runtime
overflow checks, and match exhaustiveness and evolution. The independent
report and complete command logs are in
`acceptance/agent_blind_alpha2_round2/REPORT.md` and `commands.log`.

The exercise initially characterized an uncaught checked Java exception in
top-level code as a compiler gap. Inspection of `TypeChecker.requireHandled`
and `docs/KNOWN_LIMITATIONS.md` confirms this is the explicitly provisional
top-level rule: top-level code has no caller to declare an effect, so an
uncaught exception propagates and aborts at runtime. Function calls still
require catch or a declared effect. `docs/JVM_INTEROP.md` now states this
distinction, and a regression confirms top-level `Files.readString` can be
checked and run. This is a provisional language boundary, not a newly found
compiler failure.

The agent's copy of the extracted SDK had launcher mode `0644` because its
Python `zipfile` extraction did not restore ZIP Unix permissions. The archive
entry itself is `0755`; the archive checker uses standard `unzip` and invokes
the launcher directly. This did not require a package change.

## Release assessment

No release-blocking defect remains in the locally tested scope after these
repairs and the second blind exercise. Hosted CI for the exact audit commit is
still required before the verdict can be `READY FOR v0.1.0-alpha.2`. Do not
publish a tag or GitHub release from this audit alone.
