# Implemented JVM interop (alpha.2 development)

Import a public class with an alias, then call public constructors, static
methods, instance methods, or fields. Java calls use positional arguments.
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
bound but its generic arguments are not enforced; `unsupported` means the
current compiler cannot bind/emit it. The legacy `usableFromSprig` field means
only that binding/emission is possible, not that a generic contract is safe.
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

Repeat `--classpath` or use the platform path separator. Relative paths are
resolved against the command's current working directory. The compiler/JDK
parent loader wins over user entries; then the first user entry wins duplicate
classes. Missing entries raise `SPR-JVM-CLASSPATH`. This is a deterministic
local classpath, not a Maven resolver or package manager. Third-party class
initializers execute only if the user program actually executes the class.

`sprig api` includes Java's declared exception types. Inside a named Sprig
function, checked Java exceptions must be caught or covered by that function's
`throws` declaration. Top-level module statements currently may leave a
checked Java exception uncaught; it then aborts the program at runtime. This
top-level rule is provisional, as described in `KNOWN_LIMITATIONS.md`. Java
library arithmetic and nullability are not magically upgraded to Sprig's
checked numeric or non-null contracts.
