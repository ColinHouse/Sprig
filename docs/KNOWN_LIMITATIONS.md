# Known limitations — v0.3.0-alpha.1

This list describes the Java stage-0 implementation, not every feature proposed
by the Sprig v0.7 design kit.

- The compiler is written in Java and emits Java source before invoking `javac`.
  It does not compile itself and is not self-hosted.
- v0.8 generics accept one or more parameters (`generic K, V:`) but are fully
  explicit: no inference, no variance, and partial type arguments are never
  guessed. A type parameter `T` has no operators, ordering or methods, and
  equality only under `requires T: Equatable`; `Comparable` and user-defined
  capabilities are not implemented (`SPR-GENERIC-CONSTRAINT`).
- Generic code is erased and boxed in generated Java (type parameters become
  `Object`). Boxing/unboxing is compiler-controlled, but generic values carry
  no JVM-level type information at runtime.
- Local/Git Sprig and Maven JVM dependencies are resolved by `sprig resolve`.
  Schema-3 locks verify graph identity, manifests and JAR/POM SHA-256. Shared
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
  calls, overloads, and checked exceptions. Java generic signatures, type-use
  nullability annotations, arrays, varargs, and collection adapters are limited
  or unsupported. Java reference results are conservatively nullable; Java
  reference parameters are conservatively non-null.
- Sprig `throws` and `catch` are implemented, but their relationship to Java
  exception classes and top-level execution remains provisional.
- Lambdas have single-expression bodies, support arities zero through three,
  and cannot declare a `throws` type. A lambda that calls a checked-throwing
  operation must handle that effect inside the lambda.
- Runtime numeric failures report a runtime diagnostic but may not carry the
  exact source span of the arithmetic expression. JVM library operations do
  not inherit Sprig's checked integer arithmetic rules.
- Floating-point operations follow Java `float`/`double` behavior. The compiler
  does not promise cross-JVM bitwise identity for transcendental functions,
  numerical stability, physical units, or mathematically correct algorithms.
- Compiler classes use `javac --release 17`. Supported release platforms are Linux/macOS
  with JDK 17 and 26; Windows is an experimental, non-blocking preview; definitions are not execution evidence. The current
  release validation report records which exact source/archive gates ran.
  No production or architecture-wide portability guarantee is made.
- Local dependency locks contain canonical absolute paths and are not portable.
  Manifest semantic errors can point to line 1. Cache tree verification adds IO;
  OS locks have no timeout. Git submodules are unsupported. Offline Git builds
  require Git and a complete verified cache. Concurrent hostile mutation after
  validation is outside the cooperative cache model.
- The stage-1 frontend is a subset probe, not a self-hosted compiler.
- Sprig targets v0.3.0-alpha.1, an experimental Alpha under Apache-2.0 (`LICENSE`, `NOTICE`),
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
