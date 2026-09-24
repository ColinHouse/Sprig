# Release status

**Compiler candidate:** `0.1.0-alpha.1` (language design: Sprig v0.7)
**Repository:** <https://github.com/ColinHouse/Sprig>
**License:** Apache-2.0

## What is verified

- Built and tested from source on macOS Apple Silicon with OpenJDK 26.0.1 and
  Python 3.14.6; hosted CI runs the same suite on Linux.
- `scripts/build.sh`, `scripts/test.sh` and the grammar smoke harness pass on
  that environment, and the documented snippets are executed by
  `tools/verify-doc-snippets.py`.
- The compiler source is compiled with `javac --release 17` (classfile version
  61), but a Java 17 **runtime** run has not been observed. The documented
  runtime requirement is JDK 26 or newer.
- Tests, diagnostics and documentation examples are run from the working tree;
  the numbers quoted in the README and this site come from those runs.

## Releases

Prereleases are published from version tags by
`.github/workflows/release.yml`, which rebuilds the compiler, runs the test
suite, packages the archive with `scripts/package-alpha.sh`, and attaches the
ZIP and its SHA-256 sidecar. The release notes are in the repository under
`docs/releases/`.

An alpha is an early build: it is published so the project can be evaluated,
not because it is production-ready.

## What is not claimed

- Stage-1 self-hosting, a package manager, a language server, a standard
  library distribution and full Java generics/array interop are **not**
  implemented.
- Numerical results are not certified for algorithmic stability or physical
  units.
- No platform other than macOS Apple Silicon (local) and Linux (CI) has been
  exercised.

See [Known limitations](/reference/KNOWN_LIMITATIONS) and the
[stage-1 roadmap](/reference/STAGE1_ROADMAP).
