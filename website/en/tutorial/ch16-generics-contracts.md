# 16. Generics and contract classes

In this chapter you will learn:

- why **generics** exist: one class or function serving many types instead of copying code;
- `generic T:` blocks, type parameters, and **inference** from arguments — and when `[T]` must be written out;
- why a bare `T` can do nothing, and what `requires T: Comparable` / `Equatable` add;
- generic variants: writing your own `Option[T]`;
- **contract classes**: methods with no body, and `conform` for classes that keep their promise;
- when to choose a variant and when to choose a contract.

## 16.1 Why generics

A "box" class holding an `Int` and one holding a `String` are two different types:

```sprig
class IntBox:
    let value: Int

class StringBox:
    let value: String
```

Only the field type differs; everything else is copy-paste. Every new element type means another copy, and a fix missed in one of them. **Generics** turn the element type into a parameter:

<<< @/snippets/book/ch16_generics.spr

```text
42
x
age:18
```

Line by line:

- `generic T:` opens a block in which **one** declaration (a class, variant or function) may use the type parameter `T`. `T` is just a name standing for "some type the caller picks".
- `class Box:` with `let value: T`: the field type is `T`.
- `generic K, V:` takes two or more parameters; `Entry` keys by `K` and stores a `V`, which reads naturally as a key-value entry.
- `Box(value=42)` writes no `[Int]`. The compiler **infers** from the arguments: `42` is an `Int`, so `T = Int` and `box` is a `Box[Int]`. The same happens for the generic function `identity`.
- `Box[String](value="x")` writes the type argument out, and the written form wins. It is **all or nothing**: for `generic K, V` you cannot write half of `Entry`'s arguments.
- Inference looks **only at the call's arguments**, never at "where the value goes": not the assignment target, not the `return` type, not the enclosing call's expectation. Type positions (`let box: Box[Int]`) always write their arguments.

| Call | Inferred type arguments | Resulting type |
|---|---|---|
| `Box(value=42)` | `T = Int` | `Box[Int]` |
| `Box[String](value="x")` | written `T = String` | `Box[String]` |
| `Entry(key="age", value=18)` | `K = String, V = Int` | `Entry[String, Int]` |

Cases inference cannot handle are common: `[]`, `{}` and `null` "say nothing".

### Deliberate mistake: two empty lists cannot name `T`

<<< @/snippets/book/ch16_inference_error.spr

```text
SPR-TYPE-GENERIC-ARGS-REQUIRED [TYPE] main.spr:5:7: Cannot infer type argument T of 'pair': argument 1 is an empty list, which says nothing about T
  hint: Write pair[Type](...) with T spelled out; type arguments are inferred only from a call's arguments, never from where its result goes.
```

Both arguments are `[]`, so neither can say what `T` is. The code name says "generic arguments required" and the hint shows the fix: `pair[Int]([], [])` (with whatever element type you meant). If a `List[Int]` value is nearby, an ordinary list literal becomes a `List[Int]` with it, and no explicit argument is needed.

### Deliberate mistake: one type argument too many

<<< @/snippets/book/ch16_arity_error.spr

```text
SPR-TYPE-GENERIC-ARITY [TYPE] main.spr:5:10: Type 'Box' requires exactly 1 type argument but got 2
```

`Box` declares one `T`, but got `[String, Int]`. When reading this error, look at the "requires exactly N" number and count the parameters on the `generic` line.

::: tip Coming from another language?
`generic T: class Box` is Java's `class Box<T>` or C#'s `class Box<T>`; usage is similar, but Sprig's inference is more restrained: it never looks at the expected type (no Java 8 target typing), and generic types are **invariant** — `Box[MutableList[Int]]` does not work as `Box[List[Int]]`, and `List[Int]` and `List[String]` have no subtype relationship.
:::

## 16.2 What a bare `T` can do

The answer is: almost nothing. Generic code knows nothing about `T`, so `+`, `>` and method calls are all rejected:

```sprig
generic T:
    func largest(items: List[T]) -> T:
        var best = items[0]
        for item in items:
            if item > best:
                best = item
        return best
```

The problem is `item > best`. The compiler will not guess whether `T` has an order; you must **declare the capability**:

<<< @/snippets/book/ch16_constraints.spr

```text
9
pear
true
false
```

- `largest` starts its body with `requires T: Comparable`. From that line on, `T` may use `<`, `<=`, `>` and `>=`. Without it, the code above is a compile error.
- `contains` uses `requires T: Equatable`, which unlocks `==` and `!=`; it finds `2` (`true`) and does not find `"c"` (`false`).
- There are exactly two capabilities, and they are independent: to compare both order and equality, write both clauses.
- `requires` must be the **first statement** of the body; it cannot sit in the middle.
- The capability is checked at **every call**: `largest(["pear", "apple"])` is fine because `String` is `Comparable`; `largest([true, false])` is not.

