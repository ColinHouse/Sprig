# Sprig agent bootstrap guide (v0.2.0-alpha.1 development)

This guide assumes only the release archive and a JDK 17+ are available.
The compiler's versioned catalog is the quickest source of implemented syntax.

```text
bin/sprig version
bin/sprig doctor --json
bin/sprig capabilities --json
bin/sprig help language --json
bin/sprig help match --json
bin/sprig help generics --json
bin/sprig help projects --json
bin/sprig help dependencies --json
bin/sprig api java.time.LocalDate --json
bin/sprig check program.spr --json
bin/sprig explain SPR-CODE --json
bin/sprig run program.spr --json
```

Use `help` topics before writing unfamiliar constructs. `capabilities` lists
deliberately unsupported features; grammar acceptance alone does not imply
runtime support. Every function and method declares parameter and result types.
Top-level statements execute; a named `main` function is not invoked
automatically. Sprig classes and variant cases use named constructors; ordinary functions and
Java methods use positional arguments. `match` is an exhaustive statement, not
an expression. A Java reference result is nullable until checked. `List` and
`MutableList` differ. Integer `/` is rejected; use `divTrunc` when truncation is
intended. No implicit mixed numeric promotion is performed.

For a third-party JAR, pass the same `--classpath path/to/library.jar` to
`api`, `check`, `build`, and `run`. Paths resolve against the current working
directory. Repeat the flag for multiple entries. No download occurs. Inspect
`api`'s `usableFromSprig`, `signatureSupported`, `interopLevel`,
`unusableReason`, `genericBoundary`, and `checkedExceptions` before writing a
call. `usableFromSprig` means the compiler can bind and emit the erased JVM
signature; it does not promise generic element safety. `interopLevel` is
`direct`, `erased-generic`, or `unsupported`. Use `--member NAME` to limit the
metadata result while preserving overloads. `api` does not initialize classes.

`check/build/run --json` return one JSON object on stdout with
`schemaVersion`, `toolVersion`, `command`, `exitCode`, `environment.classpath`,
and `diagnostics`. Each diagnostic has a stable code, phase, severity, URI,
zero-based range, message, and optional types/hint/data. `CLI` is the phase for
option errors. JVM overload failures
include candidate signatures in `data.candidates`. CLI tooling errors use 2;
source and runtime failures normally use 1. `run` forwards the Sprig process
status, so an explicit program exit can use any status, including 2; non-zero
exits without a JVM exception are reported as `SPR-PROGRAM-EXIT` with
`data.programExitCode`.
Use the code with `explain --json`, repair the source, and rerun `check` before
`run`. Never invent syntax or accept a lossy conversion without deciding the
numerical meaning.

For generics, read `docs/GENERICS.md`: parameters and uses are explicit,
including multiple parameters. `requires T: Equatable` must lead the function
body. Generic variant cases use expanded payloads and match case owners omit
type arguments. `docs/PROJECTS.md` defines the strict manifest subset;
`docs/DEPENDENCIES.md` describes local/Git resolution, edge lock identities,
exports and offline cache validation. Run `sprig resolve` before project builds.
Use `import "@alias/module.spr" as module` for an exported dependency module.
Project-based compilation refuses missing or stale locks and unsupported Maven declarations. An explicit source
file outside the project source root bypasses the project, so pass every manually acquired JVM JAR with
`--classpath`. Do not treat that as Maven resolution.

For details, see `docs/QUICK_REFERENCE.md`, `docs/JVM_INTEROP.md`,
`docs/NUMERIC_SEMANTICS.md`, and `docs/KNOWN_LIMITATIONS.md` in the archive.
