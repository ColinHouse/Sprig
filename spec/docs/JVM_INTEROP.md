# JVM bridge constraints (design only)

- Sprig targets **Java source then javac** initially. Sprig is not a Java syntax superset.
- Bind external calls against real, pinned JDK/JAR metadata; resolve exact method and overload at compile time, not by a guessed name or unbounded reflection at runtime.
- For editor/agent queries, inspect JVM metadata without class initialization or executing third-party code. The alpha.2 development compiler implements `sprig api ... --json`; current behavior is documented in `docs/JVM_INTEROP.md` and may cover less than this design target.
- Maintain original Sprig source spans through lowering and translate Java compiler errors where possible. Java compiler output does not prove Sprig-specific semantics were checked.
- Distinguish native `List[T]` immutability from Java `java.util.List`; explicit adapters define copy/view behavior. JNI/FFI support is out of scope.
- Handle primitive boxing, overflow/narrowing, nullability, Java method overloads, checked exceptions, generic erasure, varargs, resource ownership, and Kotlin-generated JVM signatures deliberately. Unknown or ambiguous conversions produce diagnostics rather than `Any` fallback.
- Sprig constructor calls are named-only and can be lowered to a generated Java constructor/builder; imported Java constructors remain positional and can be overloaded.
- `variant` can lower to a sealed interface plus nested final case classes/records (depending on pinned JDK), but this is a backend implementation option, not a requirement on Sprig source syntax.