| Capability | Allows | Satisfied by |
|---|---|---|
| `Equatable` | `==`, `!=` | every type (classes by identity, variants by contents, as always) |
| `Comparable` | `<`, `<=`, `>`, `>=`, sorting | `Int`, `Int32`, `Float`, `Float32`, `Decimal`, `BigInt`, `String` |

### Deliberate mistake: forgetting `requires`

<<< @/snippets/book/ch10_constraint.spr

```text
SPR-TYPE-OPERAND [TYPE] main.spr:5:16: Operator '>' is not available for generic type parameter T; this operation needs a supported leading capability
  hint: Use a concrete type, or begin the function with 'requires T: Comparable' for ordering.
```

The error says it plainly: `>` is not available for a bare `T`, and the missing capability is `Comparable`; the fix is to put it in the body's first line.

### Deliberate mistake: the type argument lacks the capability

<<< @/snippets/guide/generics_comparable_bool.spr

```text
SPR-GENERIC-CONSTRAINT [TYPE] main.spr:8:7: Type argument 'Bool' for T is not Comparable, which 'larger' requires (expected Comparable type, actual Bool)
  hint: Comparable types are Int, Int32, Float, Float32, Decimal, BigInt and String; for other types, pass an explicit comparison function.
```

The declaration is fine; the **call** is the problem: `Bool` is not on the Comparable list. The hint lists every type with an order and shows the way out — pass an explicit comparison function (the lambda from chapter 15 comes in handy).

## 16.3 Generic variants: writing an `Option`

Chapter 12's variants express "one of a few cases", but each case's field types are fixed. To make "some value or none" hold any type, use a generic variant. Outside the standard library, the classic example is:

<<< @/snippets/book/ch16_option.spr

```text
42
0
kiwi
```

- A variant inside `generic T:` may use `T`. This one uses the expanded form: after `Some:` comes an indented `value: T`; `None:` has no fields. Chapter 12's compact form (`Some(value: T)`) works here too; the expanded form reads better once there are several fields.
- Construction works as for any variant: `Option.Some(value=42)`. `T` is inferred from `42` as `Int`.
- `Option[Int].None` **has no payload to infer from**, so its type argument must be written. `read` has a concrete result type, and `match` covers both cases exhaustively, as in chapter 12.
- `Option.Some(value="kiwi")` infers `Option[String]`, so `kiwi.value` is a `String`.

Like generic classes, a generic variant is checked again with the concrete types at every use; `match` remains exhaustive.

## 16.4 Contract classes

Sometimes a caller only cares *which methods* an object has, not *which class* it is: logs go to the console or to memory, data to a file or a database. Sprig expresses that with a **contract class** — methods with signatures but no bodies:

<<< @/snippets/contracts.spr

```text
console: a
console: b
2
1
```

Piece by piece:

- Both methods of `class Sink:` end at the colon, with no body. That makes the class a contract: it cannot be constructed with `Sink()`, it may not have fields, and it cannot have a mix of bodiless and bodied methods.
- `conform Console to Sink` declares that `Console` keeps the contract. The compiler checks that `Console` has every method of the contract, with exactly the same parameter and result types, throwing no more than the contract declares.
- From then on, a `Console` value goes wherever a `Sink` is expected. `log_all`'s `sink` parameter is a `Sink`, and it can call only the `write` and `flush` the contract lists.
- `log_all(["a", "b"], Console())`: `Console` prints two lines and returns the count `2`, so the two `console: ...` lines come first and `2` last.
- `log_all(["c"], Memory())`: `Memory` prints nothing and only stores lines; `flush` returns `1`.

**Having a method with the same name is not conformance** — `conform` must be written:

### Deliberate mistake: the methods match, but `conform` is missing

<<< @/snippets/book/ch16_missing_conform.spr

```text
SPR-TYPE-ASSIGN [TYPE] main.spr:8:18: Type mismatch in initializer (expected Sink, actual Console)
  hint: 'Console' does not conform to the contract 'Sink': declare 'conform Console to Sink' after the class, with every method of the contract matched exactly.
```

`Console` does have `write(String) -> Unit`, but without the declaration it has nothing to do with `Sink`. The hint spells out the missing line. (The same omission in a function argument position reports `SPR-TYPE-MISMATCH` with the same message.)

A contract cannot be constructed, and it is not a capability for `requires`:

### Deliberate mistake: constructing a contract

