# Known limitations — v0.2.0-alpha.1

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
- Sprig dependencies resolve for **local paths and Git** (`sprig resolve`
  writes a deterministic `sprig.lock`, `@alias/module.spr` imports are checked
  against `exports`, and `--offline` rebuilds from the cache). **Maven/JVM
  dependency resolution is not implemented**: declaring `[[jvm]]` fails with
  `SPR-DEP-MAVEN`, and `sprig api`/`check`/`run` still need explicit
  `--classpath` for third-party jars. Git checkouts are verified against exact HEAD, clean tracked/untracked
  contents and cache metadata on every reuse. Modified checkouts are rejected
  with SPR-DEP-GIT; no Maven artifacts are fetched.
  `sprig.lock` covers Sprig dependencies; JVM coordinates are recorded only
  when resolution exists.
- The early dependency system covers local/Git Sprig packages. Maven resolution,
  publishing/registry workflows, a standard-library distribution, language server,
  IDE plugin, debugger and editor integration are not implemented.
  Explicit local JAR/directory `--classpath` is available for `check`, `build`,
  `run`, and `api`; only explicitly resolved Git Sprig packages are downloaded.
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
- Local tests cover macOS Apple Silicon with JDK 17.0.19 and 26.0.1; hosted CI
  covers Linux with JDK 17 and 26. Compiler classes use `javac --release 17`.
  Other platforms/architectures have not been validated.
- Local dependency locks contain canonical absolute paths and are not portable.
  Manifest semantic errors can point to line 1. Cache tree verification adds IO;
  OS locks have no timeout. Git submodules are unsupported. Offline Git builds
  require Git and a complete verified cache. Concurrent hostile mutation after
  validation is outside the cooperative cache model.
- The stage-1 frontend is a subset probe, not a self-hosted compiler.
- Sprig v0.2.0-alpha.1 is a prerelease under Apache-2.0 (`LICENSE`, `NOTICE`),
  not a production stability or numerical correctness guarantee.
