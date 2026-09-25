# Sprig v0.1.0-alpha.2 development notes

This file describes the alpha.2 development tree. It is **not** evidence that
an alpha.2 GitHub release or tag has been published. The latest published
prerelease is v0.1.0-alpha.1.

- Versioned offline `help <topic> --json` and `capabilities --json`.
- `api <Java.Class> --json` reads reflection metadata without initializing
  the class; output includes mappings, overloads, generic signatures, checked
  exceptions, nullable result policy, and unavailable array/varargs members.
- `check`, `build`, `run`, and `api` accept the same explicit local classpath.
- `doctor --json` and structured `explain CODE --json` support repair loops.
- The archive includes an agent guide, implemented quick reference, JVM
  interop guide, diagnostics, numeric contract, feature status, and examples.
- Java `Object` parameters no longer receive a special nullable exception.
- The grammar and language design version remain unchanged (Sprig v0.7).

Maintainer gate results and blind-test raw evidence remain in the repository at
`docs/releases/ALPHA2_VALIDATION.md` and `acceptance/`; they are intentionally
excluded from the user SDK archive.
