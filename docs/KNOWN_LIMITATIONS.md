# Known limitations — v0.4.0-alpha.1

This list describes the Java stage-0 implementation, not every feature proposed
by the historical design kit in `spec/`.

- The compiler is written in Java and emits Java source before invoking `javac`.
  It does not compile itself and is not self-hosted.
- `conform C to J` is v1-scoped: Java interfaces only, a non-generic source
  class and target interface, no overloaded abstract methods and no method
  renaming or adapters. It declares a foreign JVM contract; it does not add
  inheritance or interfaces to the language. Generic methods witness by
  erasure; boxed `Short`/`Byte`/`Character` parameters are not expressible
  because of the existing interop adapters.
- Generics accept one or more parameters (`generic K, V:`) but are fully
  explicit: no inference, no variance, and partial type arguments are never
  guessed. A type parameter `T` has no operators, ordering or methods, and
  equality only under `requires T: Equatable`; `Comparable` and user-defined
  capabilities are not implemented (`SPR-GENERIC-CONSTRAINT`).
- Generic code is erased and boxed in generated Java (type parameters become
  `Object`). Boxing/unboxing is compiler-controlled, but generic values carry
  no JVM-level type information at runtime.
- Local/Git Sprig and Maven JVM dependencies are resolved by `sprig resolve`.
  Schema-4 locks verify graph identity, manifests, owner-relative locators and
  JAR/POM SHA-256. Shared
  project classpaths work for check/build/run/api/doctor; only resolve uses
  Maven networking. Apache Resolver handles effective POMs and mediation.
  Missing/invalid POMs fail. Publishing/registry, authentication, Maven plugins
  and non-JAR runtime artifacts remain unsupported. See `DEPENDENCIES.md`.
- The small `std/` slice covers UTF-8 filesystem/path, arguments/environment,
  text/time and a typed JSON model. It is intentionally experimental; there is
  no giant library, HTTP server abstraction or stable package registry.
  LSP, debugger integration and incremental compilation are absent. The local
  VS Code preview offers lexical highlighting, CLI checks/run and Java viewing;
  see `editors/vscode/README.md`.
- JVM interop covers common imported classes, constructors, fields, method
  calls, overloads, and checked exceptions. Java arrays cross the boundary as
  opaque values (no source array syntax; varargs remain unsupported). Concrete
  generic arguments are preserved for explicit `Type[Arg]` application on
  imported classes and methods; wildcards, inference, recursive and
  intersection bounds, generic arrays and Short/Byte/Character generic
  arguments are rejected with structured reasons, class bounds are validated,
  raw evidence never promotes to concrete arguments, and raw boundaries stay
  erased. Collection conversion is explicit through `@std/jvm.spr`; there is no
  implicit Java/Sprig collection conversion. Type-use nullability annotations
  are not interpreted. Java reference results are conservatively nullable; Java
  reference parameters are conservatively non-null.
- Sprig `throws` and `catch` are implemented, but their relationship to Java
  exception classes and top-level execution remains provisional.
- Lambdas have single-expression bodies, support arities zero through three,
  and cannot declare a `throws` type. A lambda that calls a checked-throwing
  operation must handle that effect inside the lambda.
- Runtime numeric failures report `SPR-RUNTIME-EXCEPTION` with the nearest
  statement range for local and imported `Int`/`Int32` checked arithmetic, plus
  `data.origin="checked-arithmetic"`; the span is the statement, not a
  sub-expression. JVM library operations do not inherit Sprig's checked integer
  arithmetic rules.
- Floating-point operations follow Java `float`/`double` behavior. The compiler
  does not promise cross-JVM bitwise identity for transcendental functions,
  numerical stability, physical units, or mathematically correct algorithms.
- Compiler classes use `javac --release 17`. Supported release platforms are Linux/macOS
  with JDK 17 and 26; Windows is an experimental, non-blocking preview; definitions are not execution evidence. The current
  release validation report records which exact source/archive gates ran.
  No production or architecture-wide portability guarantee is made.
- Portable local locks carry owner-relative locators and survive relocation of the
  whole workspace; absolute-path declarations are not portable and need `resolve`
  after the target moves. The lock does not attest source bytes or symlink targets.
  Manifest semantic errors can point to line 1. Cache tree verification adds IO;
  OS locks have no timeout. Git submodules are unsupported. Offline Git builds
  require Git and a complete verified cache. Concurrent hostile mutation after
  validation is outside the cooperative cache model.
- `sprig fmt` is canonical and comment-preserving, without configuration or
  aggressive wrapping. See [formatter](FORMATTER.md) for file safety and
  comment indentation policy. LSP integration remains future work.
- `sprig test` is a sequential project runner for ordinary `.spr` programs.
  It has a fixed 30-second runtime timeout and no parallel execution, watch
  mode, coverage, snapshots or test-function discovery. It requires a current
  lockfile and checks negative fixtures by diagnostic code, not message. See
  [testing](TESTING.md) for its JSON and temporary directory contracts.
- `tests/agent_eval` provides deterministic fixtures and scoring only. No LLM
  run has been performed, and no model performance is claimed.
- The managed SDK installer/upgrader is supported only on Linux/macOS and
  targets public GitHub releases. Published SHA-256 assets detect archive
  mismatch but do not provide signed provenance. Older SDKs are retained.
  SQLite migrations record filenames without content digests; changing an
  applied migration is unsupported by convention, not automatically detected.
  Migration SQL is trusted project code.
- The stage-1 frontend is a subset probe, not a self-hosted compiler.
- Sprig targets v0.4.0-alpha.1, an experimental Alpha under Apache-2.0 (`LICENSE`, `NOTICE`),
  not a production stability or numerical correctness guarantee.

## Callable boundary

Source function types now use `fn(A) -> R`, arities 0–3. Parameters and results
are invariant. Callable types carry no recoverable `throws` effects: handle
those inside named wrappers before placing them in a lambda. Arbitrary Java
SAM interfaces are not converted. Only concrete Fn0..Fn3 generic signatures
preserve callable types; raw, wildcard or unresolved type variables are rejected.
`Character`/`Short`/`Byte` callable slots and arbitrary parameterized Java slots
are unsupported because they would require additional erased-value adapters.
Nullable callable values use `(fn(A) -> R)?`; `fn(A) -> R?` means nullable result.

Project discovery currently compares normalized source-root prefixes rather than
canonicalizing alternate symlink spellings. On macOS an explicit absolute /var
source while cwd discovers /private/var can be treated as standalone and lose
package aliases. Use project-relative source paths from the project directory;
this existing path-alias behavior was reproduced during the SDK web experiment.
