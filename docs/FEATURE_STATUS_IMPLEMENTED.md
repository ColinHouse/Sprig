# Sprig stage-0 — implemented feature status

This table reflects what the compiler in this directory **actually does**, as
verified by `scripts/test.sh`. The release validation record is
[on GitHub](https://github.com/ColinHouse/Sprig/blob/main/docs/RELEASE_VALIDATION.md).
The historical design kit in `spec/` describes target semantics rather than current capabilities.

| Feature | Front end | Static semantics | Codegen + runtime | Tests |
|---|---|---|---|---|
| Expression `match` | one expression per branch | strict contextual/inferred results, shared exhaustive/binder rules | Java 17 switch/yield; no closures; scrutinee once | match expression runtime/generic/null/effect tests |
| Explicit `export alias.Symbol` | yes | original symbols, collisions, cycles and package boundaries | no wrapper/copy | reexport runtime/API/package suite |
| Canonical `sprig fmt` | trivia-preserving lexer | parse structure invariant | atomic file replacement, check/JSON modes | formatter fixtures + valid corpus |
| Foreign JVM conformance `conform C to J` | declaration form, one per relation | exact JVM witness matching, v1 restrictions, foreign conformance conversion | emitted `implements`; non-null entry guards (`SPR-CONFORM-*`) | `tests/conform` runtime and negative suite |
| Functions, typed parameters/returns, recursion | yes | yes | Java static methods | runtime 01/02, visitor |
| Indentation, blocks, `if`/`elif`/`else`, `while`, `for`, `break`/`continue` | yes | yes | Java control flow | runtime 03/13 |
| `let`/`var`, local inference, assignment rules | yes | yes (`SPR-NAME-LET-ASSIGN`) | locals/static fields | runtime 16 |
| Classes: named auto-ctor, per-instance defaults, implicit fields/methods | yes | yes | final Java classes | runtime 04/12 |
| `enum` + exhaustive `match` | yes | yes | Java enum + if-chain | runtime 06 |
| `variant` + typed case binders + exhaustive `match` | yes | yes | sealed interface + nested classes | runtime 05/14, visitor |
| Missing/duplicate/wrong-type cases, enum binder rejection | — | yes (`SPR-MATCH-*`) | — | semantics 11 cases |
| Nullability `T?`, null checks, narrowing | yes | yes (`SPR-TYPE-NULL/NULLABLE`) | boxed nullable locals | runtime 07 |
| `List`/`MutableList`/`Map`/`MutableMap`, snapshots, indexing, `in` | yes | yes, distinct mutability | runtime wrappers | runtime 09/16 |
| String positions: `length`, indexing, `charAt`, `codeAt`, `substring`, `indexOf`, iteration | yes | Unicode code-point indices; one-code-point `String` elements; no `Char` type | `sprig.runtime.StringOps` helpers and code-point iteration | runtime 20 + String semantics suite |
| Immutable collection mutation rejected | — | yes (`SPR-COLLECTION-IMMUTABLE`) | — | semantics |
| User generics: `generic K, V:` blocks (one or more parameters) for class/variant/function, explicitly applied as `Entry[String, Int]`, erased and boxed in generated Java | yes | yes (`SPR-TYPE-GENERIC-*`) | raw Java classes + compiler-controlled boxing/unboxing | runtime 19, visitor generic_stack, semantics 8 cases, syntax 08 |
| Generic variant expanded payloads + exhaustive `match` on instantiations | yes | yes | raw nested case classes | runtime 19 |
| `requires X: Equatable` equality capability | yes | equality on the parameter allowed only with the clause; value equality in codegen | boxed `Objects`-style equality | visitor generic_stack, runtime 19 |
| `requires X: Comparable` | parsed | rejected as not implemented (`SPR-GENERIC-CONSTRAINT`) | — | semantics |
| Project model: `sprig.toml` discovery, defaults, `init`, `project --json`, `deps --json`, explicit-file priority, `run --bin` | yes | validated (`SPR-PROJECT-*`) | default entry compiled with the normal pipeline | project model suite |
| Local and Git Sprig dependencies: recursive resolution, `@alias/module.spr` imports, `exports` enforcement, cycle detection, `sprig.lock`, stale/missing lock refusal | yes | yes (`SPR-DEP-*`, `SPR-PROJECT-*`) | dependency source modules compile through the normal pipeline | dependency resolver suite |
| Offline mode for local/Git dependencies (`--offline`, warm cache required) | yes | `SPR-DEP-OFFLINE` when the cache is incomplete | `~/.sprig/git` verified detached checkouts | dependency resolver |
| Maven/JVM dependency resolution | exact direct release coordinates | Apache Resolver effective POM + transitive mediation, schema-3 hashes/graph | shared locked check/build/run/api/doctor classpath | Maven fixtures + real commons-text showcase |
| Source callable types `fn(A) -> R`, nullable `(fn(A) -> R)?`, invariant, arities 0–3 | yes | signatures/fields/locals/collections/generics, no callable effects | existing Fn0..Fn3 representation | tests/callables |
| JVM callable ABI with concrete `sprig.runtime.Fn0..Fn3` signatures | yes | boxed invariant parameters/results, non-null arguments | direct calls, nullable returned callable and runtime contract guards | tests/callables |
| Lambdas `fn(...) => expr`, arities 0–3, `map`/`filter`/`forEach` | yes | yes | `Fn0..Fn3` anonymous classes | runtime 10, 18 |
| `throw`/`throws`/`try`/`catch`/`finally` (typed errors) | yes | yes (`SPR-FLOW-THROWS`) | Java exceptions | runtime 08, interop 15 |
| Flow: definite return, unreachable code | — | yes (`SPR-FLOW-*`) | — | semantics |
| Modules: file imports, alias access, init once, cycle detection | yes | yes | static `$init()` per module | runtime 12, semantics |
| JDK interop: imports, ctors, static/instance methods/fields, overloads | yes | reflection-based; reference results nullable and require narrowing | direct Java calls | runtime 15, correctness regressions |
| Checked `Int`/`Int32`, explicit integer quotient, numeric literal ranges | yes | `SPR-NUM-*` checks | `NumericOps` checked JVM operations | numeric acceptance suite |
| IEEE `Float`/`Float32`, explicit exact/lossy conversion | yes | mixed-type and narrowing checks | Java `double`/`float` | numeric acceptance suite |
| `BigInt` and `Decimal` | yes | distinct native types | BigInteger/BigDecimal wrappers | numeric acceptance suite |
| Java checked exceptions + typed catch + `error.message` | yes | yes | Java try/catch | runtime 15 |
| `sprig check/run/build/explain/codes/help/capabilities/api/doctor`, `--json`, `--syntax-only` | — | — | — | `scripts/test.sh`, agent tooling suite |
| Explicit local `--classpath` on check/build/run/api | — | shared class loader + javac/JVM path | locked JVM JARs precede explicit entries; compiler libraries isolated | agent tooling suite |
| javac error → Sprig span translation | — | — | line map | by design |
| Checked effects from omitted class defaults | — | checked at each constructor call; explicit field values skip unused defaults | defaults still evaluate per instance, in declaration order | correctness regressions |
| `Unit` value positions and unsupported type arguments | rejected before codegen | `SPR-TYPE-UNIT` / `SPR-TYPE-MISMATCH` | no invalid Java emitted | correctness regressions |
| Match on statically inferred variant case | yes | singleton exhaustiveness; impossible other branches rejected | concrete case `instanceof` dispatch | correctness regressions |
| JSON CLI results | — | includes command/status and structured diagnostics | run output carried as `programOutput` | correctness regressions |
| `sprig test` project runner | ordinary `.spr` files under `tests/` | compile-fail `.expect.toml` matches stable error codes using normal project/dependency checking | isolated child JVM per runtime test, 30-second timeout, per-test temp directory, deterministic JSON rows and `@std/test.spr` process helper | test runner integration suite + five-case dogfood project |
| Managed Linux/macOS SDK install and upgrade | release ZIP + checksum workflow | managed-install metadata and layout validation | staged installation and atomic `current` pointer switch; older versions retained | installer, upgrade and installed-SDK dogfood suites |
| SQLite migrations (`@sqlite/migrations.spr`) | Sprig package module | validated `NNN_description.sql` names, sorted ledger and idempotent apply | trusted multi-statement SQL and ledger row share a SQLite batch transaction | migration apply/restart/failure-retry suite |
| CLI parsing (`@cli/cli.spr`) | Sprig package module | typed option specs, duplicate/unknown/missing checks | deterministic usage, flags/values/aliases/positionals | CLI library and installed `json-select` dogfood |
| Sprig module API (`sprig api module.spr\|@pkg/module.spr`) | resolved checked AST | declaration/field/method/throws/generic metadata; `--member Type.member` | compiler-owned JSON; never executes code | Sprig API suite |
| Project API inventory (`sprig api .`) | project + lockfile validation | source modules plus each dependency's exported modules | no application execution; stale/unexported targets refused | Sprig API suite and installed SDK dogfood |
| Structured diagnostic metadata | optional `relatedHelp` and `repair` fields | `machineApplicable` false unless semantics-preserving and unambiguous | stable fields unchanged | agent tooling suite |
| Capability feature guidance | `featureGuidance` per unsupported feature | alternatives and help topic reflect current compiler only | additive to `features` booleans | agent tooling and Sprig API suites |
| Operational `explain` | `whyMatters`, `confusedWith`, causes/fixes/examples, `repair`, `relatedHelp` | conservative, compact | same data in text and `--json` | agent tooling suite |
| Sprig-written agent tools | `examples/agent_tools` (api-report, diag-summary, api-diff) | plain Sprig JSON/file/CLI policy | consume saved compiler JSON | agent tools suite |
| Agent task pack | deterministic fixtures and runner under `tests/agent_eval` | acceptance is mechanical; no model run claimed | initial states fail, known solutions pass | task-pack gate |

Not implemented (honest status): generic type inference, variance,
`Comparable` and user-defined capabilities, inheritance or interfaces, `match`
expressions, nested/positional patterns, `%=`,
tuples/destructuring, varargs/arrays/annotations in interop,
LSP, publishing/registry, incremental checking,
self-hosting.
See [`KNOWN_LIMITATIONS.md`](KNOWN_LIMITATIONS.md) for boundaries.
