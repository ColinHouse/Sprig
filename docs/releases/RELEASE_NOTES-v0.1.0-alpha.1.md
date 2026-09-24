# Sprig v0.1.0-alpha.1

The first public alpha of the Sprig Java stage-0 compiler. Sprig source is
parsed and checked, lowered to Java source, compiled with `javac`, and run on
the JVM. The language design remains Sprig v0.7.

- Repository: <https://github.com/ColinHouse/Sprig>
- Documentation: <https://colinhouse.github.io/Sprig/>
- License: Apache-2.0 (`LICENSE`, `NOTICE`)

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

The source is compiled with `javac --release 17`; the only runtime exercised so
far is OpenJDK 26.0.1 on macOS Apple Silicon, and hosted CI covers Linux. The
documented runtime requirement is JDK 26 or newer until a Java 17 runtime run
is observed. The source archive contains the compiler/runtime, ANTLR 4.13.2
and its license notices; it does not contain a JDK.

## Known limits

This is an alpha compiler, not a production stability or numerical correctness
claim. It is not self-hosted and has no package manager, language server, IDE
plugin, complete standard library, or unrestricted Java interop. See
`docs/KNOWN_LIMITATIONS.md` and `docs/NUMERIC_SEMANTICS.md`.

Report reproducible problems through the repository issue tracker with the
Sprig version, JDK/platform, minimal source, exact command, and diagnostics.
