# Sprig v0.1.0-alpha.1

**Published 2026-09-24 as a GitHub prerelease:**
<https://github.com/ColinHouse/Sprig/releases/tag/v0.1.0-alpha.1>

The first public alpha of the Sprig Java stage-0 compiler. Sprig source is
parsed and checked, lowered to Java source, compiled with `javac`, and run on
the JVM. The language design remains Sprig v0.7.

- Repository: <https://github.com/ColinHouse/Sprig>
- Documentation: <https://colinhouse.github.io/Sprig/>
- License: Apache-2.0 (`LICENSE`, `NOTICE`)

## Download

| Asset | Notes |
|---|---|
| `sprig-v0.1.0-alpha.1-jdk.zip` | Compiler/runtime, ANTLR 4.13.2, launcher, Hello World, license and notices. No JDK included. |
| `sprig-v0.1.0-alpha.1-jdk.zip.sha256` | SHA-256 checksum of the archive. |

Verified checksum of the published archive:

```text
fccc87e0d92c864672824d42035cbc9b1486da430166f744325db6b8def436db  sprig-v0.1.0-alpha.1-jdk.zip
```

The archive was downloaded from the release again and smoke-tested
(`bin/sprig version`, `check`, `run`); its `BUILD_INFO.txt` records source
revision `6e7b57e`. Extract it and run `bin/sprig run examples/hello.spr` with
JDK 17 or newer on `PATH`.

## Included and exercised

- Indentation-based syntax, typed functions, local inference, classes, enums,
  sealed variants, and exhaustive `match` statements.
- `let`/`var`, nullable types and flow narrowing, typed error handling,
  modules, and separate immutable/mutable collections.
- Checked fixed-width integer operations, explicit numeric conversions,
  `BigInt`, `Decimal`, IEEE `Float`/`Float32`, and static diagnostics for
  ambiguous numeric operations.
- A command line for `check`, `build`, `run`, diagnostic explanations, and
  JSON diagnostics.
- Regression coverage for parser recovery, zero-argument lambdas, contextual
  `Int32` compound assignments, and nullable values passed to Java references.
- A VitePress documentation site built in the same repository.

## Runtime and build requirements

The source is compiled with `javac --release 17` and verified end-to-end on
OpenJDK 17.0.19 and 26.0.1 (macOS Apple Silicon); hosted CI covers Linux for
both JDKs. The supported runtime is JDK 17 or newer. The source archive
contains the compiler/runtime, ANTLR 4.13.2 and its license notices; it does
not contain a JDK.

## Known limits

This is an alpha compiler, not a production stability or numerical correctness
claim. It is not self-hosted and has no package manager, language server, IDE
plugin, complete standard library, or unrestricted Java interop. See
`docs/KNOWN_LIMITATIONS.md` and `docs/NUMERIC_SEMANTICS.md`.

Report reproducible problems through the repository issue tracker with the
Sprig version, JDK/platform, minimal source, exact command, and diagnostics.
