# Known limitations (Stage-0 v0.2.0-alpha.1 development)

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
- The project model exists (`sprig.toml` discovery, defaults, `sprig init`,
  `sprig project --json`, `sprig deps --json`, explicit-file priority,
  `sprig run --bin`), but **dependency resolution is not implemented**:
  declared local/Git/Maven dependencies are listed with `resolved: false` and
  `SPR-PROJECT-UNSUPPORTED`; there is no `sprig.lock`, no offline cache and no
  project-aware `sprig api` classpath yet. JVM jars still require `--classpath`.
- There is no package manager, Maven dependency resolver, standard-library
  distribution, language server, IDE plugin, debugger, or editor integration.
  Explicit local JAR/directory `--classpath` is available for `check`, `build`,
  `run`, and `api`; no dependencies are downloaded automatically.
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
- Local v0.8-dev build/test evidence comes from macOS Apple Silicon with
  OpenJDK 17.0.19 and 26.0.1; earlier alpha.2 work also passed hosted Linux CI
  on both JDKs and the documentation job. Compiler classes are built with
  `javac --release 17`, so the supported runtime is JDK 17 or newer; no other
  platform or architecture has been exercised locally.
- The project is licensed under Apache-2.0 (`LICENSE` and `NOTICE`). The
  latest published prerelease is `v0.1.0-alpha.1`; the `0.2.0-alpha.1`
  development tree is not released. Manifest commands exist; dependency
  resolution, a lockfile and the integrated stage-1 project are still absent.
