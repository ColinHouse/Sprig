# Release status

The current release is [v0.7.1-beta.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.7.1-beta.1).

| | |
|---|---|
| Compiler | `0.7.1-beta.1` |
| Language version | `0.8-dev` |
| Requires | JDK 17 or newer (the SDK doesn't include a JDK) |
| License | Apache-2.0 |
| Platforms | Linux and macOS are supported; Windows is an experimental preview |

This is an experimental Beta. It's for trying Sprig out and reporting problems; don't move production projects to it yet.

## What's new in this release

v0.7.1-beta.1 fixes what an evaluation of v0.7.0-beta.1 found with real programs and Java baselines, and makes Java calls easier:

- `for i in range(n)` counts instead of building a list, so a loop of any length runs in constant memory
- top-level code with several hot loops runs as fast as the same Java
- joining a value that may be `null` into text is an error, instead of printing `null`
- an `Error` shows its message wherever it becomes text, `toString()` included
- `a.compareTo(b)` on Strings, which is what a Java `Comparator` wants
- an `Int` goes into an `int` Java parameter with a run-time range check, and wildcard types in Java signatures, such as `List<? extends Entity>`, are read at their bounds
- `sprig api` shows the comment written above each declaration
- help and `sprig capabilities` say what the compiler actually does, including that `==` compares two class objects by identity

It's a patch release: everything from v0.7.0-beta.1 is still there, and so is everything from v0.6. There's no central package registry yet, and the compiler isn't written in Sprig itself (it isn't self-hosted). For exactly what your installed SDK supports, run `sprig capabilities --json`. The full story is in the [v0.7.1-beta.1 release notes](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/v0.7.1-beta.1.md).

## Upgrading from v0.7.0

Run `sprig upgrade`, then run `sprig resolve` once in each project, because a lock file records the compiler that wrote it.

Two kinds of programs that v0.7.0 accepted are now rejected, because they could print `null` or fail at run time. The error tells you what to change:

- joining a value that may be `null` into a `String`: check it first, or give it a fallback with `or_else` from `@std/nulls.spr`. A value a Java method returns counts as possibly `null` too; a `toString()` result doesn't
- using a Java exception's `message` as a `String`: Java may leave it `null`, so it's a `String?` now. Join the exception itself, or check the message first

One program that still compiles prints something else: `toString()` on an `Error` now gives its message, without the `sprig.runtime.SprigError: ` prefix.

Coming from v0.6? Read the upgrade section of the [v0.7.0-beta.1 release notes](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/v0.7.0-beta.1.md) as well.

## Changes since the release

This website follows the source on the repository's `main` branch, which can be ahead of the release. When a page describes something newer than v0.7.1-beta.1, it says so. So far that's quick fixes in the language server.

## How it was verified

The release's SDK archive passed acceptance checks on Linux and macOS with JDK 17 and 26, including checksum verification and the publishing workflow. The [release validation record](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/validation.md) lists the source revision, the archive's SHA-256, the commands run and links to the CI runs.

A build from source without a tag reports itself as development; a build that exactly matches a clean tag reports prerelease.

## Still planned

- Java arrays as values you can index, and full Java generics
- a compiler written in Sprig itself (self-hosting)

Also note that type checking doesn't prove numerical stability.

## Earlier releases

v0.7 let lambdas call functions that throw `Error`, added `throws Error` in function types and `rethrows`, Sprig functions as Java callbacks and Java varargs calls, the `@std/sets`, `@std/random`, `@std/regex` and `@std/dates` modules, a test runner in `@std/test`, `process.run`, and errors that give the Sprig spelling of habits from Python, Java or C. v0.6 added the language server, `requires T: Comparable`, the `@std/lists`, `@std/nulls`, `@std/math` and `@std/json_codec` modules, the Gradle plugin and Fabric template, and lock format version 5. v0.5 was the first Beta, with projects with local, Git and Maven dependencies, `sprig test`, `sprig wrap`, explicit Java interop, and the official command-line, HTTP, JSON, SQLite and Web libraries. v0.4 built on v0.3 with the code formatter, explicit module re-exports, expression `match`, Unicode code-point string semantics, module and project inspection through `sprig api`, and managed SDK upgrades, along with a round of correctness fixes. Notes for every release are in [`docs/releases/`](https://github.com/ColinHouse/Sprig/tree/main/docs/releases).
