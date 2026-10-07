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

## Application: written or inferred from the arguments

```sprig
let box = Box[Int](value=42)          # written type arguments
let value = identity[Int](42)
let some: Option[Int] = Option[Int].Some(value=1)
let none: Option[Int] = Option[Int].None

let inferred = Box(value=42)          # Box[Int], inferred from the arguments
let same = identity(42)               # identity[Int]
let kiwi = Option.Some(value="kiwi")  # Option[String].Some
```

- A call of a generic Sprig function (local or from an imported module, such
  as `lists.sort_by`), a constructor of a generic class (named arguments) and
  a constructor of a generic variant case that has payload fields may leave
  out the `[Type]` arguments. They are then inferred from the argument
  expressions of that call **only**: never from the expected type, the
  assignment target, the enclosing call or the result. There is no
  bidirectional inference.
- Written arguments still work and are used as written. They are all or
  nothing: `Entry[String]` for a two-parameter declaration reports
  `SPR-TYPE-GENERIC-ARITY`, as does a bare `Box` in a type position or
  arguments on a non-generic type. Partial arguments are never inferred.
- Type positions (`let box: Box[Int]`), payloadless cases
  (`Option[Int].None`) and Java generic types (`ArrayList[String]`) always
  write their arguments. A Java method's own type parameters follow the same
  rule as Sprig calls: inferred when the arguments fix every one exactly
  (`Collections.sort(names)`, `List.of(1, 2, 3)`), written otherwise
  (`Collectors.toList[Int]()`); see [JVM interop](../jvm/interop.md).
- Nested applications are ordinary:
  `List[Option[Int]]`, `Map[String, Box[Int]]`, `Box[List[String?]]`.
- Generic types are **invariant**. No `out`/`in`, wildcards or subtyping.

### How arguments determine type parameters

Each argument is matched against its declared parameter or field type,
structurally: a type parameter, `T?`, `List`/`MutableList`, `Map`/`MutableMap`,
function types and generic class and variant applications. A Java generic type
is never looked into.

- **Positions.** An argument passed directly for `T`, an element of a list or
  map literal, and the result of a lambda are *flexible*: the value only has
  to be assignable there. A position inside another type (`List[T]` against
  `List[Int]`, a lambda's written parameter type against `fn(T) -> R`) is
  *exact*, because generic types are invariant.
- **Several arguments.** All exact positions must agree, and every flexible
  argument must be assignable to that type. Without an exact position, the
  narrowest type every flexible argument is assignable to wins, in any
  argument order: `Int32` and `Int` give `Int`, `String?` and `String` give
  `String?` and `Int32?` and `Int` give `Int?` (where a nullable argument is
  accepted), and a variant case value gives its variant (`Option.Some(value=1)`
  makes `T` `Option[Int]`), so two cases of one variant meet at the variant.
- **Weak literals.** An unannotated numeric literal binds a parameter only if
  no other argument binds it. For `generic T: func pair(a: T, b: T) -> List[T]`
  and `small: Int32`, `pair(small, 8)` infers `Int32`; `pair(1, 2)` infers `Int`
  and `pair(1.5, 2.5)` `Float`. Literals of different kinds (`pair(1, 2.5)`)
  disagree, as they do in a list literal. A list or map literal passed for a
  bare `T` is typed as a whole and is weak in the same way: on its own,
  `pair([1], [2])` infers `MutableList[Int]`, as `let xs = [1]` does, but next to
  a `List[Int]` value the literal becomes a `List[Int]` too, so
  `nulls.or_else(config["tags"], ["default"])` works for a
  `Map[String, List[String]]`.
- **No information.** `null`, `[]`, `{}`, a literal that holds only those, and a
  `Unit` result say nothing. A non-empty list or map literal passed for
  `List[T]` or `Map[K, V]` infers from its elements, each on its own.
- **Lambdas.** Lambda parameters are always annotated. A lambda is typed
  without an expected type; its parameter types bind exactly and its body
  binds the function type's result flexibly.
- **Nullability.** A parameter written `T?` matched against `X` or `X?` binds
  `T = X`. A bare `T` matched against `X?` binds `T = X?` only if an explicit
  `[X?]` would be accepted (see the nullability rule below); otherwise
  `T = X`, and the nullable argument is reported by the argument check. Inside
  another type the nullable type is kept, because `List[T]` against
  `List[String?]` can only mean `T = String?`; where `[String?]` is not
  accepted, the call reports `SPR-TYPE-GENERIC-NULLABLE` as the written form
  does.

After inference every argument is checked against the substituted parameter
types exactly as with written arguments, so a literal adapts (the `8` above is
an `Int32`) and a wrong argument gets the usual `SPR-TYPE-MISMATCH` or
`SPR-TYPE-NULLABLE`. The inferred arguments are validated like written ones
(nullability rule, substituted annotations), stored where written ones are,
and every `requires X: Comparable` is checked against them at the call.
Generated Java is the same as for the written form.

A mismatch on an argument whose expected type was inferred from another
argument says so in its hint (`T is String, from argument 2`).

A parameter that no argument determines, or that two arguments give
incompatible types, reports `SPR-TYPE-GENERIC-ARGS-REQUIRED`. The message
names each parameter and why (`argument 1 is an empty list, which says
nothing about T`; `T is String from argument 1 but Bool from argument 2`), and
the hint writes the call with the inferred arguments filled in where known,
spelled as the calling module writes them (`lists.Pair[Int, String]` through
an import alias, a Java class by its import alias, a variant case as its
variant), for example `Write lists.first[Type](...)` or
`Write Result[Int, Type].Ok(...)`. When a parameter is unknown only because an
argument has another shape than its parameter (`lists.sort_by(orders, 5)`) or
is a `Unit` result, that argument is reported instead, as the
`SPR-TYPE-MISMATCH` or `SPR-TYPE-UNIT` it is, because writing the type
arguments would not help. An argument that already has an error of its own
is no evidence, and its error is reported instead of a second one.

Inference first types each lambda and literal argument on its own; a generic
call nested inside it stops there once it knows its own type arguments. The
argument is then checked once more against the inferred types, so checking
stays polynomial in the nesting depth of generic calls instead of doubling
with every level.

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

`Option[Int].Some(value=42)` builds a value, and so does `Option.Some(value=42)`,
which infers `T` from the payload; `Option[Int].None` is a value, not a call,
and has no payload to infer from. A match writes the unapplied case owner; do
not put `[Int]` in a case:

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
deliberately bounded: no wildcard syntax in Sprig (an imported wildcard keeps
its bound and cannot be written through), no capture conversion and no Java
generic inference; method type parameters require explicit arguments, and
recursive/intersection bounds or generic arrays are rejected before codegen.
Java reference results remain conservatively nullable. See
[JVM interop](../jvm/interop.md) for arrays and collection adapters.

## Not implemented

Inference of type arguments from the expected type, inference of Java method
type variables, variance, any user-defined capability,
generic constraints on JVM types, and registry/publishing features. The
manifest and entry-discovery model (`sprig.toml`, `init`, `project`, `deps`,
`run --bin`), schema-5 lockfiles, local/Git and Apache Maven resolution are implemented;
see the current project/dependency documentation.
