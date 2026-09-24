# Release status

**Compiler candidate:** `0.1.0-alpha.1` (language design: Sprig v0.7)
**Repository:** <https://github.com/ColinHouse/Sprig>
**License:** Apache-2.0

## What is verified

- Built and tested from source on macOS Apple Silicon with OpenJDK 26.0.1,
  OpenJDK 17.0.19 and Python 3.14.6; hosted CI runs the same suite on Linux
  for JDK 17 and 26.
- `scripts/build.sh`, `scripts/test.sh` and the grammar smoke harness pass on
  those environments, and the documented snippets are executed by
  `tools/verify-doc-snippets.py`.
- The compiler source is compiled with `javac --release 17` (classfile version
  61) and runs on JDK 17 or newer.
- Tests, diagnostics and documentation examples are run from the working tree;
  the numbers quoted in the README and this site come from those runs.

## Releases

**No release has been published yet.** When the first one is ready, prereleases
are published from version tags by
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

See [Known limitations](/en/reference/KNOWN_LIMITATIONS) and the
[stage-1 roadmap](/en/reference/STAGE1_ROADMAP).
