# JVM interop

Sprig compiles to Java before it runs, so the JDK's classes and Java libraries from Maven are right there for you to use. This page covers importing and calling Java classes, and the checks Sprig makes where your code meets Java.

The general rule is cautious. Java can't promise that a returned value isn't `null`, so Sprig assumes it might be. In the other direction, nothing you pass to Java may be `null`.

The [JVM interop reference](/en/reference/jvm/interop) has the complete rules. To plug Sprig into an existing build such as Gradle or Loom, see [Gradle integration](/en/guide/gradle) and [Fabric mods](/en/guide/fabric).

## Importing and calling Java classes

<<< @/snippets/jvm_interop.spr

```text
9
4
Sprig!
2026
25
```

- `import java.lang.Math as Math` lets you refer to that class as `Math` in this file. Classes in `java.lang` need an import too; nothing is imported automatically.
- Constructors, static methods, instance methods and static fields are all available through that name.
- When a method is overloaded, Sprig picks the overload by argument types. If none matches you get `SPR-JVM-MEMBER`; if several fit equally well, `SPR-JVM-AMBIGUOUS`.

## Check what Java returns

Objects returned by Java methods, including boxed types such as `String` and `Integer`, are treated as possibly `null`. Check them before use:

<<< @/snippets/guide/jvm_nullable.spr

```text
true
```

Methods that return primitives (`long`, `int`, `double`, `boolean` and so on) never return `null`, so you can use those results directly.

In the other direction, an argument you pass to Java can never be `null`, even when the parameter type is `Object`. Check a `T?` value before you pass it to Java. Sprig doesn't read nullability annotations in Java code.

## Type mapping

| Java | Sprig (primitive) | Sprig (boxed or object) |
|---|---|---|
| `long` | `Int` | `Int?` |
| `int`, `short`, `byte` | `Int32` | `Int32?` |
| `double` | `Float` | `Float?` |
| `float` | `Float32` | `Float32?` |
| `boolean` | `Bool` | `Bool?` |
| `char` | `String` | `String?` |
| `void` | `Unit` | — |
| `String` and other object types | — | the nullable Sprig type `T?` |

A few things to watch for:

- **Numbers are never narrowed for you.** An integer literal that fits can be passed straight to an `int`, `short` or `byte` parameter. A variable is never narrowed silently, though. To pass an `Int` variable to an `int` parameter, convert it with `toInt32Exact()` first. For `short` and `byte` parameters, only literals work for now, because there's no conversion to those types yet.
- **A `char` parameter takes a one-character literal.** `char` and `Character` parameters accept only a string literal of exactly one UTF-16 unit. `"a"` works, but `"ab"` doesn't, and neither does `"😀"`, which one Java `char` can't hold. Sprig's own strings count positions in Unicode code points instead (see `sprig help strings`).

## Catching Java exceptions

A Java checked exception can go in a Sprig `throws` clause and be caught with `catch`:

<<< @/snippets/jvm_exceptions.spr

```text
example.com
bad uri
```

## Java collections and Sprig collections

A Java `List` or `Map` never turns into a Sprig collection by itself, and a Sprig collection is never quietly passed where Java expects one. When you need to convert, use the functions in `@std/jvm.spr`:

<<< @/snippets/guide/jvm_collections.spr

```text
2
3
2
```

- `list_snapshot` and `map_snapshot` copy a Java collection, in order, into a read-only Sprig `List` or `Map`. Whatever happens on the Java side afterwards doesn't affect the snapshot: above, `names` gets a third name, and `snapshot` still has two.
- `list_copy` and `map_copy` go the other way. They copy a Sprig collection into a separate Java `ArrayList` or `LinkedHashMap`, and from then on each side changes on its own.
- A `null` element, key or value in the Java collection causes a runtime error during the copy, so `null` never sneaks into a Sprig collection that doesn't allow it.
- The element type has to match the source; a mismatch is caught at check time.
- All of these functions declare `throws Error`, so handle that when you call them inside a function.

## Generic Java classes

Imported Java classes and generic methods can take concrete type arguments, like `ArrayList[String]()` above.

- Type arguments are kept intact. You can write `List[Map[String, Int32]]` or `Host.method[String](value)`, and type arguments inherited from a superclass or interface are recognized too.
- Results are still treated as possibly `null`, so `get` on an `ArrayList[String]` returns `String?`.
- Type arguments of generic methods aren't inferred, so write them out.
- A raw type (one with no type arguments) can't stand in for a parameterized one. `ArrayList[String]` works as a `List[String]`; `ArrayList[Int32]` doesn't.
- Wildcards (like `List<?>`) and generic arrays (`T[]`) aren't supported; `sprig api` shows the reason code for each one it rejects.

## Arrays and bytes

In Sprig, a Java array is a value you hold as a whole. You can receive it, check it for `null` and pass it unchanged to another Java method that expects a compatible array. You can't create, index, modify or loop over it in Sprig. There's no array syntax, and such code is rejected before javac runs, with `SPR-SYNTAX-ERROR` or `SPR-TYPE-OPERAND`. Overload resolution tells `byte[]`, `int[]`, `String[]` and `Object[]` apart, and whether one array type can be passed as another follows the JVM's own rules.

Binary data usually comes as `byte[]`, and `sprig.runtime.jvm.HostBytes` converts it:

<<< @/snippets/guide/jvm_bytes.spr

```text
5
5370726967
```

| Method | What it does |
|---|---|
| `HostBytes.utf8(text)` | Encodes a string as UTF-8 bytes |
| `HostBytes.utf8String(bytes)` | Decodes UTF-8 bytes into a string; invalid UTF-8 is a runtime error |
| `HostBytes.length(bytes)` | The number of bytes, as an `Int` |
| `HostBytes.hex(bytes)` | The bytes as a lowercase hex string |

You always call these conversions yourself; Sprig never converts implicitly.

## Look it up before you write it

Before writing code against a Java class, ask `sprig api` how Sprig sees it. It lists each member's Sprig signature, whether you can use it and, if not, why. With `--json`, that information is in fields such as `interopLevel`, `interopReasonCodes` and `adaptation`:

```bash
sprig api java.time.LocalDate --member parse
sprig api com.example.Client --classpath lib/client.jar --json
```

If you use a Java class a lot, `sprig wrap` can generate a Sprig wrapper file for it. The result is ordinary source code that you're free to edit:

```bash
sprig wrap com.example.Client --out src/client.spr --classpath lib/client.jar
```

Before writing the file, `wrap` checks the generated code against the same classpath. It won't overwrite an existing file unless you pass `--force`. The [wrapper generator reference](/en/reference/jvm/wrap) has the full rules.

## Not supported yet

- **Varargs**: they use a different calling convention and aren't supported.
- **Array syntax**: there are no array literals, array type annotations, indexing or loops over arrays; arrays can only be passed along.
- **Wildcard types**: members that use wildcards are rejected with a reason. Deeply nested builder APIs such as Brigadier need a small Java adapter; see [Fabric mods](/en/guide/fabric).
- **Generic inference**: write the type arguments yourself; there's no variance either.
- **Nullability annotations**: Java's nullability annotations aren't read.
- **Arithmetic inside Java**: an `int` overflow inside a Java method doesn't raise Sprig's numeric error.

The full list is in [known limitations](/en/reference/language/known-limitations).
