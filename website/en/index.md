---
layout: home

hero:
  name: Sprig
  text: A statically typed language for the JVM
  tagline: Sealed variants, exhaustive match, checked numerics and explicit JVM interop — implemented today by a Java stage-0 compiler.
  image:
    src: /logo-round.png
    alt: Sprig mascot
  actions:
    - theme: brand
      text: Get started
      link: /en/guide/getting-started
    - theme: alt
      text: Language tour
      link: /en/guide/language-tour
    - theme: alt
      text: GitHub
      link: https://github.com/ColinHouse/Sprig

features:
  - title: One canonical syntax
    details: Indentation for blocks, func for named functions, named constructors for classes and variant cases, positional calls for functions and JVM methods. No aliases, no pipeline operator.
  - title: Static, honest types
    details: Nullable T? with flow narrowing, distinct immutable and mutable collections, no implicit precision-losing numeric conversion, no truthiness, and no Any escape hatch.
  - title: Sealed variants and exhaustive match
    details: variant declares a closed sum type with immutable named fields. match is checked for missing, duplicate and wrong cases, so growing an AST breaks every visitor that misses it.
  - title: Checked numerics and explicit interop
    details: Int/Int32 overflow raises an error instead of wrapping and integer division is explicit; BigInt/Decimal are exact. Java reference results are nullable and must be narrowed before use.
---

## Latest published release: `0.2.0-alpha.1`

Sprig language `v0.8-dev`; JDK 17+. This is the second public prerelease.

Sprig is experimental. The compiler is a Java stage-0 implementation that parses
`.spr` source, checks it, emits Java source, invokes `javac` and runs the JVM.
It is **not self-hosted**. Local/Git packages are supported; Maven resolution,
publishing/registry, LSP/IDE and a standard-library distribution are not implemented.

::: info Release status
**`v0.2.0-alpha.1` is published as a prerelease.** Download the ZIP and
`.sha256` checksum from the
[releases page](https://github.com/ColinHouse/Sprig/releases/tag/v0.2.0-alpha.1)
(compiler, runtime, ANTLR and launcher; **no JDK included**), or build from
source with [Getting Started](/en/guide/getting-started). This is an alpha,
not a stable release; see [Release status](/en/project/release-status) for
what was verified.
:::

## Your first program

<<< @/../examples/hello.spr

```bash
git clone https://github.com/ColinHouse/Sprig.git
cd Sprig
./scripts/build.sh
./bin/sprig run examples/hello.spr
```

```text
Hello, Ada!
```

JDK 17 or newer is required; the first build downloads ANTLR 4.13.2 from Maven
Central and verifies a pinned SHA-256 digest. More commands are in
[Getting Started](/en/guide/getting-started) and
[Tooling and JSON](/en/guide/tooling).

## Where to go next

- [Getting Started](/en/guide/getting-started): requirements, build, Hello World and the CLI.
- [Language Tour](/en/guide/language-tour): bindings, functions, classes, variants, collections, nullability and errors.
- [Examples](/en/examples): programs actually executed by the current compiler.
- [Implemented features](/en/reference/FEATURE_STATUS_IMPLEMENTED) and [Known limitations](/en/reference/KNOWN_LIMITATIONS).
- [Language spec (v0.7 design)](/en/reference/LANGUAGE_SPEC) and [Numerical semantics](/en/reference/NUMERIC_SEMANTICS).
- [Release status](/en/project/release-status): what has and has not been verified.

## Project

Source and issues: <https://github.com/ColinHouse/Sprig>. Licensed under
Apache-2.0. AI-assisted contributions are welcome but must pass the build,
tests and review, and state what the contributor verified — see
[Contributing](/en/project/contributing) and
[AI-assisted development](/en/project/ai-disclosure).

A Chinese version of this documentation is available: [简体中文](/).
