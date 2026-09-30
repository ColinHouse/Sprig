# JVM Interoperability

Sprig compiles through Java source, so JDK and third-party JVM classes are
available through an explicit import alias. The rules are conservative on
purpose: where Java gives no nullness information, Sprig assumes a reference
result may be `null`, and Java reference parameters are treated as non-null.

The authoritative contract is [JVM interop](/en/reference/JVM_INTEROP); this
page is the runnable tour. For host build integration (Gradle/Loom, Maven,
in-house builds), see
[Fabric / JVM framework integration](/en/guide/fabric).

## Importing Java classes

<<< @/snippets/jvm_interop.spr

```text
9
4
Sprig!
2026
25
```

The import form `import java.lang.Math as Math` binds the simple name `Math` in
the module. Constructors, static methods, instance methods and static fields are
all callable through the alias. Overloads are resolved against the argument
types; an ambiguous or non-matching call is `SPR-JVM-AMBIGUOUS` or
`SPR-JVM-MEMBER`.

## Type mapping

| Java | Sprig (primitive) | Sprig (boxed/reference) |
|---|---|---|
| `long` | `Int` | `Int?` |
| `int`, `short`, `byte` | `Int32` | `Int32?` |
| `double` | `Float` | `Float?` |
| `float` | `Float32` | `Float32?` |
| `boolean` | `Bool` | `Bool?` |
| `char` | `String` | `String?` |
| `void` | `Unit` | — |
| `String` and other reference types | — | nullable Sprig type (`T?`) |

Primitive results are non-null. Reference and boxed results are treated as
nullable and must be checked before use:

```sprig
let version = System.getProperty("java.version")
if version != null:
    print(version.length() > 0)
```

Java reference parameters are conservatively treated as non-null, so a `T?`
argument must be narrowed first. `Object`-typed parameters, which accept null,
are the documented exception. The compiler does not read type-use nullability
annotations.

A `char`/`Character` parameter accepts only a one-UTF-16-unit String literal:
`"a"` is accepted, `"ab"` and `"😀"` are rejected because one Java `char`
cannot represent a supplementary code point. This differs from Sprig String
position semantics, which count Unicode code points
(see `sprig help strings --json`).

## Checked exceptions

A Java checked exception can appear in a Sprig `throws` clause and be caught:

<<< @/snippets/jvm_exceptions.spr

```text
example.com
bad uri
```

## Arrays and bytes

Java arrays are **opaque foreign values**: receive them, null-check them, pass
them unchanged to another Java member expecting a compatible array class, or
return them through another call. Overload resolution distinguishes `byte[]`,
`int[]`, `String[]` and `Object[]`, and array covariance follows the actual JVM
class. Sprig has no array literal, annotation, indexing, assignment or
iteration syntax; those are rejected before `javac` (`SPR-SYNTAX-ERROR` or
`SPR-TYPE-OPERAND`). Varargs remain a different invocation contract and are
unsupported.

`byte[]` is the common binary boundary; `sprig.runtime.jvm.HostBytes` provides
explicit helpers:

```text
HostBytes.utf8(String) -> byte[]
HostBytes.utf8String(byte[]) -> String   # strict UTF-8; malformed bytes fail at runtime
HostBytes.length(byte[]) -> Int
HostBytes.hex(byte[]) -> String          # lowercase hex
```

Conversion is never implicit and `byte[]` remains a JVM value.

## Concrete Java generics

Explicit type arguments can be applied to imported Java classes and generic
methods:

```sprig
import java.util.ArrayList as ArrayList

let values = ArrayList[String]()
values.add("x")
let first = values.get(0)          # String?
```

`Box[String]`, `List[Map[String, Int32]]` and `Host.method[String](value)`
preserve their concrete arguments; class type variables resolve through the
receiver hierarchy (`Source[String]` reached through `StringSource` works too).
Java reference results stay conservatively nullable, so
`ArrayList[String].get` is `String?`. Method type parameters are never
inferred: a generic method requires explicit arguments. Raw evidence never
becomes concrete evidence: a raw generic value cannot be assigned to, or passed
where, a concrete parameterized type is expected; `ArrayList[String]` is
accepted as `List[String]`, `ArrayList[Int32]` is not. Wildcards and generic
arrays (`T[]`) are rejected with stable `interopReasonCodes`.

## Explicit collection adapters

Java collections never convert implicitly to Sprig collections, and a Sprig
`List`/`Map` is never silently accepted for a Java collection formal. The
adapters live in `@std/jvm.spr`:

```sprig
import "@std/jvm.spr" as jvm

let foreign = SomeJavaApi.names()
if foreign != null:
    let names = jvm.list_snapshot[String](foreign)   # immutable Sprig List[String]
    let copy = jvm.list_copy[String](names)          # independent java.util.ArrayList
    SomeJavaApi.acceptNames(copy)
```

`list_snapshot`/`map_snapshot` copy in order and validate non-null contents: a
Java null element/key/value raises a runtime error instead of leaking into a
non-null Sprig collection. `list_copy`/`map_copy` build independent
`ArrayList`/`LinkedHashMap` copies; later mutation on either side is invisible
to the other. The element type is tied to the source, so a mismatch is rejected
at check time.

## Query first, wrap when needed

Ask `sprig api` before writing integration code: it reports `interopLevel`,
`interopReasonCodes`, `adaptation` and recursive generic shapes. To bring an
ecosystem class into Sprig, `sprig wrap` generates **ordinary editable source**
(it refuses to overwrite without `--force` and checks the file under the same
classpath before writing):

```bash
sprig api com.example.Client --classpath lib/client.jar --json
sprig wrap com.example.Client --out src/main/sprig/client.spr --classpath lib/client.jar --json
```

See the [wrapper generator contract](/en/reference/WRAP) for the full policy.

## What interop does not cover

- **Varargs**: a different invocation contract; unsupported.
- **Source array syntax**: no array literal/annotation/indexing/iteration; arrays
  cross only as opaque values.
- **Wildcard shapes**: not modelled by the current profile; members containing
  them are rejected with structured reasons. Nested builder APIs such as
  Brigadier need a narrow Java adapter (see
  [framework integration](/en/guide/fabric)).
- **Generic inference**: no inference or variance; type arguments are explicit.
- **Annotations**: Java type-use nullability annotations are not read.
- **JVM arithmetic**: a Java method that overflows an `int` does not raise
  Sprig's numeric error.
- `short`/`byte` formals require an explicit checked conversion that is not
  offered yet; pass an `Int32` instead.

See [Known limitations](/en/reference/KNOWN_LIMITATIONS) and the Chinese
[已知限制](/reference/known-limitations).
