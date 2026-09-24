# Release status

**Compiler candidate:** `0.1.0-alpha.1` (language design: Sprig v0.7)
**Status:** prepared and tested locally; **not released**.

## What is verified

- Built and tested from source on macOS Apple Silicon with OpenJDK 26.0.1 and
  Python 3.14.6.
- `scripts/build.sh`, `scripts/test.sh` and the grammar smoke harness pass on
  that environment.
- The compiler source is compiled with `javac --release 17` (classfile version
  61), but a Java 17 **runtime** run has not been observed. The only runtime
  requirement that has been exercised is JDK 26 or newer.
- Tests, diagnostics and documentation examples are run from the working tree;
  the numbers quoted in the README and this site come from those runs.

## What is not verified

- CI is configured in `.github/workflows/ci.yml` for Temurin 17 and 26, but it
  has not run on GitHub because no public repository exists yet.
- The GitHub Pages deployment workflow is prepared but has not been executed.
  This site is not claimed to be deployed at any address.
- No tag, GitHub Release or binary archive has been published.
- Only macOS Apple Silicon has been exercised locally; no other platform or
  architecture has been tested.

## Blocking decisions

1. **Project license.** No license has been selected for the compiler, runtime,
   documentation, examples or generated artifacts. Public redistribution is not
   cleared until the owner records one. See
   [License status](/project/license-status).
2. **Repository destination.** The owner must confirm the public repository
   owner/name and the intended Pages address (project site, user site or custom
   domain) before deployment.
3. **Hosted checks.** The configured CI and Pages workflows should run on the
   accepted commit before a release is announced.

## Not part of this release

Stage-1 self-hosting, a package manager, a language server, a standard
library distribution and full Java generics/array interop are **not**
implemented. See [Known limitations](/reference/KNOWN_LIMITATIONS) and the
[stage-1 roadmap](/reference/STAGE1_ROADMAP).
