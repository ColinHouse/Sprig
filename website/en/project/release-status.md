# Release status

**Published SDK:** [v0.4.0-alpha.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.4.0-alpha.1), compiler `0.4.0-alpha.1`, JDK17+, Apache-2.0. Experimental Alpha, not production-ready.

v0.4 adds the canonical formatter, explicit module re-exports, expression
`match`, Unicode code-point string semantics, `sprig api` module/project
introspection and managed SDK upgrades on top of v0.3, plus an adversarial
correctness pass: inferred globals, generated type-name collisions, default
field effects, `finally` completion, CR layout, JVM source/bridge resolution and
installer ZIP hardening.

Linux/macOS × JDK17/26 passed source and the same actual tagged SDK ZIP gates.
Windows is an experimental, non-blocking preview. The SDK includes Maven
Resolver, locked project classpaths, explicit `@std` IO/JSON modules, SQLite/Web
libraries and three showcases.

See the [validation record](https://github.com/ColinHouse/Sprig/blob/main/docs/RELEASE_VALIDATION.md) for source SHA, archive SHA256,
commands, actual CI links and limits. Untagged source builds report development,
based on the published release; clean matching tagged builds report prerelease.
No JDK is bundled. Java SAM/arrays/general generic adapters, LSP/IDE and
self-hosting remain future work. Types do not prove numerical stability.
