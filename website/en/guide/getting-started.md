# Getting Started

This page explains how to get the stage-0 compiler and run a first Sprig
program.

## Install

**Option A — download the alpha archive** (compiler, runtime, ANTLR and
launcher; no JDK included):

```bash
curl -LO https://github.com/ColinHouse/Sprig/releases/download/v0.1.0-alpha.1/sprig-v0.1.0-alpha.1-jdk.zip
curl -LO https://github.com/ColinHouse/Sprig/releases/download/v0.1.0-alpha.1/sprig-v0.1.0-alpha.1-jdk.zip.sha256
shasum -a 256 -c sprig-v0.1.0-alpha.1-jdk.zip.sha256
unzip sprig-v0.1.0-alpha.1-jdk.zip
cd sprig-v0.1.0-alpha.1-jdk
./bin/sprig run examples/hello.spr
```

**Option B — build from source**, described below.

## Requirements

| Tool | Version | Why |
|---|---|---|
| JDK | 17 or newer | The compiler builds with `javac --release 17` and has been run end-to-end on OpenJDK 17.0.19 and 26.0.1 (macOS Apple Silicon); hosted CI exercises both on Linux. |
| Python | 3.12 or newer | Test and acceptance scripts. |
| `curl` | any | The first build downloads the pinned ANTLR 4.13.2 tool JAR if it is missing. |
| Node.js | 20 or newer | Only needed to build this documentation site. |

## Build the compiler

```bash
./scripts/build.sh
```

The script downloads `antlr-4.13.2-complete.jar` from Maven Central when
`tools/antlr-4.13.2-complete.jar` is absent, verifies its SHA-256 digest,
regenerates the parser from `grammar/`, compiles `compiler/` and `runtime/`
with `javac --release 17`, and writes `bin/sprig`.

```text
Generating ANTLR4 parser...
Compiling compiler + runtime...
Built Sprig stage-0 compiler.
  launcher:  .../bin/sprig
  classes:   .../build/classes
  compiler:  .../build/sprig-compiler.jar
```

## Run your first program

<<< @/../examples/hello.spr

```bash
./bin/sprig run examples/hello.spr
```

```text
Hello, Ada!
```

`run` type-checks the file, generates Java source, compiles it with `javac`
and executes the result on the JVM.

## Command line

```text
Usage: sprig <command> [options]

  check <file.spr> [--json] [--syntax-only]   parse and type-check
  run   <file.spr> [--json] [--keep] [-- a b] compile and execute on the JVM
  build <file.spr> [-d dir] [--json]          emit Java sources + .class files
  explain <SPR-CODE>                          explain a diagnostic code
  codes [--json]                              list every diagnostic code
  version
```

| Command | What it does |
|---|---|
| `check` | Runs the lexer, layout adapter, parser, name resolution and type checking. Writes nothing. |
| `run` | Does everything `check` does, then emits Java, invokes `javac` and runs the program. |
| `build` | Emits generated Java sources and `.class` files under `-d <dir>` (default `build/out`). |
| `explain` | Prints the meaning of a stable diagnostic code such as `SPR-MATCH-NONEXHAUSTIVE`. |
| `codes` | Lists every diagnostic code. |
| `--json` | Wraps the result in a single machine-readable JSON document. |
| `--syntax-only` | Stops after lexing, layout and parsing. |

See [Tooling and JSON](/en/guide/tooling) for the JSON envelope and stable error
codes.

## Run the project tests

```bash
./scripts/test.sh
```

This runs syntax positives and negatives, 46 semantic expectation cases,
runtime programs with golden stdout, visitor programs, examples, numeric
boundary tests, parser recovery fuzzing, and the independent acceptance
matrices. The suite prints a final `N passed, 0 failed` line and exits non-zero
on any failure.

An independent grammar smoke harness is also available:

```bash
ANTLR_JAR="$PWD/tools/antlr-4.13.2-complete.jar" ./tools/test-grammar.sh
```

## Common first-run problems

- **`JDK required`** — install JDK 17 or newer and make sure `java` and
  `javac` are on `PATH`.
- **Checksum mismatch for ANTLR** — delete
  `tools/antlr-4.13.2-complete.jar` and rebuild so it is downloaded again.
- **`SPR-LEX-TAB`** — Sprig indentation uses spaces, never tabs.
- **A Java reference result is reported as nullable** — this is intentional.
  Narrow it with a `!= null` check before calling methods on it.
