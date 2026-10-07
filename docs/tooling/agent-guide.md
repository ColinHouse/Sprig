# Sprig agent bootstrap guide (v0.7.1-beta.1)

This guide assumes only the release archive and a JDK 21+ are available.
The compiler's versioned catalog is the quickest source of implemented syntax.

```text
bin/sprig version
bin/sprig doctor --json
bin/sprig capabilities --json
bin/sprig help language --json
bin/sprig help strings --json
bin/sprig help match --json
bin/sprig help conform --json
bin/sprig help generics --json
bin/sprig help projects --json
bin/sprig help dependencies --json
bin/sprig help upgrade --json
bin/sprig api java.time.LocalDate --json
bin/sprig api src/main.spr --json
bin/sprig api @pkg/module.spr --member Type.member --json
bin/sprig api @std/text.spr --json
bin/sprig api . --json
bin/sprig project --json
bin/sprig deps --json
bin/sprig check program.spr --json
bin/sprig check --bin tool --json
bin/sprig explain SPR-CODE --json
bin/sprig run program.spr --json
bin/sprig test examples/test_runner --json
bin/sprig help testing --json
bin/sprig fmt program.spr --check --json
```

Start with `help language`: its syntax block is a complete program (imports,
standard input, `if/elif/else`, loops, `var`/`let`, functions) that compiles and
runs. `help <topic> --json` also returns `methods` for `language` (built-in
functions), `strings`, `collections` and `numerics`, read from the tables the
checker resolves against; text help prints each example's source. Diagnostics
for constructs from other languages (`else if`, `var x: T` without a value,
`List<Int>`, braces, `++`, `;`, `&&`, `readLine()`, `len()`, `True`, `str`, an
unimported `Math`) name the Sprig spelling in the message or hint, and a missing
`@std` module lists the bundled ones. When a file declares `func main`, has no
top-level statements and prints nothing, `run` adds a note (standard error, or
`note` with `--json`).

Use `help` topics before writing unfamiliar constructs. `capabilities` lists
deliberately unsupported features; grammar acceptance alone does not imply
runtime support. `explain` returns structured causes and safe fixes for every
stable diagnostic, so prefer it over guessing when `check` reports a code. Every function and method declares parameter and result types.
Top-level statements execute; a named `main` function is not invoked
automatically. Sprig classes and variant cases use named constructors; ordinary functions and
Java methods use positional arguments. `match` supports exhaustive statements and value expressions; expression
branches contain exactly one expression. `if`/`elif`/`else` is a value expression in the same positions, with a
required `else` and one expression per branch; an `if` at the start of a statement is the `if` statement. A Java
reference result is nullable until checked. `List` and
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
`interopReasonCodes`, `adaptation`, `unusableReason`, `genericBoundary`, and
`checkedExceptions` before writing a call. `usableFromSprig` means the compiler
can bind and emit the JVM signature; it does not promise generic element
safety. `interopLevel` is `direct`, `concrete-generic`, `opaque-array`,
`adaptable`, `sprig-callable`, `erased-generic`, or `unsupported`; reason codes
are stable ids (`varargs-expansion`, `wildcard-bounds`,
`raw-generic-boundary`, `explicit-type-arguments-required`,
`generic-wrapper-unsupported`, ...) and
`adaptation` names an explicit helper when one exists (`byte-array` via
`sprig.runtime.jvm.HostBytes`, `collection-adapter` via `@std/jvm.spr`). Java
arrays cross as opaque values with their exact JVM class; concrete generic
arguments require explicit `Type[Arg]` application. Raw generic values never
become concrete evidence: a parameterized target requires matching arguments
(or a hierarchy projection), Short/Byte/Character generic shapes are
rejected rather than erased silently, and a wildcard keeps its bound
(`wildcard-bounds`: reads at the upper bound, no writes through `? extends`).
Use `--member NAME` to
limit the metadata result while preserving overloads. `api` does not
initialize classes.

