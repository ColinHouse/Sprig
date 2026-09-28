# Sprig agent bootstrap guide (v0.4.0-alpha.1)

This guide assumes only the release archive and a JDK 17+ are available.
The compiler's versioned catalog is the quickest source of implemented syntax.

```text
bin/sprig version
bin/sprig doctor --json
bin/sprig capabilities --json
bin/sprig help language --json
bin/sprig help strings --json
bin/sprig help match --json
bin/sprig help generics --json
bin/sprig help projects --json
bin/sprig help dependencies --json
bin/sprig help upgrade --json
bin/sprig api java.time.LocalDate --json
bin/sprig api src/main.spr --json
bin/sprig api @pkg/module.spr --member Type.member --json
bin/sprig api . --json
bin/sprig check program.spr --json
bin/sprig explain SPR-CODE --json
bin/sprig run program.spr --json
bin/sprig fmt program.spr --check --json
```

Use `help` topics before writing unfamiliar constructs. `capabilities` lists
deliberately unsupported features; grammar acceptance alone does not imply
runtime support. Every function and method declares parameter and result types.
Top-level statements execute; a named `main` function is not invoked
automatically. Sprig classes and variant cases use named constructors; ordinary functions and
Java methods use positional arguments. `match` supports exhaustive statements and value expressions; expression
branches contain exactly one expression. A Java reference result is nullable until checked. `List` and
`MutableList` differ. Integer `/` is rejected; use `divTrunc` when truncation is
intended. No implicit mixed numeric promotion is performed. Sprig has no `Char`
type: a String element is a non-null `String`, and `length`, indexing, `charAt`,
`codeAt`, `substring`, `indexOf` and `for` iteration use Unicode code points, not
UTF-16 code units and not grapheme clusters (`"A😀東".length()` is 3). Java `char`
interop remains a single UTF-16 code unit at the JVM boundary; run
`sprig help strings --json`.

For a Maven library, declare exact release coordinates in `[[jvm]]` and run
`sprig resolve`; `api/check/build/run/doctor` then share the locked classpath.
Only resolve downloads. Extra local JARs can use repeated `--classpath` flags;
paths resolve against cwd and follow locked entries. Inspect
`api`'s `usableFromSprig`, `signatureSupported`, `interopLevel`,
`unusableReason`, `genericBoundary`, and `checkedExceptions` before writing a
call. `usableFromSprig` means the compiler can bind and emit the erased JVM
signature; it does not promise generic element safety. `interopLevel` is
`direct`, `erased-generic`, or `unsupported`. Use `--member NAME` to limit the
metadata result while preserving overloads. `api` does not initialize classes.

`api` also accepts a `.spr` module path, `@package/module.spr`, or a project
directory. It returns resolved declarations after normal checking: function
parameters/result/throws, class fields and methods, enums, variants and generic
parameters; `--member Type.member` narrows module results. A project directory
returns the checked inventory of project source modules plus each dependency's
exported modules. Stale/missing locks and unexported modules are refused, and
no application code is executed.

On Linux/macOS, install a managed SDK with `docs/INSTALL.md`; the installer
verifies the release checksum and `sprig upgrade --check` inspects the current
installation. Windows remains experimental. Useful library dogfood projects
include `examples/ledger`, `examples/sqlite_migrations` and
`examples/json_select`; the package READMEs describe their APIs. SQLite
migrations are trusted SQL files named `NNN_description.sql`; keep applied
migration files unchanged.

`check/build/run --json` return one JSON object on stdout with
`schemaVersion`, `toolVersion`, `command`, `exitCode`, `environment.classpath`,
and `diagnostics`. Each diagnostic has a stable code, phase, severity, URI,
zero-based range, message, and optional types/hint/data. Diagnostics may also
carry `relatedHelp` (a help topic) and `repair`
(`{"kind": ..., "machineApplicable": bool}`); `machineApplicable` is false when
the fix needs a semantic decision. `capabilities --json` includes
`featureGuidance` with alternatives for unsupported features. `CLI` is the phase for
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
`docs/DEPENDENCIES.md` describes local/Git/Maven resolution, schema-3 lock identities,
exports and offline cache validation. Run `sprig resolve` before project builds.
Use `import "@alias/module.spr" as module` for an exported dependency module.
Project compilation refuses missing/stale locks or missing/corrupt locked JARs.
An explicit file outside the project source root bypasses the project graph;
use local `--classpath` there. Import the installed bundled standard package
with `import "@std/files.spr" as files`; no copied std or dependency declaration
is needed. `build --emit-java-only --json` returns checked Java paths before javac;
see `docs/STANDARD_LIBRARY.md` and the three `examples/showcases` projects.

For details, see `docs/QUICK_REFERENCE.md`, `docs/JVM_INTEROP.md`,
`docs/NUMERIC_SEMANTICS.md`, and `docs/KNOWN_LIMITATIONS.md` in the archive.

Declaration facades use `export alias.Symbol` after imports and before other
code. Query `sprig api module.spr --json` for exported signatures and origins.
No wildcard, renaming or implicit reexport exists. See docs/MODULE_REEXPORTS.md.

`sprig fmt file.spr` (or a project directory) writes canonical,
comment-preserving source and fails without rewriting malformed input;
`--check --json` reports whether files would change. Formatting never changes
program semantics. See docs/FORMATTER.md.
