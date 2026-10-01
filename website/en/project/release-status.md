# Release status

**Published SDK:** [v0.5.0-beta.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.5.0-beta.1), compiler `0.5.0-beta.1`, language version `0.8-dev`, JDK17+, Apache-2.0. This is an experimental Beta, not a production migration promise. Linux/macOS are release-supported; Windows remains a non-blocking preview.

The Beta SDK includes project testing, wrapper generation, schema-4 dependency locks, bounded and explicit Java/JVM interop, and first-party CLI, HTTP, JSON, SQLite and Web packages. Check the release assets and `sprig capabilities --json` from the installed SDK for exact support. There is no central package registry, and Sprig is not self-hosted.

Downloaded-release ZIP validation across Linux/macOS × JDK 17/26, checksum evidence and the publish workflow are recorded in the [release validation report](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/validation.md).

v0.4 adds the canonical formatter, explicit module re-exports, expression
`match`, Unicode code-point string semantics, `sprig api` module/project
introspection and managed SDK upgrades on top of v0.3, plus an adversarial
correctness pass: inferred globals, generated type-name collisions, default
field effects, `finally` completion, CR layout, JVM source/bridge resolution,
indexed-assignment key/index checks, nullable scalar equality and installer ZIP
hardening.

Linux/macOS × JDK17/26 passed source and the same actual tagged SDK ZIP gates.
Windows is an experimental, non-blocking preview. The SDK includes Maven
Resolver, locked project classpaths, explicit `@std` IO/JSON modules, SQLite/Web
libraries and three showcases.

See the [validation record](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/validation.md) for source SHA, archive SHA256,
commands, actual CI links and limits. Untagged source builds report development,
based on the published release; clean matching tagged builds report prerelease.
No JDK is bundled. Java SAM/arrays/general generic adapters, LSP/IDE and
self-hosting remain future work. Types do not prove numerical stability.