Generate an editable wrapper for an ecosystem class with
`bin/sprig wrap com.example.Client --out client.spr --json`; it uses the same
classpath as `api`, skips unsupported members with the shared reason codes and
never writes a file that fails checking.

For package/application tests, run `sprig resolve` and then `sprig test --json`.
The runner discovers ordinary `tests/**/*.spr` programs, each in a separate
JVM. Negative fixtures under `tests/compile_fail/` have sibling
`.expect.toml` files listing stable diagnostic codes. `@std/test.spr` offers
an isolated temporary directory and argv-based child process calls. The
`testRunner` capability object and [testing contract](testing.md) give
the exact modes, exit codes and JSON fields; no test annotations are used.

`api` also accepts a `.spr` module path, `@package/module.spr`, or a project
directory. It returns resolved declarations after normal checking: function
parameters/result/throws, class fields and methods, enums, variants and generic
parameters; `--member Type.member` narrows module results. A project directory
returns the checked inventory of project source modules plus each dependency's
exported modules. Stale/missing locks and unexported modules are refused, and
no application code is executed.

On Linux/macOS, install a managed SDK with `docs/projects/install.md`; the installer
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
the fix needs a semantic decision. `suggestedEdits` lists the mechanical
rewrites the hint describes, each as `{"range", "newText", "description"}`
(see [diagnostic codes](diagnostic-codes.md)); apply one, then rerun `check`
to see what the program needs next. `capabilities --json` includes
`featureGuidance` with alternatives for unsupported features. `CLI` is the phase for
option errors. JVM overload failures
include candidate signatures in `data.candidates`. CLI tooling errors use 2;
source and runtime failures normally use 1. `run` forwards the Sprig process
status, so an explicit program exit can use any status, including 2; non-zero
exits without a JVM exception are reported as `SPR-PROGRAM-EXIT` with
`data.programExitCode` in JSON mode. Text mode forwards the status without a
compiler-error message.
Use the code with `explain --json`, repair the source, and rerun `check` before
`run`. Never invent syntax or accept a lossy conversion without deciding the
numerical meaning.

For generics, read `docs/language/generics.md`: parameters and uses are explicit,
including multiple parameters. `requires T: Equatable` must lead the function
body. Generic variant cases use expanded payloads and match case owners omit
type arguments. `docs/projects/projects.md` defines the strict manifest subset;
`docs/projects/dependencies.md` describes local/Git/Maven resolution, schema-5 lock identities,
exports and offline cache validation. Run `sprig resolve` before project builds.
Use `import "@alias/module.spr" as module` for an exported dependency module.
Project compilation refuses missing/stale locks or missing/corrupt locked JARs.
An explicit file outside the project source root bypasses the project graph;
use local `--classpath` there. Import the installed bundled standard package
with `import "@std/files.spr" as files`; no copied std or dependency declaration
is needed. `build --emit-java-only --json` returns checked Java paths before javac;
see `docs/projects/standard-library.md` and the three `examples/showcases` projects.

For details, see `docs/language/quick-reference.md`, `docs/jvm/interop.md`,
`docs/language/numeric-semantics.md`, and `docs/language/known-limitations.md` in the archive.

For Gradle host projects, use the SDK-bundled `dev.sprig` plugin rather than
hand-writing bridge/classpath/generation tasks. `./gradlew sprigInfo` reports
the selected source set, compiler, runtime, lock and generated output. See
`libraries/sprig-gradle/README.md` and `libraries/sprig-fabric/README.md`.

Declaration facades use `export alias.Symbol` after imports and before other
code. Query `sprig api module.spr --json` for exported signatures, origins and
`doc`, the `#` comment written directly above a declaration or member.
No wildcard, renaming or implicit reexport exists. See docs/language/module-reexports.md.

`sprig fmt file.spr` (or a project directory) writes canonical,
comment-preserving source and fails without rewriting malformed input;
`--check --json` reports whether files would change. Formatting never changes
program semantics. See docs/tooling/formatter.md.
