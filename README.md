# Sprig

<p align="center">
  <img src="website/public/logo-round.png" alt="Sprig icon" width="170">
</p>

**An indentation-based, statically typed language for the JVM** with sealed
variants, exhaustive `match`, checked numerics and explicit interop — designed
to be readable by people and predictable for coding agents.

**Docs:** [简体中文](https://colinhouse.github.io/Sprig/) ·
[English](https://colinhouse.github.io/Sprig/en/) ·
[Repository](https://github.com/ColinHouse/Sprig) ·
[Examples](https://github.com/ColinHouse/Sprig/tree/main/examples) ·
[Known limitations](docs/KNOWN_LIMITATIONS.md)

[![CI](https://github.com/ColinHouse/Sprig/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/ColinHouse/Sprig/actions/workflows/ci.yml)
[![Documentation](https://img.shields.io/website?url=https%3A%2F%2Fcolinhouse.github.io%2FSprig%2F&label=docs)](https://colinhouse.github.io/Sprig/)
[![License](https://img.shields.io/github/license/ColinHouse/Sprig)](LICENSE)
[![Release](https://img.shields.io/github/v/release/ColinHouse/Sprig?include_prereleases&label=release)](https://github.com/ColinHouse/Sprig/releases)
[![JDK](https://img.shields.io/badge/JDK-17%2B-blue)](https://adoptium.net/)

> **Current version `v0.1.0-alpha.1` — published as a prerelease.** Download
> the archive from the [releases page](https://github.com/ColinHouse/Sprig/releases/tag/v0.1.0-alpha.1)
> or build from source. The compiler parses, checks, emits Java and runs on
> the JVM, but it is **not self-hosted** and has no package manager, language
> server or standard-library distribution. It builds with `javac --release 17`
> and has been run end-to-end on JDK 17 and 26. See the
> [release notes](docs/releases/RELEASE_NOTES-v0.1.0-alpha.1.md) and
> [known limitations](docs/KNOWN_LIMITATIONS.md).

The repository's `0.2.0-alpha.1` development tree adds v0.8 multi-parameter generics, an `Equatable` capability, a `sprig.toml` project model and offline agent-facing commands
(`help <topic>`, `capabilities`, `api`, `doctor`, structured `explain`) and
explicit local `--classpath`. The **latest published archive remains
v0.1.0-alpha.1** until the next candidate passes its independent release gates.

The latest consolidated acceptance report is [REVIEW_REPORT.md](REVIEW_REPORT.md).
The current v0.8 candidate is **NOT READY — RELEASE BLOCKERS REMAIN**: dependency
resolution/lockfiles and the integrated stage-1 project are incomplete.

## What Sprig is

Sprig is a small, complete-looking language implemented by a Java stage-0
compiler in this repository. It keeps one canonical syntax for each
operation and makes semantically dangerous defaults explicit:

- **Indentation, not braces.** Tabs are rejected; blocks are layout tokens.
- **Static types without escape hatches.** `let`/`var`, local inference,
  nullable `T?`, flow narrowing, distinct immutable and mutable collections,
  no truthiness, no implicit numeric promotion, no `Any`.
- **Sealed variants and exhaustive `match`.** `variant` declares a closed sum
  type; `match` must cover every case. Add a case and every visitor that
  misses it fails to compile.
- **Checked numbers.** `Int`/`Int32` overflow raises an error instead of
  wrapping. Integer `/` is rejected in favor of explicit `divTrunc`.
  `BigInt`/`Decimal` are exact, `Float`/`Float32` stay IEEE 754.
- **Explicit JVM interop.** Import a Java class with an alias; Java reference
  results are nullable and must be narrowed before use.
- **Agent-friendly feedback.** Stable diagnostic codes, a phase per error,
  and a single JSON result envelope for `check`, `build` and `run`.

The compiler is the current stage; the v0.7 language design kit lives in
[`spec/`](spec/) and the implementation status lives next to its tests in
[`docs/FEATURE_STATUS_IMPLEMENTED.md`](docs/FEATURE_STATUS_IMPLEMENTED.md).

## Quick start

Requirements: **JDK 17 or newer** (tested on 17.0.19 and 26.0.1). Python 3.12+
and `curl` are only needed for the test suite and source build.

**Option A — download the alpha archive** (compiler/runtime + ANTLR, no JDK):

```bash
curl -LO https://github.com/ColinHouse/Sprig/releases/download/v0.1.0-alpha.1/sprig-v0.1.0-alpha.1-jdk.zip
curl -LO https://github.com/ColinHouse/Sprig/releases/download/v0.1.0-alpha.1/sprig-v0.1.0-alpha.1-jdk.zip.sha256
shasum -a 256 -c sprig-v0.1.0-alpha.1-jdk.zip.sha256
unzip sprig-v0.1.0-alpha.1-jdk.zip
cd sprig-v0.1.0-alpha.1-jdk
./bin/sprig run examples/hello.spr
```

**Option B — build from source:**

```bash
git clone https://github.com/ColinHouse/Sprig.git
cd Sprig
./scripts/build.sh
./bin/sprig run examples/hello.spr
```

Expected output:

```text
Hello, Ada!
```

The first build downloads ANTLR 4.13.2 from Maven Central into
`tools/antlr-4.13.2-complete.jar` and verifies a pinned SHA-256 digest. Emit
generated Java and class files with:

```bash
./bin/sprig build examples/hello.spr -d build/hello
```

Machine-readable results:

```bash
./bin/sprig check --json examples/hello.spr
./bin/sprig explain SPR-MATCH-NONEXHAUSTIVE
```

Run the full test suite (syntax, semantics, JVM end-to-end, numeric
boundaries, parser recovery, independent acceptance):

```bash
./scripts/test.sh
ANTLR_JAR="$PWD/tools/antlr-4.13.2-complete.jar" ./tools/test-grammar.sh
```

## What works today

- Typed functions and methods, local type inference, `let`/`var`, classes with
  named constructors and field defaults.
- `enum`, sealed `variant` and exhaustive `match` statements, including
  recursive visitors written in Sprig.
- Nullable types with flow narrowing, typed `throws`/`catch`/`finally`, and
  local modules with import-cycle detection.
- `List`, `MutableList`, `Map`, `MutableMap` with explicit snapshots.
- Checked `Int`/`Int32`, explicit integer quotient, `BigInt`, `Decimal`, IEEE
  `Float`/`Float32` — see [`docs/NUMERIC_SEMANTICS.md`](docs/NUMERIC_SEMANTICS.md).
- JVM interop for imported classes: constructors, fields, methods, overloads,
  checked exceptions, conservative nullability.
- `sprig check`, `build`, `run`, `explain`, `codes`, `--json`,
  `--syntax-only`; alpha.2 development adds topic `help`, `capabilities`,
  `api`, `doctor` and explicit local `--classpath`.

The authoritative list is
[`docs/FEATURE_STATUS_IMPLEMENTED.md`](docs/FEATURE_STATUS_IMPLEMENTED.md).

## Documentation

The official documentation site is built from this repository with VitePress
and is bilingual: Simplified Chinese at <https://colinhouse.github.io/Sprig/>
and English at <https://colinhouse.github.io/Sprig/en/>.

- **Source:** [`website/`](website/) — `cd website && npm ci && npm run docs:dev`
- **Reference documents:** [`docs/`](docs/) for implementation semantics and
  [`spec/`](spec/) for the v0.7 design kit. These authoritative documents are
  English; the site generates the English reference pages from them at build
  time, so there is one copy of each.
- **Brand assets:** `assets/brand/` holds the source artwork and the generator
  for the site logo, favicons and social card.

## Repository layout

```text
compiler/     Java stage-0 compiler (authoritative source)
runtime/      Java runtime for generated programs
grammar/      SprigLexer.g4, SprigParser.g4 (authoritative grammar)
spec/         v0.7 language design kit
docs/         numeric contract, diagnostics, status, limits, roadmap, releases
examples/     runnable example programs
tests/        syntax, semantics, runtime, numeric, correctness, recovery
acceptance/   independent acceptance cases and matrices
scripts/      build, test, docs and packaging entry points
tools/        pinned ANTLR tool, grammar harness, static checks
website/      VitePress documentation site
bin/, build/  generated locally by scripts/build.sh (not committed)
```

## Known limitations

The compiler is a Java seed that emits Java source and invokes `javac`; it
does not compile itself. v0.8 single-parameter generics are implemented, but
multiple type parameters, inference, capability implications, inheritance,
`match` expressions, arrays, varargs, full Java generic/annotation interop,
file IO, a project/dependency system, a package manager, a language server and
stage-1 self-hosting are **not implemented**. Runtime numeric errors do not always carry an exact source
span. Local verification covers macOS Apple Silicon with OpenJDK 17.0.19 and
26.0.1; hosted CI covers Linux. See
[`docs/KNOWN_LIMITATIONS.md`](docs/KNOWN_LIMITATIONS.md).

## Contributing

Contributions are welcome, including AI-assisted ones — this project is built
that way. The rules are:

- [`CONTRIBUTING.md`](CONTRIBUTING.md) — setup, required commands, pull
  request expectations.
- [`AI_DISCLOSURE.md`](AI_DISCLOSURE.md) — disclose significant AI assistance,
  say what you verified, and never weaken a test to make it pass.
- [`AGENTS.md`](AGENTS.md) — the operating guide coding agents should read
  first.

Report reproducible problems through
[Issues](https://github.com/ColinHouse/Sprig/issues) with the Sprig version,
JDK/platform, minimal source and exact command.

## License

Sprig is licensed under the **Apache License, Version 2.0** — see
[`LICENSE`](LICENSE) and [`NOTICE`](NOTICE). Third-party components and the
icon provenance note are in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md);
the scope summary is in [`LICENSE_STATUS.md`](LICENSE_STATUS.md).

## 中文简介

Sprig 是一门缩进式、静态类型的 JVM 语言，核心特性包括：sealed `variant` 与
穷尽 `match`、可空类型与流分析收窄、受检整数运算（溢出报错而非静默回绕）、
精确的 `BigInt`/`Decimal`、以及显式的 JVM 互操作。当前由仓库内的 Java
stage-0 编译器实现：解析 `.spr` → 类型检查 → 生成 Java → `javac` → JVM
运行；**尚未自举**，也没有包管理器、LSP、IDE 插件或标准库发行版。

安装（需要 JDK 17 或更新版本）：从
[Releases](https://github.com/ColinHouse/Sprig/releases/tag/v0.1.0-alpha.1)
下载 `sprig-v0.1.0-alpha.1-jdk.zip`，用同名 `.sha256` 文件校验后解压即可；
也可以从源码构建：

```bash
git clone https://github.com/ColinHouse/Sprig.git
cd Sprig
./scripts/build.sh
./bin/sprig run examples/hello.spr
```

完整文档见 <https://colinhouse.github.io/Sprig/>；已知限制见
[`docs/KNOWN_LIMITATIONS.md`](docs/KNOWN_LIMITATIONS.md)。项目采用
Apache-2.0 许可证；欢迎 AI 辅助贡献，但必须通过构建、测试与审查，并说明
你实际验证过的内容。
