# Release status

The current release is [v0.6.0-beta.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.6.0-beta.1).

| | |
|---|---|
| Compiler | `0.6.0-beta.1` |
| Language version | `0.8-dev` |
| Requires | JDK 17 or newer (the SDK doesn't include a JDK) |
| License | Apache-2.0 |
| Platforms | Linux and macOS are supported; Windows is an experimental preview |

This is an experimental Beta. It's for trying Sprig out and reporting problems; don't move production projects to it yet.

## What's new in this release

- A language server, `sprig lsp`, for Neovim, Helix and other editors; the [VS Code extension](/en/guide/editor) starts it for you
- `requires T: Comparable`, so generic code can compare and sort values
- New standard library modules: `@std/lists`, `@std/nulls`, `@std/math` and `@std/json_codec`, plus padding helpers in `@std/text`, assertions in `@std/test` that print both values, and standard input, standard error and exit status in `@std/process`
- In a condition like `if x != null and ...`, the right side of `and` can treat `x` as non-null
- A Gradle plugin and a Fabric template; see [Gradle integration](/en/guide/gradle)
- Lock format version 5, which no longer records `@std` in the lock file
- A round of correctness fixes: error positions, names that are keywords in Java, and the order top-level code runs in
- Faster commands, because the compiler JVM starts with only the C1 JIT

Everything from v0.5 is still there: projects with local, Git and Maven dependencies, `sprig test`, `sprig wrap`, explicit Java interop, and the official command-line, HTTP, JSON, SQLite and Web libraries.

There's no central package registry yet, and the compiler isn't written in Sprig itself (it isn't self-hosted). For exactly what your installed SDK supports, run `sprig capabilities --json`. The full story is in the [v0.6.0-beta.1 release notes](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/v0.6.0-beta.1.md).

## Upgrading from v0.5

Run `sprig upgrade`, then run `sprig resolve` once in each project. Lock files written by v0.5.0-beta.1 use format version 4, and the new compiler asks you to resolve again instead of reading them. A few programs that v0.5 accepted are now rejected, such as top-level code that uses a variable declared further down; the error tells you what to change.

## Changes since the release

This website follows the source on the repository's `main` branch, which can be ahead of the release. When a page describes something newer than v0.6.0-beta.1, it says so.

## How it was verified

The release's SDK archive passed acceptance checks on Linux and macOS with JDK 17 and 26, including checksum verification and the publishing workflow. The [release validation record](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/validation.md) lists the source revision, the archive's SHA-256, the commands run and links to the CI runs.

A build from source without a tag reports itself as development; a build that exactly matches a clean tag reports prerelease.

## Still planned

- adapters for Java functional interfaces (SAM), arrays and full generics
- a compiler written in Sprig itself (self-hosting)

Also note that type checking doesn't prove numerical stability.

## Earlier releases

v0.5 was the first Beta. It added projects with local, Git and Maven dependencies, `sprig test`, `sprig wrap`, explicit Java interop, and the official command-line, HTTP, JSON, SQLite and Web libraries. v0.4 built on v0.3 with the code formatter, explicit module re-exports, expression `match`, Unicode code-point string semantics, module and project inspection through `sprig api`, and managed SDK upgrades, along with a round of correctness fixes. Notes for every release are in [`docs/releases/`](https://github.com/ColinHouse/Sprig/tree/main/docs/releases).
