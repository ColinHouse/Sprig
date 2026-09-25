# Sprig v0.1.0-alpha.2 development validation

This report records observed results for the alpha.2 development tree on
2026-09-26. It is not a claim that an alpha.2 tag, GitHub release, or hosted
alpha.2 CI run exists. The latest published prerelease remains
`v0.1.0-alpha.1`.

## Baseline and scope

- Work was done on `alpha2-agent-sdk` in an isolated worktree. Its base commit
  is `45891124cc5c01358e6da466e51a4c61892f298b`; its base tree
  `3e5264cee7c6321e8a709baf444fcc59dae833df` exactly matches GitHub
  `main` commit `b32e1d14f5a89e3fc657e4a1e9ffb1865faa0adb`'s tree when
  checked. The user's original checkout was not edited.
- Before development, `./scripts/build.sh`, `./scripts/test.sh` (100/100),
  the 21-case grammar harness, and `./scripts/check-docs.sh` passed locally.
  The published alpha.1 [GitHub Actions run](https://github.com/ColinHouse/Sprig/actions/runs/36059153985)
  passed JDK 17, JDK 26, and documentation jobs on Linux.
- No `.g4` file, keyword, or v0.7 language design version was changed.

## Implemented and observed

- A packaged, versioned compiler catalog supplies `help TOPIC --json` and
  `capabilities --json`. Topics cover language, types, functions, classes,
  variants, match, nullability, errors, collections, numerics, modules, JVM,
  and Agent workflow. `version` reads the same catalog. A consistency gate
  checks version, JDK floor, license, commands, topics, diagnostic codes,
  release state, and linked examples against docs and website content.
- `api CLASS --json` reads real public JDK or supplied JAR metadata, including
  overloads, generic signatures, checked exceptions, nullability policy, and
  unsupported arrays/varargs. The reflection read does not initialize the
  inspected class: a test JAR with a file-writing static initializer left its
  marker absent after `api` and `check`, then wrote it when `run` executed.
  JVM overload diagnostics expose candidate signatures and rejection reasons.
- `check`, `build`, `run`, and `api` use the same explicit `--classpath` layer.
  Tests covered JAR lookup, a missing path diagnostic, platform-separated and
  repeated entries, and first-entry duplicate-class precedence. No Maven
  resolver or implicit download was added.
- `doctor --json` and structured `explain CODE --json` are available. Java
  reference results remain conservatively nullable, and Java `Object`
  parameters no longer make an unproved nullable exception.
- A real `check`-versus-`javac` defect was found during this work: Java
  `char/Character/Short/Byte` results were mapped to Sprig `String/Int32`
  without adapting generated Java values. The generator/runtime now adapt
  results (including boxed nulls), arbitrary `String` to Java `char` is
  rejected before generation, and writes to Java fields requiring adapters
  are rejected. The custom-JAR regression runs through check, Java generation,
  javac, and JVM execution for primitive and boxed long, int, short, byte,
  float, double, char, and boolean results.
- The archive contains the compiler, ANTLR, runtime, guides, implemented
  status, numeric contract, diagnostics, examples, and stage-1 probe. The
  `HostFiles` Java boundary supplies UTF-8 and path services only; lexer,
  layout, parser, AST, symbol checking, diagnostics, and pretty printing in
  the probe are written in Sprig.

## Commands and results

The final local commands were run on macOS Apple Silicon with JDK 17.0.19
and 26.0.1. `scripts/build.sh` regenerates ANTLR code and compiles the
compiler/runtime with `javac --release 17`.

| Command | Observed result |
|---|---|
| `./scripts/build.sh` | Pass on JDK 17 and JDK 26 |
| `./scripts/test.sh` | 102/102 on each JDK; includes 46 semantic cases, 66 numeric checks, 22 correctness regressions, 467 parser recovery checks (435 truncation prefixes), 52 independent acceptance cases, 13 JSON cases, 10 check/build/run consistency cases, and stage-1 probe. The Agent/JVM tooling sub-suite was 40/40 in those full runs; three added text-output checks then passed separately on both JDKs (43/43). |
| `ANTLR_JAR="$PWD/tools/antlr-4.13.2-complete.jar" ./tools/test-grammar.sh` | 21 syntax cases passed; syntax only |
| `./scripts/check-docs.sh` | 17 executable snippets, metadata consistency, and VitePress build passed |
| `./scripts/package-alpha.sh` | Created `dist/sprig-v0.1.0-alpha.2-jdk.zip` |
| `python3 tools/check-sdk-archive.py` | Extracted archive and ran offline help, API, doctor, check, run, and probe on JDK 17 and 26 |

The stage-1 probe's own gate checks `sprig check`, `sprig build`/javac, JVM
golden output, malformed-input ranges, and an added variant case causing
`SPR-MATCH-NONEXHAUSTIVE`.

## Context-free Agent SDK exercise

A separate Agent received a copy of a pre-final alpha.2 archive, ten neutral
tasks, and no repository or website access. Frozen sources, JSON responses,
stdout/stderr, and the full methodology are in
`acceptance/agent_blind_alpha2/REPORT.md`. I independently parsed the saved
JSON files and checked the output artifacts. First checks passed for 9/10
tasks; the intended immutable-list error was reported as
`SPR-COLLECTION-IMMUTABLE` in the TYPE phase, then repaired in one iteration.
All ten final programs ran with exit code 0 and empty stderr. The medium
program was expanded from an initial 34 lines to 128 total lines (115
nonblank), then checked and ran without a repair. The recorded exercise used
37 SDK CLI calls: 12 shared discovery and 25 task-specific. No invalid
syntax or JVM API hallucination was observed in this bounded test. This is
evidence for the tested tasks, not a general Agent success rate.

## Remaining boundaries

- The Sprig-written frontend probe is 489 lines, covers a meaningful subset,
  and handles malformed input; it is **below the requested several thousand
  lines** and is not a stage-1 or self-hosted compiler. The next iteration
  should expand the subset and stress symbols/types before inferring a need
  for new language syntax.
- Java arrays, varargs, exact generic collection mapping, type-use
  nullability, and Java↔Sprig collection adapters remain unavailable or
  limited. `api` reports these boundaries; it does not make unsafe mappings.
- Java library operations do not inherit Sprig's checked numeric rules.
  Float stability, Java API contracts, and algorithm correctness remain the
  caller's responsibility.
- No alpha.2 hosted Linux CI result or public alpha.2 prerelease exists yet.
  The archive smoke is local and offline. `docs/KNOWN_LIMITATIONS.md` records
  further implementation limits.

The next release gate is hosted JDK 17/26 CI and documentation CI on the
alpha.2 commit. Publishing a tag or GitHub prerelease is a separate action.
