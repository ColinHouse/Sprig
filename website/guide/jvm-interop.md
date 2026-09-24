# JVM Interoperability

Sprig compiles through Java source, so JDK classes are available through an
explicit import alias. The rules below are conservative on purpose: where Java
gives no nullness information, Sprig assumes the value may be null.

## Importing Java classes

<<< @/snippets/jvm_interop.spr

```text
9
4
Sprig!
2026
25
```

The import form `import java.lang.Math as Math` binds the simple name `Math`
in the module. Constructors, static methods, instance methods and static
fields are all callable through the alias. Overloads are resolved against the
argument types; an ambiguous or non-matching call is `SPR-JVM-AMBIGUOUS` or
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
argument must be narrowed first. `Object`-typed parameters, which accept
null, are the documented exception. The compiler does not read type-use
nullability annotations, so these rules do not depend on them.

## Checked exceptions

A Java checked exception can appear in a Sprig `throws` clause and be caught:

<<< @/snippets/jvm_exceptions.spr

```text
String
class not found: does.not.Exist
```

The message shown by the snippet is the exception message; importing the
exception class under an alias keeps the `throws` clause readable.

## What interop does not cover

- Java generic signatures and generic collection element types are not fully
  interpreted; Java collections are not silently converted to Sprig `List` or
  `Map`.
- Arrays, varargs and annotations are limited or unsupported.
- Java type-use nullability annotations are not read.
- JVM arithmetic is not re-checked: a Java method that overflows an `int` does
  not raise Sprig's numeric error.
- `short`/`byte` formals require an explicit checked conversion that is not
  offered yet; pass an `Int32` instead.

These boundaries are part of [Known limitations](/reference/KNOWN_LIMITATIONS)
and the [numeric semantics](/reference/NUMERIC_SEMANTICS).
