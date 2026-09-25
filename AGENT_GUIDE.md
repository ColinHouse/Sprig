# Sprig agent bootstrap guide (alpha.2 development)

This guide assumes only the release archive and a JDK 17+ are available.
The compiler's versioned catalog is the quickest source of implemented syntax.

```text
bin/sprig version
bin/sprig doctor --json
bin/sprig capabilities --json
bin/sprig help language --json
bin/sprig help match --json
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
`api`'s `usableFromSprig`, `unusableReason`, `genericBoundary`, and
`checkedExceptions` before writing a call. `api` does not initialize classes.

`check/build/run --json` return one JSON object on stdout with
`schemaVersion`, `toolVersion`, `command`, `exitCode`, `environment.classpath`,
and `diagnostics`. Each diagnostic has a stable code, phase, severity, URI,
zero-based range, message, and optional types/hint/data. `CLI` is the phase for
option errors. JVM overload failures
include candidate signatures in `data.candidates`. Exit status 0 means
success, 1 means a program or source failure, and 2 means a tooling/IO failure.
Use the code with `explain --json`, repair the source, and rerun `check` before
`run`. Never invent syntax or accept a lossy conversion without deciding the
numerical meaning.

For details, see `docs/QUICK_REFERENCE.md`, `docs/JVM_INTEROP.md`,
`docs/NUMERIC_SEMANTICS.md`, and `docs/KNOWN_LIMITATIONS.md` in the archive.
