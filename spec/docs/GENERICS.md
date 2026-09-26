# Sprig v0.8 generics (implemented contract)

This document describes the generic type system the stage-0 compiler
implements. It is narrower than a general generics design and deliberately so.

## Declaration

```sprig
generic T:
    class Box:
        let value: T
```

- `generic T:` wraps **one** declaration: a class, variant or function.
- Blocks may declare any number of parameters: `generic K, V:`. There is no
  arbitrary limit, and there is no separate single-parameter type system.
- Duplicate names in one list (`generic T, T:`) report `SPR-NAME-DUPLICATE`.
- Parameters are visible only inside the block; they never leak, and a name or
  type outside the block cannot refer to them (`SPR-NAME-UNRESOLVED`).

## Application is always explicit

```sprig
let box = Box[Int](value=42)          # generic class constructor
let value = identity[Int](42)          # generic function
let some: Option[Int] = Option[Int].Some(value=1)
let none: Option[Int] = Option[Int].None
```

- Every generic use site writes all `[Type]` arguments. There is no
  inference: `identity(42)` and `Box(value=42)` report
  `SPR-TYPE-GENERIC-ARGS-REQUIRED`; `Entry[String]` for a two-parameter
  declaration reports `SPR-TYPE-GENERIC-ARITY`, as does a bare `Box` in a
  type position or arguments on a non-generic type. Partial arguments are
  never inferred from context.
- Nested applications are ordinary:
  `List[Option[Int]]`, `Map[String, Box[Int]]`, `Box[List[String?]]`.
- Generic types are **invariant**. No `out`/`in`, wildcards or subtyping.

## Type parameters have almost no abilities

Inside a generic declaration, `T` supports assignment, passing, returning and
being placed in compatible generic containers. It has no operators, ordering
or methods, and equality only when the enclosing function declares it:
`value + value` is `SPR-TYPE-OPERAND`, and `a == b` is rejected unless the
function contains `requires K: Equatable`, in which case equality is checked
with value equality on the boxed representation.

Capabilities are deliberately minimal:

| Capability | Status |
|---|---|
| `Equatable` | implemented for `<T>` equality under `requires X: Equatable` |
| `Comparable` | parsed, but not implemented (`SPR-GENERIC-CONSTRAINT`) |

Any `requires` clause naming a parameter that is not in scope reports
`SPR-NAME-UNRESOLVED`.

## Nullability rule (Practical Strict)

- `List[String?]`, `Box[String?]` and `Option[String?]` are legal.
- If the declaration itself applies `?` to the parameter:

  ```sprig
  generic T:
      class Box:
          let value: T?
  ```

  then `Box[String]` is legal but `Box[String?]` is rejected with
  `SPR-TYPE-GENERIC-NULLABLE`. The compiler does not flatten `T?` or invent
  `String??`; the declaration already owns the nullable position.

## Generic variants

Generic variants use the expanded payload form:

```sprig
generic T:
    variant Option:
        Some:
            value: T
        None:
```

`Option[Int].Some(value=42)` builds a value; `Option[Int].None` is a value, not
a call. Exhaustive `match` works on instantiations and a new case still
reports `SPR-MATCH-NONEXHAUSTIVE` for every match that misses it.

## Relationship to indexing

`values[index]` remains ordinary indexing. The parser records a bracket payload
that parses as type references as a candidate; the checker decides:

- if the base names a generic declaration, the bracket is a type application;
- otherwise a single plain name is indexing, exactly as in v0.7.

One consequence: `handler[index](arg)` (index, then call the result) is not a
valid v0.8 form and is diagnosed; index-then-call was never usable with the
current function-type model.

## JVM lowering

Generics are erased and boxed in generated Java:

- a type parameter becomes `java.lang.Object`;
- generic classes and variants are emitted as raw classes;
- the compiler inserts boxing at generic argument positions and casts plus
  unboxing at generic result positions;
- `instanceof` stays raw, so match exhaustiveness is unaffected.

Sprig types are preserved at the source level: `Box[Int]` is `Int` to Sprig
even though the JVM sees a boxed value.

## Not part of v0.8

Generic inference, variance, `Comparable` and any user-defined capability,
generic constraints on JVM types, and registry/dependency features. The
project system (`sprig.toml`, `sprig.lock`, dependencies) is not implemented
in this tree.
