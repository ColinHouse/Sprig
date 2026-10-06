# Generics

Sprig's generics follow one rule: **be explicit where you declare them and where you use them.** The compiler doesn't infer type arguments, generics have no variance, and one generic type never converts into another behind your back.

Here's a complete example first:

<<< @/snippets/generics.spr

```text
42
```

## Declaring a generic

Put a class, variant or function inside a `generic` block:

```sprig
generic T:
    class Box:
        let value: T
```

- A block can declare several type parameters, as in `generic K, V:`. The names have to be distinct.
- Type parameters only exist inside the block; using `T` outside it is rejected with `SPR-NAME-UNRESOLVED`.

## Writing the type arguments

```sprig
let box = Box[Int](value=42)          # generic class
let value = identity[Int](42)          # generic function
let entry = Entry[String, Int](key="age", value=18)
let some: Option[Int] = Option[Int].Some(value=1)
let none: Option[Int] = Option[Int].None
```

Leave the type arguments out, and the compiler won't guess them for you:

<<< @/snippets/guide/generics_missing_args.spr

```text
SPR-TYPE-GENERIC-ARGS-REQUIRED [TYPE] main.spr:5:7: Function 'identity' is generic; a call requires explicit type arguments, e.g. identity[Type](...)
  hint: Write identity[Int](...); the arguments you passed say which type. Sprig does not infer type arguments.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

The hint names the type the argument implies, but it is still you who writes it: `identity[Int](42)`. With `--json`, the same rewrite is in the diagnostic's `suggestedEdits`.

The wrong number of type arguments is rejected with `SPR-TYPE-GENERIC-ARITY`. That includes a bare `Box` in a type position and type arguments on a type that isn't generic.

## What you can do with a type parameter

Inside a generic declaration, a value of type `T` can be assigned, passed, returned and stored in a compatible generic container. By default it has no operators and no methods: `value + value` is rejected with `SPR-TYPE-OPERAND`.

There are two capabilities you can add, both written first in the function body. The first is equality: start the function with `requires T: Equatable`, and `==` and `!=` work on `T`, comparing by value:

<<< @/snippets/guide/generics_equatable.spr

```text
true
false
```

The second is ordering: with `requires T: Comparable`, you can use `<`, `<=`, `>` and `>=` on `T`, and call `sort()` on a `MutableList[T]`:

<<< @/snippets/guide/generics_comparable.spr

```text
9
pear
[0.5, 1.0, 2.5]
```

- The Comparable types are `Int`, `Int32`, `Float`, `Float32`, `Decimal`, `BigInt` and `String`, the types that already have `<`. A type parameter of the calling function qualifies too, if that function also declares `requires X: Comparable`.
- A comparison means exactly what it means on the concrete type; for example, every comparison with a Float NaN is false.
- Every call is checked. Passing a type without ordering is an error at the call:

<<< @/snippets/guide/generics_comparable_bool.spr

```text
SPR-GENERIC-CONSTRAINT [TYPE] main.spr:8:13: Type argument 'Bool' for T is not Comparable, which 'larger' requires (expected Comparable type, actual Bool)
  hint: Comparable types are Int, Int32, Float, Float32, Decimal, BigInt and String; for other types, pass an explicit comparison function.
```

The two capabilities are independent: `Comparable` doesn't grant `==`, so a function that needs both writes both `requires` lines. Any other capability name is rejected with `SPR-GENERIC-CONSTRAINT`.

## Nullable type arguments

When the declaration stores `T` directly, `Box[String?]` is fine. But if the declaration already writes `T?`:

```sprig
generic T:
    class Box:
        let value: T?
```

then `Box[String?]` is rejected with `SPR-TYPE-GENERIC-NULLABLE`, because the declaration already decides whether that spot can be null. Write `Box[String]` instead.

## Generic variants

Variants can be generic too. You write the type argument when you create a value, and only the case name in a `match` branch:

<<< @/snippets/guide/generics_option.spr

```text
some 1
none
```

The `match` still has to be exhaustive. Add a case to `Option` later, and every `match` that misses it is rejected with `SPR-MATCH-NONEXHAUSTIVE`.

## When brackets mean indexing

`values[index]` is still indexing, not a type argument. The compiler decides by the name in front of the brackets: if it names a generic declaration, the brackets hold type arguments; otherwise they're an index. Indexing and then calling the result (`handler[0](arg)`) isn't supported.

## What it compiles to

In the generated Java, generics are erased: a type parameter becomes `Object`, generic classes are raw types on the JVM, and the compiler adds boxing and casts where needed. The [generics reference](/en/reference/language/generics) has the exact rules.

Imported Java classes can take concrete type arguments too, such as `ArrayList[String]`; see [JVM interop](/en/guide/jvm-interop).

## Not implemented yet

Type inference, variance and user-defined capabilities don't exist yet. The full list is in [known limitations](/en/reference/language/known-limitations).
