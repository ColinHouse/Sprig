# Generics (v0.8)

Sprig v0.8 adds user-defined generics with one guiding rule: **declaration and
use are explicit**. There is no inference, no variance and no hidden
conversion. This page is the runnable tour; the authoritative contract is the
[generics reference](/en/reference/GENERICS).

<<< @/snippets/generics.spr

```text
42
```

## Declaration

```sprig
generic T:
    class Box:
        let value: T
```

`generic T:` wraps one class, variant or function. Blocks can declare any
number of parameters (`generic K, V:`), duplicate names are rejected, and
parameters are visible only inside the block — using one after the block is
`SPR-NAME-UNRESOLVED`.

## Use sites write `[Type]`

```sprig
let box = Box[Int](value=42)          # generic constructor
let value = identity[Int](42)          # generic function
let entry = Entry[String, Int](key="age", value=18)
let some: Option[Int] = Option[Int].Some(value=1)
let none: Option[Int] = Option[Int].None
```

`identity(42)` or `Box(value=42)` without type arguments fail with
`SPR-TYPE-GENERIC-ARGS-REQUIRED`; the compiler will not guess. Wrong counts,
including a bare `Box` in a type position or arguments on a non-generic type,
report `SPR-TYPE-GENERIC-ARITY`.

## What a bare `T` can do

Inside a generic declaration, `T` can be assigned, passed, returned and stored
in compatible generic containers. It has **no operators, ordering or
methods**: `value + value` is `SPR-TYPE-OPERAND`. Equality needs an explicit
capability: `requires K: Equatable` makes `==`/`!=` on that parameter legal
with value equality. `requires K: Comparable` is parsed but not implemented
(`SPR-GENERIC-CONSTRAINT`).

## Nullable arguments

`Box[String?]` is fine when the declaration stores `T` directly. If the
declaration applies `?` itself:

```sprig
generic T:
    class Box:
        let value: T?
```

then `Box[String?]` is rejected with `SPR-TYPE-GENERIC-NULLABLE`, because the
declaration already owns the nullable position. `Box[String]` works.

## Generic variants

```sprig
generic T:
    variant Option:
        Some:
            value: T
        None:
```

Construct with `Option[Int].Some(value=1)` and `Option[Int].None`; `match`
stays exhaustive, including for instantiations. Adding a case still breaks
every match that misses it with `SPR-MATCH-NONEXHAUSTIVE`.

## Indexing still works

`values[index]` is indexing, not a type application. The compiler decides from
symbol kinds: a bracket whose base names a generic declaration is a generic
use, otherwise a single plain name is an index. Index-then-call
(`handler[0](arg)`) is not a valid v0.8 form.

## JVM lowering

Generics are erased and boxed in generated Java: a type parameter becomes
`Object`, generic classes are raw at the JVM level, and the compiler inserts
boxing/casts. See the [contract](/en/reference/GENERICS) for the exact rules.

## Not in v0.8

Multiple type parameters, inference, variance, capability implications and
the project/dependency system. See
[Known limitations](/en/reference/KNOWN_LIMITATIONS).
