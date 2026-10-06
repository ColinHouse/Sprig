# Release status

The current release is [v0.7.0-beta.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.7.0-beta.1).

| | |
|---|---|
| Compiler | `0.7.0-beta.1` |
| Language version | `0.8-dev` |
| Requires | JDK 17 or newer (the SDK doesn't include a JDK) |
| License | Apache-2.0 |
| Platforms | Linux and macOS are supported; Windows is an experimental preview |

This is an experimental Beta. It's for trying Sprig out and reporting problems; don't move production projects to it yet.

## What's new in this release

- Lambdas can call functions that throw `Error`, function types can say `throws Error`, and `rethrows` lets a helper such as `lists.sort_by` throw exactly what the function you pass it throws
- You can pass a Sprig function where Java expects a callback (`list.sort`, `forEach`, `removeIf`), and call Java varargs methods such as `Path.of("a", "b")` and `String.format`
- New standard library modules `@std/sets`, `@std/random`, `@std/regex` and `@std/dates`; a test runner in `@std/test` that reports every failing check; `process.run` for running other programs; and more helpers in `@std/lists`, `@std/text`, `@std/files` and `@std/time`
- Errors written for someone new to Sprig: habits from Python, Java or C, like `else if`, `readLine()` or `List<Int>`, get the Sprig spelling, and `sprig help language` is a complete small program you can run
- `@std/files` reports every failure as an `Error` such as `cannot read data/x.txt: no such file`
- New checks catch a `catch` that can never run, a `throws` that can never happen, and a `Unit` result used as a value
- `sprig check --bin` and `sprig build --bin`, and `sprig check` with no file checks every bin of a project

Everything from v0.6 is still there: the language server, `requires T: Comparable`, projects with local, Git and Maven dependencies, `sprig test`, `sprig wrap`, the Gradle plugin and Fabric template, and the official command-line, HTTP, JSON, SQLite and Web libraries.

There's no central package registry yet, and the compiler isn't written in Sprig itself (it isn't self-hosted). For exactly what your installed SDK supports, run `sprig capabilities --json`. The full story is in the [v0.7.0-beta.1 release notes](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/v0.7.0-beta.1.md).

## Upgrading from v0.6

Run `sprig upgrade`, then run `sprig resolve` once in each project, because a lock file records the compiler that wrote it.

A few programs that v0.6 accepted are now rejected, and the error tells you what to change:

- `catch problem: IOException` around a `@std/files` call doesn't compile anymore; catch `Error` instead
- a `catch` or `throws` for a checked Java exception that can never happen is an error; remove it
- `rethrows` is now a keyword, so a variable or function with that name needs a new name

## Changes since the release

This website follows the source on the repository's `main` branch, which can be ahead of the release. When a page describes something newer than v0.7.0-beta.1, it says so.

## How it was verified

The release's SDK archive passed acceptance checks on Linux and macOS with JDK 17 and 26, including checksum verification and the publishing workflow. The [release validation record](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/validation.md) lists the source revision, the archive's SHA-256, the commands run and links to the CI runs.

A build from source without a tag reports itself as development; a build that exactly matches a clean tag reports prerelease.

## Still planned

- Java arrays as values you can index, and full Java generics
- a compiler written in Sprig itself (self-hosting)

Also note that type checking doesn't prove numerical stability.

## Earlier releases

v0.6 added the language server, `requires T: Comparable`, the `@std/lists`, `@std/nulls`, `@std/math` and `@std/json_codec` modules, the Gradle plugin and Fabric template, and lock format version 5. v0.5 was the first Beta, with projects with local, Git and Maven dependencies, `sprig test`, `sprig wrap`, explicit Java interop, and the official command-line, HTTP, JSON, SQLite and Web libraries. v0.4 built on v0.3 with the code formatter, explicit module re-exports, expression `match`, Unicode code-point string semantics, module and project inspection through `sprig api`, and managed SDK upgrades, along with a round of correctness fixes. Notes for every release are in [`docs/releases/`](https://github.com/ColinHouse/Sprig/tree/main/docs/releases).
