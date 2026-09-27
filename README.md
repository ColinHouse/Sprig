# Sprig

<p align="center">
  <img src="website/public/logo-round.png" alt="Sprig icon" width="170">
</p>

**A small, explicit JVM language for tools, automation and reliable application
code — designed for humans and coding agents to work from compiler feedback.**
Sprig is experimental, statically typed and indentation based. Alpha; JDK 17+;
language `0.8-dev`. It is implemented by a Java stage-0 compiler and is not self-hosted.

[简体中文](https://colinhouse.github.io/Sprig/) ·
[English docs](https://colinhouse.github.io/Sprig/en/) ·
[Releases](https://github.com/ColinHouse/Sprig/releases) ·
[Known limitations](docs/KNOWN_LIMITATIONS.md) ·
[Contribute](CONTRIBUTING.md)

[![CI](https://github.com/ColinHouse/Sprig/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/ColinHouse/Sprig/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/ColinHouse/Sprig?include_prereleases&label=release)](https://github.com/ColinHouse/Sprig/releases)
[![License](https://img.shields.io/github/license/ColinHouse/Sprig)](LICENSE)

## Why Sprig?

Build CLI utilities, repository automation, configuration tools, data transforms
and code analyzers. Readable code, sealed variants and exhaustive `match` fit
small tools and AST traversal; explicit types, nullability, effects and checked
numbers help make failures predictable. JVM interop gives those tools access
to existing Java libraries.

The compiler exposes stable diagnostic codes and JSON queries. A human or
agent can **query → edit → check → understand → repair** using compiler evidence:

```bash
sprig capabilities --json
sprig help generics --json
sprig api java.time.LocalDate --json
sprig check --json src/main.spr
sprig explain SPR-TYPE-NULLABLE --json
```

Sprig has no `Any`, truthiness or implicit precision-losing numeric conversions.
Generics are explicit and invariant. Java reference results require null
checks. These are implemented contracts; see
[feature status](docs/FEATURE_STATUS_IMPLEMENTED.md) and
[numeric semantics](docs/NUMERIC_SEMANTICS.md).

## Five-minute first project

Install **JDK 17+** with `java` and `javac` on `PATH`.
The current published SDK is [v0.3.0-alpha.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.3.0-alpha.1).
On Linux/macOS, install and upgrade it with the [managed installer](docs/INSTALL.md),
which verifies the release checksum. You can also download its ZIP and `.sha256`,
verify the checksum, and extract manually. The SDK includes compiler/runtime libraries, **not a JDK**. Supported release platforms:
**Linux/macOS**. **Windows is experimental**, with a separate non-blocking
preview workflow. Source checkouts report development metadata; clean tagged
artifacts report prerelease. See the [validation record](https://github.com/ColinHouse/Sprig/blob/main/docs/RELEASE_VALIDATION.md) for actual
source/archive evidence.

Linux/macOS, inside the extracted SDK:

```bash
./bin/sprig version
SPRIG="$(pwd)/bin/sprig"
"$SPRIG" init my-tool
cd my-tool
"$SPRIG" resolve
"$SPRIG" run
```

Expected program output: `Hello, Sprig!`. You now have `sprig.toml`,
`src/main.spr` and a generated `sprig.lock`. Edit the source, check it, run it.
The v0.3 SDK supports local/Git and Maven dependencies, explicit @std imports
and three tested showcase projects.

For the current source milestone (JDK 17+, Python 3.12+, Git):

```bash
git clone https://github.com/ColinHouse/Sprig.git
cd Sprig
python3 scripts/build.py
./bin/sprig version
```

On **Windows PowerShell**, the native launcher is an **experimental preview**;
Windows is not currently a supported release platform:

```powershell
py -3 scripts/build.py
$Sprig = (Resolve-Path .\bin\sprig.cmd).Path
& $Sprig version
& $Sprig init my-tool
Set-Location my-tool
& $Sprig resolve
& $Sprig run
```

See the [step-by-step guide](website/en/guide/getting-started.md) for checksum
commands, troubleshooting and the compiler query loop. The first source build
downloads pinned ANTLR and Maven Resolver libraries; Maven CLI is not required.

## Build something useful

The SDK includes three project-oriented showcases:

- **[Repository auditor](examples/showcases/repository_audit/README.md)** — a
  multi-module CLI that walks a tree, counts source/text files and writes JSON.
- **[Maven library application](examples/showcases/maven_slug/README.md)** —
  resolve a real Java library, query its API, run and reuse its locked cache offline.
- **[Source analyzer](examples/showcases/source_analyzer/README.md)** —
  Sprig frontend tooling with variants and exhaustive traversal.

Their READMEs specify real inputs and commands. All three were executed from
the published SDK; exact artifact and platform evidence is in the validation
record. Smaller [examples](examples/README.md) and
[language tour](website/en/guide/language-tour.md) teach individual constructs.

### Web + SQLite development examples

The current checkout adds [mini-web](examples/mini_web/README.md),
[persistent SQLite](examples/sqlite/README.md), and a
[reduced ledger backend](examples/ledger/README.md). Routing, typed handlers,
JSON and explicit OpenAPI schemas are Sprig; small JVM adapters own HTTP and JDBC.
See the [web API](libraries/sprig-web/README.md),
[SQLite API](libraries/sprig-sqlite/README.md) and
[engineering evidence](docs/milestones/WEB_SQLITE_ENGINEERING_REPORT.md).
The current checkout also has transactional SQLite migration support and a CLI
option parsing library; see [migration example](examples/sqlite_migrations/README.md),
[JSON CLI example](examples/json_select/README.md) and their library READMEs.
These additions are development work, not a newly published release.

## Contribute with your coding agent

**Want to contribute with Codex / Claude / ChatGPT?** Pick a scoped
[`agent-friendly` issue](https://github.com/ColinHouse/Sprig/issues?q=is%3Aissue+is%3Aopen+label%3Aagent-friendly),
let your agent read [AGENTS.md](AGENTS.md), review the patch, and open a PR.
Compiler expertise is useful; docs, regression fixtures and tooling also matter.

```bash
./scripts/verify.sh
# Windows: py -3 scripts/verify.py
```

This is the normal contributor gate (JDK/Python/Node/npm/Git): build, full
compiler/JVM tests, independent grammar tests, executed docs, website build and editor checks.
[CONTRIBUTING.md](CONTRIBUTING.md) explains scoped checks and release gates.
AI assistance is welcome; the submitter owns review, tests, licensing and
correctness. See [AI_DISCLOSURE.md](AI_DISCLOSURE.md).

## VS Code extension (local preview)

[Sprig for VS Code](editors/vscode/README.md) provides `.spr` syntax highlighting,
saved-file diagnostics, Run and Show Generated Java. Build a locally installable
VSIX with `npm ci && npm run package` in `editors/vscode/`. The SDK/JDK are
installed separately; this extension is not yet on Marketplace.

## Boundaries and source of truth

Sprig is an Alpha project, not a production migration promise. Publishing,
registry, LSP, interfaces/traits, generic inference, arrays and
stage-1 self-hosting remain future work. JVM generics and annotations have
interop limits. Consult the checkout's `capabilities --json` and
[known limitations](docs/KNOWN_LIMITATIONS.md).

`grammar/`, `compiler/`, and `runtime/` define implemented behavior.
`docs/` records that behavior; `spec/` contains the older design kit.
`website/` is the bilingual VitePress site (`npm ci && npm run docs:dev` there).
Generated `bin/`, `build/` and website outputs are not committed.

## License

[Apache-2.0](LICENSE). See [NOTICE](NOTICE),
[third-party notices](THIRD_PARTY_NOTICES.md) and [license scope](LICENSE_STATUS.md).

## 中文简介

Sprig 是一门小型、显式、静态类型的 JVM 语言，面向命令行工具、自动化、代码分析
和可靠的应用代码。人与编码 Agent 可以查询编译器、修改、检查、理解诊断并修复，
无需猜测语言规则。缩进式语法、封闭 variant、穷尽 match、显式泛型与受检数值是
现有基础；当前仍是实验性 Alpha，需要 JDK 17+，语言版本为 `0.8-dev`，尚未自举。

[快速开始](https://colinhouse.github.io/Sprig/guide/getting-started) ·
[参与贡献](CONTRIBUTING.md)。欢迎使用 Codex / Claude / ChatGPT 完成有验收条件的
小任务：读 `AGENTS.md`，运行 `scripts/verify.sh`（Windows：
`py -3 scripts/verify.py`），人工审查，再提交 PR。公开发行包与源代码里程碑的
功能范围请分别查阅 Releases 和 capability 输出。
