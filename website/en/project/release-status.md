# Release status

The current published SDK is [v0.9.0-beta.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.9.0-beta.1).

| Item | Current status |
|---|---|
| SDK / compiler | `0.9.0-beta.1` |
| Language | `0.8-dev`, not frozen yet |
| Runtime requirement | Separate JDK 21+; the SDK does not bundle a JDK |
| VS Code extension | `ColinHouse.sprig-language` on the Marketplace, version `0.3.1`; install the SDK separately |
| License | Apache-2.0 |
| Platforms | Linux and macOS are release-supported; Windows is an experimental preview |

This is an experimental Beta for trying Sprig and reporting problems, not for migrating production projects. The published SDK can run the current tutorial; building from source is optional. See [installation](/en/guide/getting-started) and the [extension guide](/en/guide/editor).

## What this release adds

These changes from v0.8.0-beta.1 are included in the v0.9.0-beta.1 SDK:

- Gradle and Fabric: a package of its own for each project's generated classes, `sprig.user.<project>`, and in Gradle builds the project's own copy of the runtime, so two Sprig mods work in one game.
- Compiler: building a long text with `text += part` in a function's loop takes time in proportion to the text. Using a deprecated Java API gives the `SPR-JVM-DEPRECATED` warning, and `sprig api` reports `deprecated` and `forRemoval`. Diagnostics about Java calls carry an `example` link into the new [Java interop cookbook](/en/guide/java-interop-cookbook).
- Libraries: `sqlite.transaction` and `transaction_run` in sprig-sqlite, and [sprig-csv](https://github.com/ColinHouse/Sprig/tree/main/libraries/sprig-csv), a new library that reads and writes CSV tables.
- Tools: `sprig build --emit-java-only --with-runtime`, and `sprig project` prints the generated package.

See the [v0.9.0-beta.1 notes](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/v0.9.0-beta.1.md) for the complete changes and limits. Query `sprig capabilities --json` for your installed SDK's actual features.

## Upgrade from v0.8.0

For a managed Linux/macOS installation, run `sprig upgrade`. Manually extracted SDKs, including Windows, need a new verified ZIP. Run `sprig resolve` inside projects after upgrading to update the lock's compiler identity.

Sprig source needs no change. What this MINOR upgrade asks for is in the Java code of Gradle projects, Fabric mods among them:

- Java that names a generated class adds the project's package: `sprig.user.$M_main` becomes `sprig.user.<project>.$M_main`. `javac` points at every place and `./gradlew sprigInfo` prints the package; see [Gradle integration](/en/guide/gradle#using-sprig-classes-from-java).
- The Gradle plugin and the compiler come from one SDK. A project that includes the plugin from `SPRIG_HOME/libraries/sprig-gradle`, as the template does, has nothing to do.
- See the release notes' [upgrade section](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/v0.9.0-beta.1.md#upgrade-from-v080-beta1) for the changes in tool output.

## Website, source and released SDK

The site follows `main`. The current tutorial's features are published; later source changes may precede the next SDK. `sprig version` identifies the SDK/compiler release, and `sprig capabilities --json` reports its language version and features. `languageVersion: 0.8-dev` does not mean that the SDK is unpublished.

## How it was verified

The release workflow passed the downloaded-SDK checks on Linux/macOS × JDK 21/26. A fresh local ZIP download on macOS/JDK 26.0.1 also passed SHA-256 verification and `init → resolve → run`, and `sprig upgrade` from v0.8.0-beta.1 worked in an isolated home directory. See the [release validation record](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/validation.md) for the source commit, digest, CI links and limits. The local smoke check does not imply manual Windows or complete editor-interaction coverage.

## Still planned

- Java arrays as indexable values, and full Java generics
- a compiler written in Sprig itself (self-hosting)

Type checking does not prove numerical stability or application correctness either.

## Earlier releases

v0.8 added `if` expressions, type arguments inferred from arguments, contract classes and `conform`, one-line classes, named function references, error classes, read-only views of mutable collections, structured scopes and virtual threads in `@std/concurrent` and `sprig build --bundle`, and raised the minimum JDK from 17 to 21. v0.7 let lambdas call functions that throw `Error`, added `throws Error` in function types and `rethrows`, Sprig functions as Java callbacks and Java varargs calls, the `@std/sets`, `@std/random`, `@std/regex` and `@std/dates` modules, a test runner in `@std/test`, `process.run`, and errors that give the Sprig spelling of habits from Python, Java or C. v0.6 added the language server, `requires T: Comparable`, the `@std/lists`, `@std/nulls`, `@std/math` and `@std/json_codec` modules, the Gradle plugin and Fabric template, and lock format version 5. v0.5 was the first Beta, with projects with local, Git and Maven dependencies, `sprig test`, `sprig wrap`, explicit Java interop, and the official command-line, HTTP, JSON, SQLite and Web libraries. v0.4 built on v0.3 with the code formatter, explicit module re-exports, expression `match`, Unicode code-point string semantics, module and project inspection through `sprig api`, and managed SDK upgrades, along with a round of correctness fixes. Notes for every release are in [`docs/releases/`](https://github.com/ColinHouse/Sprig/tree/main/docs/releases).
