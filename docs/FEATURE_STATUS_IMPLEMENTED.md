# Sprig v0.8-dev stage-0 — implemented feature status

This table reflects what the compiler in this directory **actually does**, as
verified by `scripts/test.sh` (see the independent audit for exact counts). It is the implementation-side companion to the
design kit's `docs/FEATURE_STATUS.md`.

| Feature | Front end | Static semantics | Codegen + runtime | Tests |
|---|---|---|---|---|
| Functions, typed parameters/returns, recursion | yes | yes | Java static methods | runtime 01/02, visitor |
| Indentation, blocks, `if`/`elif`/`else`, `while`, `for`, `break`/`continue` | yes | yes | Java control flow | runtime 03/13 |
| `let`/`var`, local inference, assignment rules | yes | yes (`SPR-NAME-LET-ASSIGN`) | locals/static fields | runtime 16 |
| Classes: named auto-ctor, per-instance defaults, implicit fields/methods | yes | yes | final Java classes | runtime 04/12 |
| `enum` + exhaustive `match` | yes | yes | Java enum + if-chain | runtime 06 |
| `variant` + typed case binders + exhaustive `match` | yes | yes | sealed interface + nested classes | runtime 05/14, visitor |
| Missing/duplicate/wrong-type cases, enum binder rejection | — | yes (`SPR-MATCH-*`) | — | semantics 11 cases |
| Nullability `T?`, null checks, narrowing | yes | yes (`SPR-TYPE-NULL/NULLABLE`) | boxed nullable locals | runtime 07 |
| `List`/`MutableList`/`Map`/`MutableMap`, snapshots, indexing, `in` | yes | yes, distinct mutability | runtime wrappers | runtime 09/16 |
| Immutable collection mutation rejected | — | yes (`SPR-COLLECTION-IMMUTABLE`) | — | semantics |
| User generics: `generic K, V:` blocks (one or more parameters) for class/variant/function, explicitly applied as `Entry[String, Int]`, erased and boxed in generated Java | yes | yes (`SPR-TYPE-GENERIC-*`) | raw Java classes + compiler-controlled boxing/unboxing | runtime 19, visitor generic_stack, semantics 8 cases, syntax 08 |
| Generic variant expanded payloads + exhaustive `match` on instantiations | yes | yes | raw nested case classes | runtime 19 |
| `requires X: Equatable` equality capability | yes | equality on the parameter allowed only with the clause; value equality in codegen | boxed `Objects`-style equality | visitor generic_stack, runtime 19 |
| `requires X: Comparable` | parsed | rejected as not implemented (`SPR-GENERIC-CONSTRAINT`) | — | semantics |
| Project model: `sprig.toml` discovery, defaults, `init`, `project --json`, `deps --json`, explicit-file priority, `run --bin` | yes | validated (`SPR-PROJECT-*`) | default entry compiled with the normal pipeline | project model 14 checks |
| Dependencies: local/Git/Maven resolution, `sprig.lock`, offline cache | — | declared only, reported `SPR-PROJECT-UNSUPPORTED` (`resolved: false`) | — | project model |
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
| Explicit local `--classpath` on check/build/run/api | — | shared class loader + javac/JVM path | no automatic dependency resolution | agent tooling suite |
| javac error → Sprig span translation | — | — | line map | by design |
| Checked effects from omitted class defaults | — | checked at each constructor call; explicit field values skip unused defaults | defaults still evaluate per instance, in declaration order | correctness regressions |
| `Unit` value positions and unsupported type arguments | rejected before codegen | `SPR-TYPE-UNIT` / `SPR-TYPE-MISMATCH` | no invalid Java emitted | correctness regressions |
| Match on statically inferred variant case | yes | singleton exhaustiveness; impossible other branches rejected | concrete case `instanceof` dispatch | correctness regressions |
| JSON CLI results | — | includes command/status and structured diagnostics | run output carried as `programOutput` | correctness regressions |

Not implemented (honest status): generic type inference, variance,
`Comparable` and user-defined capabilities, inheritance or interfaces, `match`
expressions, nested/positional patterns, function types in source, `%=`,
tuples/destructuring, varargs/arrays/annotations in interop, file IO library,
dependency resolution and `sprig.lock` (the manifest/project model
exists), LSP, incremental checking, self-hosting.
See [`KNOWN_LIMITATIONS.md`](KNOWN_LIMITATIONS.md) for boundaries. Earlier
implementation reports from the former `output/` development tree were moved
out of the public repository into the maintainer's local archive during the
publication cleanup; they are not required to build or test the compiler.
