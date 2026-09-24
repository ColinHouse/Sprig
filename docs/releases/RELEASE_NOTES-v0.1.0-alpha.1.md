# Sprig v0.1.0-alpha.1 (draft)

This is the first candidate release of the Sprig Java stage-0 compiler. Sprig
source is parsed and checked, lowered to Java source, compiled with `javac`,
and run on the JVM. The language design remains Sprig v0.7.

## Included and exercised

- Indentation-based syntax, typed functions, local inference, classes, enums,
  sealed variants, and exhaustive `match` statements.
- `let`/`var`, nullable types and flow narrowing, typed error handling, modules,
  and separate immutable/mutable collections.
- Checked fixed-width integer operations, explicit numeric conversions,
  `BigInt`, `Decimal`, IEEE `Float`/`Float32`, and static diagnostics for
  ambiguous numeric operations.
- A command line for `check`, `build`, `run`, diagnostic explanations, and JSON
  diagnostics.
- Regression coverage for parser recovery, zero-argument lambdas, contextual
  `Int32` compound assignments, and nullable values passed to Java references.

## Runtime and build requirements

The candidate archive includes the compiler/runtime JAR and ANTLR 4.13.2, and
currently requires JDK 26 or newer. It was built and run on macOS Apple Silicon
with OpenJDK 26.0.1. The source compiles against the Java 17 API (`--release
17`); validation on a Java 17 runtime is pending. The archive is not
platform-specific by design, but has only been smoke-tested on macOS Apple
Silicon.

## Known limits

This is an alpha compiler, not a production stability or numerical correctness
claim. It is not self-hosted and has no package manager, language server, IDE
plugin, complete standard library, or unrestricted Java interop. See
`docs/KNOWN_LIMITATIONS.md` and `docs/NUMERIC_SEMANTICS.md`.

Report reproducible problems with the Sprig version, JDK/platform, minimal
source, exact command, and diagnostics. Public release and redistribution are
pending project-owner license selection.
