# Generics

Sprig's generics are deliberately small. You declare type parameters in a `generic` block, a call works out its type arguments from the arguments you pass, generics have no variance, and one generic type never converts into another behind your back.

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

## Type arguments

Most of the time you don't write them. A call takes its type arguments from the arguments you pass:

```sprig
let box = Box(value=42)                    # Box[Int]
let value = identity("pear")               # identity[String]
let entry = Entry(key="age", value=18)     # Entry[String, Int]
let some = Option.Some(value=1)            # Option[Int].Some
let groups = lists.group_by(orders, fn(o: Order) => o.customer)   # group_by[Order, String]
```

You can always write them yourself, and then yours are used. It's all or nothing: once you write one, you write every one, in declaration order. Some places always need them:

```sprig
let box = Box[Int](value=42)
let none = Option[Int].None                # a case without a payload has no arguments to look at
let entries: List[Entry[String, Int]] = [] # in a type, you always write them
```

Here's how the compiler works them out:

- Only the call's arguments count. The type you assign the result to, or the parameter you pass it to, never does.
- A plain number counts only when nothing else says what the type is. If `small` is an `Int32`, `lists.sorted([small, 8])` sorts `Int32` values, and the `8` becomes an `Int32` too. `lists.sorted([1, 2])` on its own sorts `Int` values.
- `null`, `[]` and `{}` say nothing. A list or map literal with elements counts by its elements.
- A lambda's parameter types are written, so they count as written; its body gives the result type.
- When arguments give different types, the one the others fit into wins: `Int32` and `Int` make `Int`, and two cases of one variant make the variant. If there's no such type, the call is an error.
- Java methods and Java generic types such as `ArrayList[String]` always take written type arguments.

After that, every argument is checked against the types the compiler found, exactly as if you had written them. When the arguments can't say, the compiler stops and asks you to write them:

<<< @/snippets/guide/generics_missing_args.spr

```text
SPR-TYPE-GENERIC-ARGS-REQUIRED [TYPE] main.spr:3:22: Cannot infer type argument T of 'lists.first': argument 1 is an empty list, which says nothing about T
  hint: Write lists.first[Type](...) with T spelled out; type arguments are inferred only from a call's arguments, never from where its result goes.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

The `String?` on the left doesn't help, so write `lists.first[String]([])`. When two arguments disagree, the same error names both, for example `T is String from argument 1 but Bool from argument 2`.

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
- Every call is checked, whether you write the type argument or the compiler works it out. Passing a type without ordering is an error at the call:

<<< @/snippets/guide/generics_comparable_bool.spr

```text
SPR-GENERIC-CONSTRAINT [TYPE] main.spr:8:7: Type argument 'Bool' for T is not Comparable, which 'larger' requires (expected Comparable type, actual Bool)
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

When the compiler works the type out, it follows the same rule. A `String?` passed where the declaration writes `T?` makes `T` a `String`, so `Box(value=maybe)` is a `Box[String]`. Passed for a plain `T`, it makes `T` a `String?`, unless the declaration writes `T?` somewhere else; then `T` is `String`, and the nullable argument is reported as it would be with `[String]` written out.

## Generic variants

Variants can be generic too. A case with a payload works out its type arguments from the payload, like a constructor; a case without one, such as `Option[Int].None`, needs them written. In a `match` branch you write only the case name:

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

Inferring type arguments from the expected type, variance and user-defined capabilities don't exist yet. The full list is in [known limitations](/en/reference/language/known-limitations).
