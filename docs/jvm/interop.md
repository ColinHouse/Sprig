# Implemented JVM interop (v0.5.0-beta.1)

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

For Java framework callbacks, `conform C to J` declares a foreign JVM
conformance between an existing Sprig class and an imported Java interface; see
[foreign JVM conformance](../jvm/conformance.md). That declaration adds no methods
and performs no adaptation.

`sprig api java.time.LocalDate --json` reports Java and mapped Sprig parameter
and return types, static/instance status, overloads, checked exceptions,
generic signatures, and `usableFromSprig`/`unusableReason`. Use
`--member of` to return only that member's overloads. `interopLevel` is the
shared classification the checker uses: `direct` (no generic or array shape),
`concrete-generic` (every argument is concrete and preserved), `opaque-array`
(array values cross unchanged), `adaptable` (an explicit collection adapter is
available), `sprig-callable` (concrete Fn0..Fn3 slots), `erased-generic`
(generic information exists but this raw context binds the erased boundary) and
`unsupported`. Additive machine-readable fields are
`interopReasonCodes` (stable ids such as `varargs-unsupported`,
`wildcard-unsupported`, `raw-generic-boundary`,
`explicit-type-arguments-required`, `generic-array-unsupported`,
`generic-bound-unsupported`, `generic-wrapper-unsupported`,
`array-source-syntax-unavailable`, `bridge-superseded`), `parameterTypeShapes`/`returnTypeShape` (recursive
`class`/`parameterized`/`type-variable`/`wildcard`/`generic-array`/`array`
shapes), `adaptation` (the explicit helper when one exists),
`sprigBoundaryType`, and method `typeParameters`. The legacy `usableFromSprig`
field means only that binding/emission is possible, not that a generic contract
is safe. Reflected public fields are ordered by field name and then full Java
signature; inherited fields hidden by a same-named declaration remain visible
as distinct rows in that deterministic order.
Java reference and
boxed return values are conservatively nullable. Java reference parameters,
including `Object`, require a non-null Sprig argument because the compiler
does not infer a null contract from the Java type. Concrete generic arguments
are preserved: `List<String>` maps to `java.util.List[String]?`, which is
distinguishable from a Sprig native `List[String]` in metadata and diagnostics.

Primitive mappings: `long -> Int`, `int/short/byte -> Int32`, `double ->
Float`, `float -> Float32`, `boolean -> Bool`, `char -> String`. Boxed return
values map to nullable counterparts. Java `Character`, `Short`, and `Byte`
results are adapted to nullable Sprig `String` and `Int32` values without
losing null. A `char` or `Character` parameter accepts only a single UTF-16
unit Sprig string literal; an arbitrary `String` is rejected because it may
be empty or longer. Direct writes to Java fields needing these adapters are
unsupported; use an explicit Java setter. Narrowing and potentially lossy
numeric conversions require an explicit Sprig operation.

## Arrays

Java arrays are opaque foreign values. A public array parameter or result is
`opaque-array`: receive it, null-check it, pass it unchanged to another Java
member expecting a compatible array class, or return it through another call.
Overload resolution distinguishes `byte[]`, `int[]`, `String[]` and `Object[]`,
and array covariance follows the actual JVM class. Sprig has no array literal,
annotation, indexing, assignment or iteration syntax; those attempts are
rejected before `javac` (`SPR-SYNTAX-ERROR` or `SPR-TYPE-OPERAND`). Varargs stay
a different invocation contract and remain unsupported
(`varargs-unsupported`).

`byte[]` is the common binary boundary. `sprig.runtime.jvm.HostBytes` provides
explicit helpers: `utf8(String) -> byte[]`, `utf8String(byte[]) -> String`
(strict UTF-8; malformed bytes raise a runtime error instead of replacing),
`length(byte[]) -> Int` and lowercase `hex(byte[]) -> String`. Conversion is
never implicit and `byte[]` remains a JVM value.

## Concrete Java generics

