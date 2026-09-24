# Known limitations (Stage-0 alpha candidate)

This list describes the Java stage-0 implementation, not every feature proposed
by the Sprig v0.7 design kit.

- The compiler is written in Java and emits Java source before invoking `javac`.
  It does not compile itself and is not self-hosted.
- There is no package manager, Maven dependency resolver, standard-library
  distribution, language server, IDE plugin, debugger, or editor integration.
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
- Build/test evidence currently comes from macOS Apple Silicon with OpenJDK
  26.0.1. Compiler classes are built with `javac --release 17`; Java 17 runtime
  execution still needs validation in CI before it is advertised as verified.
  Until then, the documented runtime requirement is JDK 26 or newer.
- The project has no selected license. This repository and its candidate
  release package are not cleared for public redistribution until the owner
  selects and records a license.
