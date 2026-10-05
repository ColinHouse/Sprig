# Release status

The current release is [v0.5.0-beta.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.5.0-beta.1).

| | |
|---|---|
| Compiler | `0.5.0-beta.1` |
| Language version | `0.8-dev` |
| Requires | JDK 17 or newer (the SDK doesn't include a JDK) |
| License | Apache-2.0 |
| Platforms | Linux and macOS are supported; Windows is an experimental preview |

This is an experimental Beta. It's for trying Sprig out and reporting problems; don't move production projects to it yet.

## What's in this release

- Projects and dependencies: `sprig add`, `resolve` and `deps`, with local, Git and Maven dependencies (lock format version 4)
- Project tests: `sprig test`
- Sprig wrappers for Java classes: `sprig wrap`
- Java interop with clear, explicit rules
- Official command-line, HTTP, JSON, SQLite and Web libraries
- The `@std` library: files, processes, text, time, JSON and test helpers
- Three larger example programs

There's no central package registry yet, and the compiler isn't written in Sprig itself (it isn't self-hosted). For exactly what your installed SDK supports, run `sprig capabilities --json`. The full story is in the [v0.5.0-beta.1 release notes](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/v0.5.0-beta.1.md).

## Changes since the release

The source on the repository's `main` branch is newer than this release, and this website describes the latest source. That means a few things differ from the published SDK:

- a new Gradle plugin and Fabric template; see [Gradle integration](/en/guide/gradle)
- lock format version 5, which no longer records `@std` in the lock file
- a new `@std/math` module
- in a condition like `if x != null and ...`, the right side of `and` can treat `x` as non-null
- clearer wording in some error hints, with the same error codes

To use these changes now, build from source; see "Windows and building from source" in [Getting started](/en/guide/getting-started).

## How it was verified

The release's SDK archive passed acceptance checks on Linux and macOS with JDK 17 and 26, including checksum verification and the publishing workflow. The [release validation record](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/validation.md) lists the source revision, the archive's SHA-256, the commands run and links to the CI runs.

A build from source without a tag reports itself as development; a build that exactly matches a clean tag reports prerelease.

## Still planned

- adapters for Java functional interfaces (SAM), arrays and full generics
- a language server (LSP) and IDE support
- a compiler written in Sprig itself (self-hosting)

Also note that type checking doesn't prove numerical stability.

## Earlier releases

v0.4 built on v0.3 with the code formatter, explicit module re-exports, expression `match`, Unicode code-point string semantics, module and project inspection through `sprig api`, and managed SDK upgrades, along with a round of correctness fixes. Notes for every release are in [`docs/releases/`](https://github.com/ColinHouse/Sprig/tree/main/docs/releases).