<<< @/snippets/book/ch16_contract_construct.spr

```text
SPR-CLASS-ABSTRACT [TYPE] main.spr:4:12: Contract class 'Sink' has methods without a body and cannot be constructed
  hint: Write a class with those methods and 'conform C to Sink', then construct that class; a value of type Sink is any conforming object.
```

A contract describes "any object that satisfies it"; it has no instances of its own. The hint's plan is exactly this section's `Console` and `Memory`.

### Deliberate mistake: treating a contract as a capability

<<< @/snippets/book/ch16_contract_bound.spr

```text
SPR-GENERIC-CONSTRAINT [TYPE] main.spr:6:9: 'Sink' is a contract, not a capability; a contract is a type, never a bound on a type parameter
  hint: Take 'Sink' as the parameter type instead of a type parameter bounded by it: replace 'T' with 'Sink' in the signature (sink: Sink) and drop the requires clause. Capabilities are the closed set Equatable and Comparable.
```

`requires` accepts only `Equatable` and `Comparable`. For "something that can log", make the parameter type `Sink` directly (as `log_all` does) instead of a bounded type parameter.

A few more rules of the 0.8 language: a contract cannot be generic (write one contract per element type, or a generic class holding a `fn` field); a contract has no default methods, so shared behaviour is an ordinary function taking the contract; `conform` is written in the module that declares the class; and through a contract type a value shows only the contract's methods — there is no downcast and no type test.

**Variant or contract?** When the set of cases is **closed** and each case is handled (`match` must be exhaustive), use a variant; when implementations **keep growing** and callers only work through the methods, use a contract. Chapter 12's `Shape` is the variant shape; the `Sink` here is the contract shape.

::: tip Coming from another language?
A contract is like a Java or C# interface, with differences: no default methods, no generic contracts, no contract extending another contract, and **no implicit (structural) conformance** — the `conform` is explicit and lives in the module that declares the class, so every conformance relationship is readable locally. You will meet `conform`'s other forms in the Java interop chapter (chapter 21).
:::

## 16.5 Summary

- A single class, variant or function inside `generic T:` (or `generic K, V:`) may use the type parameters.
- Calls usually infer `[T]` from their arguments; type positions and payloadless cases such as `None` must write it; inference never uses the expected type, and `[]`/`{}`/`null` give it no information.
- A bare `T` has no operators; `requires T: Comparable` / `Equatable` is the body's first statement and is checked at every call.
- Generic variants accept the expanded or compact case form; a payloadless case writes `[T]`.
- A contract class has method signatures only; `conform C to Contract` declares conformance; without it there is no relationship; a contract cannot be constructed, is not a capability and cannot be generic.
- Closed cases call for a variant, open implementations for a contract.

## 16.6 Exercises

**Exercise 1 (easy)** Write a generic `last_or(items: List[T], fallback: T) -> T`: return `fallback` for an empty list, otherwise the last element. Call it with `[1, 2, 3]` and with an empty list.

Hint: `items[items.size() - 1]` is the last element; the two calls get their type from the arguments.

::: details Answer
<<< @/snippets/book/ch16_ex1.spr

```text
3
none
```
:::

**Exercise 2** Write a `Pair` class with two type parameters, fields `first: A` and `second: B`; construct `Pair(first=1, second="one")` and print both fields.

Hint: `generic A, B:` works like `Entry`; inference fixes both `A` and `B` at once.

::: details Answer
<<< @/snippets/book/ch16_ex2.spr

```text
1
one
```
:::

**Exercise 3** Define a `Greeter` contract (method `greet(name: String) -> String`) and two classes that conform: `Friendly` returns `"hello, " + name`, `Formal` returns `"Good day, " + name`. Write a `welcome` function taking a `Greeter` and call it with one instance of each.

Hint: `conform` follows each class; `welcome` sees only the contract method `greet`.

::: details Answer
<<< @/snippets/book/ch16_ex3.spr

```text
hello, Ada
Good day, Bo
```
:::

**Exercise 4 (hard)** Copy the `Option` variant and add `unwrap_or(option: Option[Int], fallback: Int) -> Int`: return the value for `Some` and `fallback` for `None`. Call it with `Some(value=7)` and `Option[Int].None`.

Hint: constructing `Some` infers `T` from `7`; `None` has no payload and writes `Option[Int].None`; `match` handles both cases.

::: details Answer
<<< @/snippets/book/ch16_ex4.spr

```text
7
0
```
:::

Next chapter: [More about numbers](/en/tutorial/ch17-numbers) — `Int32`, `Float32`, `Decimal`, `BigInt` and `@std/math`.
