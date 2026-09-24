# Sprig

Sprig is an indentation-based, statically typed programming language with its
own syntax, sealed variants, exhaustive `match`, checked numerics and explicit
JVM interop. This repository contains the **Java stage-0 compiler**, the
runtime it emits calls to, the language design kit, tests, examples and the
official documentation site.

> **Status:** `0.1.0-alpha.1` (language design v0.7) — an experimental
> stage-0 compiler. **Not self-hosted.** There is no package manager, language
> server, IDE plugin or standard-library distribution yet. The project owner
> has **not selected a license**, so public redistribution is not cleared.
> Details: [release status](docs/releases/RELEASE_NOTES-v0.1.0-alpha.1.md),
> [known limitations](docs/KNOWN_LIMITATIONS.md),
> [license status](LICENSE_STATUS.md).

## What works today

- Typed functions and methods, local type inference, `let`/`var`, classes with
  named constructors and field defaults.
- `enum`, sealed `variant` and exhaustive `match` statements, including
  recursive visitors written in Sprig.
- Nullable types `T?` with flow narrowing, typed `throws`/`catch`/`finally`,
  and local modules with import cycles rejected.
- Distinct immutable/mutable collections: `List`, `MutableList`, `Map`,
  `MutableMap`, with explicit snapshots.
- Checked `Int`/`Int32` arithmetic (overflow raises an error instead of
  wrapping), explicit integer division, `BigInt`, `Decimal`, and IEEE
  `Float`/`Float32` with documented conversion rules.
- JVM interop for imported classes: constructors, fields, methods, overloads
  and checked exceptions, with conservative nullability at the boundary.
- `sprig check`, `build`, `run`, `explain`, `codes`, `--json`, `--syntax-only`.

The authoritative list is
[`docs/FEATURE_STATUS_IMPLEMENTED.md`](docs/FEATURE_STATUS_IMPLEMENTED.md),
maintained next to the tests that verify it.

## Quick start

Requirements: JDK 26 or newer (the only runtime tested so far), Python 3.12+
for the test scripts, and `curl` for the first build. The compiler is built
with `javac --release 17`, but JDK 17 runtime execution has not been verified.

```bash
./scripts/build.sh
./bin/sprig version
./bin/sprig run examples/hello.spr
```

Expected output:

```text
Hello, Ada!
```

The first build downloads ANTLR 4.13.2 from Maven Central into
`tools/antlr-4.13.2-complete.jar` and verifies a pinned SHA-256 digest. To emit
generated Java and class files:

```bash
./bin/sprig build examples/hello.spr -d build/hello
```

Run the full project test suite (syntax, semantics, JVM end-to-end, numeric
boundaries, parser recovery, independent acceptance):

```bash
./scripts/test.sh
```

On the current tree this reports `100 passed, 0 failed`. The grammar smoke
harness is separate:

```bash
ANTLR_JAR="$PWD/tools/antlr-4.13.2-complete.jar" ./tools/test-grammar.sh
```

## Documentation

- **Website** (`website/`): the official VitePress documentation site, built
  from the same repository. Run it with Node.js 20+:
  ```bash
  cd website
  npm ci
  npm run docs:dev      # local development
  npm run docs:build    # production build
  ```
  Reference and project pages are generated from the authoritative root
  documents during the build; edit the root documents, not the copies.
- **`docs/`**: implementation-facing documents — numeric semantics, numeric
  design decisions, diagnostic codes, implemented features, known limitations
  and the stage-1 roadmap.
- **`spec/`**: the Sprig v0.7 design kit (language contract and agent-facing
  design decisions). Its examples describe target semantics and are not
  automatically executable.

## Repository layout

```text
compiler/     Java stage-0 compiler (authoritative source)
runtime/      Java runtime for generated programs
grammar/      SprigLexer.g4, SprigParser.g4 (authoritative grammar)
spec/         v0.7 language design kit
docs/         implementation documentation and release notes
examples/     runnable example programs
tests/        syntax, semantics, runtime, numeric, correctness, recovery
acceptance/   independent acceptance cases and matrices
scripts/      build, test, docs and packaging entry points
tools/        pinned ANTLR tool, grammar harness, static checks
website/      VitePress documentation site
bin/, build/  generated locally by scripts/build.sh (not committed)
```

## Known limitations

The compiler is a Java seed that emits Java source and invokes `javac`; it does
not compile itself. User-defined generics, inheritance, `match` expressions,
arrays, varargs, full Java generic/annotation interop, file IO, a package
manager, a language server and stage-1 self-hosting are **not implemented**.
See [`docs/KNOWN_LIMITATIONS.md`](docs/KNOWN_LIMITATIONS.md).

## Contributing, license and AI disclosure

- Contribution rules: [`CONTRIBUTING.md`](CONTRIBUTING.md).
- AI-assisted development disclosure: [`AI_DISCLOSURE.md`](AI_DISCLOSURE.md).
- License: no license has been selected yet. See
  [`LICENSE_STATUS.md`](LICENSE_STATUS.md). **Do not publish a release until
  the owner records one.** Third-party notices (ANTLR, and icon provenance to
  confirm) are in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## 中文简介

Sprig 是一门缩进式、静态类型的 JVM 语言，目前由 Java stage-0 编译器实现
（源码解析 → 类型检查 → 生成 Java → `javac` → JVM 运行），尚未自举。已实现
函数、类、enum、`variant` 与穷尽 `match`、可空类型、`throws`/`catch`、模块、
不可变/可变集合、受检整数、`BigInt`、`Decimal` 与浮点规则，以及常见的 JVM
互操作；没有包管理器、LSP、IDE 插件和标准库发行版。构建与运行方式见上方
Quick start。项目尚未选择许可证，公开再分发与正式发行仍被阻止。