Explicit type arguments can be applied to imported Java classes and methods:

```sprig
import java.util.ArrayList as ArrayList
let values = ArrayList[String]()
values.add("x")
let first = values.get(0)          # String?
```

`Box[String]`, `List[Map[String, Int32]]` and explicit generic methods such as
`Host.method[String](value)` preserve their concrete arguments. Class type
variables resolve through the receiver, including inherited interfaces and
superclasses (`Source[String]` reached through `StringSource`). Java reference
results stay conservatively nullable: `ArrayList[String].get` is `String?`, not
`String`. Method type parameters are never inferred; a generic method requires
explicit arguments, and a call that omits them is rejected with
`explicit-type-arguments-required`. Recursive or intersection bounds
(`generic-bound-unsupported`) and generic arrays `T[]`
(`generic-array-unsupported`) are rejected, and imported class
type-parameter bounds are validated at check time (simple class/interface
bounds only).

Raw evidence never becomes concrete evidence. A raw generic value cannot be
assigned to, or passed where, a concrete parameterized type is expected;
concrete arguments are checked invariantly and subtype conversions project
arguments through the hierarchy (`ArrayList[String]` is accepted as
`List[String]`, `ArrayList[Int32]` is not). Concrete-to-raw stays an erased
boundary: a raw receiver such as `ArrayList()` keeps its previous erased
behavior and `api` labels it `erased-generic`. The same rule covers the Sprig
collection images: a raw `SprigList`/`SprigMap` result never becomes
`List[T]`/`Map[K,V]`; only a concrete `SprigList[T]` matches, and mismatched
arguments are rejected. Concrete generic targets such as
`Comparable[String]` also use the projection, so `String` is accepted while
`Int32` or an unrelated reference is not.

Shape inspection is recursive for members and fields: a wildcard or a generic
array nested inside a parameterized type (`List<?>`, `List<T[]>`) is rejected
with `wildcard-unsupported`/`generic-array-unsupported` in both `sprig api`
and the checker. `Short`, `Byte` and `Character` need value adapters, so they
are rejected in generic argument position with `generic-wrapper-unsupported`;
direct Java calls keep their scalar adapters.

## Collection adapters

Java collections never convert implicitly to Sprig collections and a Sprig
list/map is never silently accepted for a Java collection formal. The explicit
adapters live in `@std/jvm.spr`:

```sprig
import "@std/jvm.spr" as jvm

let foreign = SomeJavaApi.names()
if foreign != null:
    let names = jvm.list_snapshot[String](foreign)   # immutable Sprig List[String]
    let copy = jvm.list_copy[String](names)          # independent java.util.ArrayList
    SomeJavaApi.acceptNames(copy)
```

The façade ties the element type to the source (`list_snapshot(source:
JavaList[T])`, `map_snapshot(source: JavaMap[K, V])`), so
`jvm.list_snapshot[String]` of a `List[Int32]` is rejected by the checker, not
at runtime. `list_snapshot`/`map_snapshot` copy in list order (map iteration
order) and validate non-null contents: a Java null element/key/value raises a
runtime error instead of leaking into a non-null Sprig collection. `list_copy`/
`map_copy` build independent `ArrayList`/`LinkedHashMap` copies; later mutation
on either side is not visible on the other. The adapters are the only bridge:
no implicit assignment conversion is added.

## Wrapping a library

`sprig wrap` turns an imported Java class into ordinary, editable Sprig source
using the same classpath and the same shared support classification as
`sprig api`; unsupported members are skipped with structured reason codes
instead of guessed. See [the wrapper generator](../jvm/wrap.md) for the command,
mapping policy and overwrite rules. For framework builds (dependency and
classpath owned by a host build system such as Gradle/Loom), the website guide
[Fabric / JVM framework integration](https://colinhouse.github.io/Sprig/en/guide/fabric) records a
verified end-to-end wiring, while the mechanics remain framework-independent.

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
the user program executes that class. See [dependencies](../projects/dependencies.md).

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
