# Sprig generics (implemented contract)

> Sole current generics reference. The website and SDK use this source.

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
being placed in compatible generic containers. It has no operators or methods,
and equality or ordering only when the enclosing function declares them:
`value + value` is `SPR-TYPE-OPERAND`; `a == b` is rejected unless the
function starts with `requires K: Equatable`, in which case equality is checked
with value equality on the boxed representation; `a < b` is rejected unless it
starts with `requires K: Comparable`.

Capabilities are deliberately minimal:

| Capability | Status |
|---|---|
| `Equatable` | implemented for `<T>` equality under `requires X: Equatable` |
| `Comparable` | implemented for `< <= > >=` and `sort()` under `requires X: Comparable` |

The set of capabilities is closed; any other name reports
`SPR-GENERIC-CONSTRAINT`. Capabilities are independent: `Comparable` does not
grant `==`, so a function that needs both writes both clauses.

### Comparable

`requires T: Comparable` grants the ordering operators `<`, `<=`, `>` and `>=`
between two values of type `T`, and `sort()` on a `MutableList[T]`.

- **Type arguments.** Comparable types are exactly the types that already have
  ordering operators: `Int`, `Int32`, `Float`, `Float32`, `Decimal`, `BigInt`
  and `String`, plus a type parameter of the calling function that itself
  declares `requires X: Comparable`. Nullable types, `Bool`, classes, enums,
  variants, collections and Java types are not Comparable. Every call that
  instantiates the requirement is checked: a generic function call, a method
  call on a generic class instance, or an unqualified method call inside the
  class. A violation reports `SPR-GENERIC-CONSTRAINT` at that call.
- **Meaning.** A generic comparison means exactly what the same operator means
  on the concrete type: numeric order for `Int`, `Int32`, `Decimal` and
  `BigInt`; IEEE comparison for `Float` and `Float32` (every comparison with
  NaN is false, and `-0.0` and `0.0` compare equal); UTF-16 code unit order for
  `String`.
- **Sorting.** `sort()` on a `MutableList[T]` uses the same ascending, stable
  order as `sort()` on a concrete list, which for `Float` places `-0.0` before
  `0.0` and NaN last. `sort()` accepts `Bool` and every Comparable element type.
- **Lowering.** Generated Java calls `SprigRuntime.lessThan`, `lessOrEqual`,
  `greaterThan` or `greaterOrEqual` on the erased values; those helpers
  dispatch on the boxed type to keep the concrete semantics above.

Clauses must form a leading prefix of the function body. A late or nested
clause reports `SPR-GENERIC-CONSTRAINT` and grants no capability.

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
  `String??`; the declaration already owns the nullable position. This applies
  recursively to written annotations, local/lambda types and nested generic
  applications, and is independent of declaration order.

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
a call. A match writes the unapplied case owner; do not put `[Int]` in a case:

```sprig
func read(option: Option[Int]) -> Int:
    match option:
        case Option.Some as some:
            return some.value
        case Option.None:
            return 0
```

Exhaustive `match` works on instantiations and a new case still
reports `SPR-MATCH-NONEXHAUSTIVE` for every match that misses it.

## Relationship to indexing

`values[index]` remains ordinary indexing. The parser records a bracket payload
that parses as type references as a candidate; the checker decides:

- if the base names a generic declaration, the bracket is a type application;
- otherwise a single plain name is indexing, as in the design kit.

One consequence: `handler[index](arg)` (index, then call the result) is not a
valid form and is diagnosed; index-then-call was never usable with the
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

## Imported Java generics

Explicit type arguments also apply to imported Java classes and methods
(`ArrayList[String]`, `Host.method[String](value)`). Concrete arguments are
preserved in Sprig types and in `sprig api` metadata; class type variables
resolve through the receiver and its inherited hierarchy. The profile is
deliberately bounded: no wildcard syntax, no capture conversion and no Java
generic inference; method type parameters require explicit arguments, and
recursive/intersection bounds or generic arrays are rejected before codegen.
Java reference results remain conservatively nullable. See
[JVM interop](../jvm/interop.md) for arrays and collection adapters.

## Not implemented

Generic inference, variance, any user-defined capability,
generic constraints on JVM types, and registry/publishing features. The
manifest and entry-discovery model (`sprig.toml`, `init`, `project`, `deps`,
`run --bin`), schema-5 lockfiles, local/Git and Apache Maven resolution are implemented;
see the current project/dependency documentation.
