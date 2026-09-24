---
layout: home

hero:
  name: Sprig
  text: A small, statically typed language for the JVM
  tagline: An indentation-based language with sealed variants, exhaustive match, checked numerics and deliberate JVM interop. Implemented today by a Java stage-0 compiler.
  image:
    src: /logo.png
    alt: Sprig mascot
  actions:
    - theme: brand
      text: Get started
      link: /guide/getting-started
    - theme: alt
      text: Language tour
      link: /guide/language-tour
    - theme: alt
      text: Feature status
      link: /reference/FEATURE_STATUS_IMPLEMENTED

features:
  - title: One canonical syntax
    details: Indentation for blocks, func for named functions, named constructors for classes and variant cases, positional calls for functions and JVM methods. No aliases, no pipeline operator.
  - title: Static, honest types
    details: Nullable T?, flow narrowing, distinct immutable and mutable collections, no implicit numeric promotion, no truthiness, and no Any escape hatch.
  - title: Sealed variants and exhaustive match
    details: variant defines a closed sum type with immutable named fields. match is checked for missing, duplicate and wrong cases, so growing an AST breaks every visitor that needs updating.
  - title: Checked numbers
    details: Int and Int32 overflow raise runtime errors instead of wrapping. Integer division is explicit, Decimal and BigInt are exact, and Float stays IEEE 754.
  - title: JVM interop, made explicit
    details: Import Java classes with an alias, call constructors, fields and overloads. Java reference results are nullable and must be checked before use.
  - title: Built for agent-assisted work
    details: Stable diagnostic codes, a JSON result envelope for check, build and run, and a repository designed so generated changes must pass real build and test gates.
---

## Current stage: 0.1.0-alpha.1

Sprig is experimental. The compiler is a Java stage-0 implementation that parses
`.spr` source, checks it, emits Java source, invokes `javac` and runs the JVM.
It is **not self-hosted**, has **no package manager, language server or IDE
plugin**, and its runtime library is still growing.

::: info Project
Source and issues: [github.com/ColinHouse/Sprig](https://github.com/ColinHouse/Sprig).
Licensed under **Apache-2.0**. Alpha releases are published on the
[releases page](https://github.com/ColinHouse/Sprig/releases); see
[Release status](/project/release-status) for what has actually been verified.
:::

## Your first program

<<< @/../examples/hello.spr

```bash
./scripts/build.sh
./bin/sprig run examples/hello.spr
```

```text
Hello, Ada!
```

Continue with [Getting Started](/guide/getting-started) for build
requirements and the full command line, or go straight to the
[Language Tour](/guide/language-tour).

## What you can rely on today

The [implemented feature status](/reference/FEATURE_STATUS_IMPLEMENTED) is the
authoritative list, maintained next to the tests that verify it. In short:
typed functions, classes, enums, sealed variants with exhaustive `match`,
nullable types and narrowing, typed `throws`/`catch`, modules, immutable and
mutable collections, checked fixed-width integers, `BigInt`, `Decimal`,
IEEE floats, and common JVM interop.

Everything else — user-defined generics, inheritance, `match` expressions,
arrays and varargs, a standard library and stage-1 self-hosting — is **not yet
available**. The [known limitations](/reference/KNOWN_LIMITATIONS) page says so
explicitly.
