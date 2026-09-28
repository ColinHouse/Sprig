# Implemented JVM interop (v0.4.0-alpha.1)

Import a public class with an alias, then call public constructors, static
methods, instance methods, or fields. The class must live in a named package:
generated Sprig classes are emitted into `sprig.user`, and Java cannot reference
a type from the unnamed package there, so importing such a class is rejected
with `SPR-JVM-CLASS` instead of failing later inside `javac`. Java calls use
positional arguments.
The compiler resolves overloads before Java generation; generated code calls
Java directly. It never initializes a class while `sprig api` or `sprig check`
indexes its metadata.

```sprig
import java.time.LocalDate as LocalDate

let date = LocalDate.of(2026, 9, 25)
if date != null:
    print(date.getYear())
```

`sprig api java.time.LocalDate --json` reports Java and mapped Sprig parameter
and return types, static/instance status, overloads, checked exceptions,
generic signatures, and `usableFromSprig`/`unusableReason`. Use
`--member of` to return only that member's overloads. Metadata also includes
`signatureSupported` and `interopLevel`: `direct` means the signature has a
direct core type mapping; `erased-generic` means the raw signature can be
bound but its generic arguments are not enforced; `sprig-callable` means concrete Sprig Fn slots are checked invariantly; `unsupported` means the
current compiler cannot bind/emit it. The legacy `usableFromSprig` field means
only that binding/emission is possible, not that a generic contract is safe.
Reflected public fields are ordered by field name and then full Java signature;
inherited fields hidden by a same-named declaration remain visible as distinct
rows in that deterministic order.
Java reference and
boxed return values are conservatively nullable. Java reference parameters,
including `Object`, require a non-null Sprig argument because the compiler
does not infer a null contract from the Java type. A Java `List<String>` stays
a Java reference: its generic arguments are displayed but not promoted to an
exact Sprig `List[String]` guarantee.

Primitive mappings: `long -> Int`, `int/short/byte -> Int32`, `double ->
Float`, `float -> Float32`, `boolean -> Bool`, `char -> String`. Boxed return
values map to nullable counterparts. Java `Character`, `Short`, and `Byte`
results are adapted to nullable Sprig `String` and `Int32` values without
losing null. A `char` or `Character` parameter accepts only a single UTF-16
unit Sprig string literal; an arbitrary `String` is rejected because it may
be empty or longer. Direct writes to Java fields needing these adapters are
unsupported; use an explicit Java setter. Narrowing and potentially lossy
numeric conversions require an explicit Sprig operation. Array parameters/results and
varargs are currently unavailable, and `api` labels those members. Java
collection copy/view adapters have not been defined; no implicit conversion
between Java collections and Sprig collections occurs.

Pass a local classpath to all related commands:

```text
sprig api com.example.Widget --classpath lib/widget.jar --json
sprig check app.spr --classpath lib/widget.jar --json
sprig build app.spr --classpath lib/widget.jar -d build/app
sprig run app.spr --classpath lib/widget.jar
```

Manifest `[[jvm]]` dependencies are resolved by `sprig resolve` using Apache
Resolver; `api/check/build/run/doctor` automatically use the locked project JARs.
Repeat `--classpath` or use the platform path separator for additional local
entries. Relative paths resolve against cwd. JDK/runtime classes, then locked
JARs in recorded order, then explicit entries: first application entry wins.
Compiler implementation JARs do not leak into application imports. Missing
explicit entries raise `SPR-JVM-CLASSPATH`; missing/corrupt locked artifacts
raise `SPR-DEP-OFFLINE`/`SPR-DEP-CHECKSUM`. Class initialization occurs only when
the user program executes that class. See [dependencies](DEPENDENCIES.md).

`sprig api` includes Java's declared exception types. Inside a named Sprig
function, checked Java exceptions must be caught or covered by that function's
`throws` declaration. Top-level module statements currently may leave a
checked Java exception uncaught; it then aborts the program at runtime. This
top-level rule is provisional, as described in `KNOWN_LIMITATIONS.md`. Java
library arithmetic and nullability are not magically upgraded to Sprig's
checked numeric or non-null contracts.

## Sprig-owned callable ABI

A source `fn(A) -> R` can be passed to a Java formal `sprig.runtime.Fn1<A,R>`
(and Fn0/Fn2/Fn3) only when its concrete boxed parameter/result types agree
invariantly. `Long` maps to Int, Integer to Int32, Double to Float, Float to
Float32, Boolean to Bool, String to String; other concrete Java references retain
their Java type. `Void` is permitted only as a Unit result. Character/Short/Byte,
arrays, raw Fn types, wildcard/type-variable slots and general parameterized
Java slots are rejected. There is no arbitrary SAM conversion.

Java callback results remain nullable **function values** and must be narrowed
before invocation. Fn generic slots contractually hold non-null values, except
Void as Unit. Generated lambdas reject null incoming arguments and invocation
rejects a null result when the source result type is non-null. Java libraries
can still throw unchecked exceptions; these are runtime failures, not proof of
successful interop. Callable types cannot carry checked effects in this version.
`sprig api` reports source callable signatures and `sprigCallableBoundary`.

Text `sprig run` streams program stdout and inherits stdin. JSON run captures
output until termination to retain one structured result. The CLI stops its
child JVM when it exits, which supports persistent servers in editor terminals.
