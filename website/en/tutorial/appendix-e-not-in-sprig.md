# Appendix E. What Sprig leaves out

This appendix collects the language features Sprig deliberately omits. They are not "not done yet": each omission is a design decision, and each comes with something to write instead. The authoritative list is `unsupportedSyntax` and `featureGuidance` in `sprig capabilities --json`; this page arranges it by topic and adds runnable examples.

## E.1 Inheritance

There is no `extends`. Types have no parent and child classes, and methods are not "overridden".

What to write instead:

- **Contract classes**: a class whose methods all have no body is a contract; an implementing class attaches with `conform`, and after that it can be used as the contract type.
- **Composition**: put an object of another class in a field and hand the work over to it.
- **Variants**: when the set of kinds is fixed and you need to branch on the kind, use a variant with `match`.

<<< @/snippets/book_en/appendix_e_inheritance.spr

```text
Hello, Ada
```

`English` does not inherit from `Greeter`; it satisfies the contract, which is why it can be assigned to a variable of type `Greeter`. A contract is an "open set" (anyone can conform); a variant is a "closed set" (the cases are fixed in the declaration).

## E.2 async/await and Promises

There is no `async`, no `await` and no `Promise`, and there are no "colored functions": a function is either ordinary or does not exist.

The replacement is `@std/concurrent` in the standard library: `spawn` runs an ordinary function on a JDK virtual thread, and `scope` waits for every task to finish. Blocking Java calls (HTTP, JDBC, sockets) go into a task as they are, with no rewrite.

<<< @/snippets/book_en/appendix_e_concurrency.spr

```text
[2, 4, 6]
42
```

- `parallel_map` starts a task for each element and returns the results in the original order.
- `spawn` returns a `Task[T]` and `await()` gets the value back; when `scope` ends, every task has finished.
- The first failure of a task cancels the rest, and `scope` rethrows it.

Chapter 22 covers `channel`, `counter`, `lock` and thread pools in full.

## E.3 Tuples and destructuring

There are no tuple types, and no destructuring like `let (a, b) = ...`.

What to write instead: a **one-line class** gives the fields names, and reading a field is the destructuring. When there are few fields and their meaning is clear, this is plainer than a tuple; meaningful field names double as documentation.

<<< @/snippets/book_en/appendix_e_tuples.spr

```text
1
2
```

`Pair(first=1, second=2)` is constructed with named arguments and read as `p.first` and `p.second`. When you need "several shapes", use a variant with named payloads.

## E.4 String interpolation

There is no `f"{name}"`, no template strings, and no `${}` syntax.

What to write instead is `+`: as soon as one side is a `String`, the other value turns into its text.

<<< @/snippets/book_en/appendix_e_interpolation.spr

```text
name: Ada, age: 36
```

Joining evaluates left to right, so `1 + 2 + " then " + 1 + 2` is `3 then 12` (see Appendix A). A value that may be `null` cannot be joined directly; check it first or give it a fallback.

## E.5 The `Char` type

There is no separate character type.

What to write instead: **one text element is a `String` of exactly one Unicode code point**. `length()`, indexing, `charAt`, `substring` and `for` all work in code points.

<<< @/snippets/book_en/appendix_e_char.spr

```text
3
A
😀
東
```

`"A😀東"` has length 3, not the 4 UTF-16 code units; `text[1]` and `charAt(1)` both give the whole `"😀"`. To get a code point, use `codeAt`; to turn one back into text, `String.fromCode` (Chapter 4).

## E.6 Block lambdas

The right side of a lambda is one expression; it cannot be an indented multi-line block. A `fn` for a functional interface takes at most three parameters.

What to write instead:

- Put the logic in a **named function** and pass it as a value: `items.map(convert)` (Chapter 15).
- When you need several lines, use ordinary statements inside a named function; keep the lambda to one expression.
- Short expression lambdas still work: `fn(x: Int) => x * 2`.

<<< @/snippets/book_en/appendix_e_block_lambda.spr

```text
[1, 4, 9]
```

## E.7 Wildcard branches in `match`

`match` must list every enum/variant case; there is no `case _:` and no default.

What to write instead: **write every case out**. When you add a case, every `match` that forgot to handle it fails at compile time, which is exactly the point of the design.

<<< @/snippets/book_en/appendix_e_wildcard.spr

```text
go
```

Writing `case _:` is rejected, and the error explains that match is for enums and variants, and that there are no type tests on class or contract values:

<<< @/snippets/book_en/appendix_e_wildcard_fail.spr

```text
SPR-MATCH-UNKNOWN-CASE [TYPE] main.spr:11:14: Match case must be written as Type.Case
  hint: match is for enums and variants; there are no type tests on class or contract values. A closed set of types is a variant (declare one with a case per type and match on it); an open set is a contract (call its methods instead of testing the type).
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

When you need "fallback behavior", write it as an ordinary `case`, or add an explicit case to the variant (such as `Unknown`).

## E.8 Operator overloading

You cannot give `+` or `==` a meaning of your own.

What to write instead: **named methods**. `a.plus(b)` is longer than `a + b`, but a reader knows what it does; and because of that, `==` always behaves the same way: by value or by identity, depending on the type (Chapter 11).

<<< @/snippets/book_en/appendix_e_operator_overloading.spr

```text
(4, 6)
```

## E.9 Pipelines, annotations, decorators and macros

| Not in Sprig | What to write instead |
|---|---|
| the pipeline operator `\|>` | ordinary statements, or nested calls |
| annotations `@Foo` | explicit typed data, or ordinary functions |
| decorators | ordinary functions and modules |
| macros | ordinary functions and modules |
| reflection-derived schemas (for example, mapping any object to JSON automatically) | an explicit structure: the field list and the code that builds the JSON |

"Explicit" is the point here: what the compiler and a reader can both check is what is written in the code.

## E.10 Arrays, varargs and Java functional interfaces

- **Sprig source has no arrays.** Day to day, use `List[T]`; when a Java API needs an array, a Java array crosses as an opaque value (`byte[]` has the `HostBytes` helpers), or use the adapters in `@std/jvm`. Appendix F has the details.
- **Sprig cannot declare varargs** (`func f(xs: Int...)` does not exist), but it can call Java varargs: the trailing arguments are packed into an array, and you may also pass an array explicitly.
- **Java functional interfaces take at most three parameters.** Beyond three parameters, or with type-variable varargs (`T...`), you need a named wrapper or a different Java API.

## E.11 Generic variance

Generics are **invariant**: `Box[Cat]` is not `Box[Animal]`, even when a `Cat` can be used as an `Animal`.

What to write instead: decide which type argument is right, and when you need a conversion, write an explicit conversion function (Chapter 16).

## E.12 Inference from the expected type

Type parameters are inferred only from **the arguments of the call**, never from "what you want it to be" (the assignment target, the return type, the field type).

- `let box = Box(value=41)` works: `41` says that `T` is `Int`.
- `let xs: List[Int] = []` must write the type: an empty `[]` says nothing.
- `identity(1)` infers from the argument; when that is not enough, write it out: `identity[Int](value=1)`.

## E.13 A central registry

There is no official, hosted, login-required central package repository.

What to write instead: dependencies can come from a **local path** or from **Git**; the package registry is simply an index in a directory or repository (`packages/NAME.toml`) that records each package's Git URL and versions, and publishing is a pull request against that index. Chapter 18 covers declaring and using dependencies.

## Related chapters

- [Chapter 11: Classes and objects](/en/tutorial/ch11-classes): one-line classes and identity.
- [Chapter 12: Enums, variants and match](/en/tutorial/ch12-enums-variants): closed sets and exhaustive matching.
- [Chapter 15: Functions as values](/en/tutorial/ch15-functions-as-values): named function references and lambdas.
- [Chapter 16: Generics and contract classes](/en/tutorial/ch16-generics-contracts): `generic`, `requires`, contracts in place of inheritance.
- [Chapter 18: Modules, projects and dependencies](/en/tutorial/ch18-modules-projects): paths, Git and the registry.
- [Chapter 22: Concurrency](/en/tutorial/ch22-concurrency): the full `@std/concurrent`.
- [Appendix F: Java interop in depth](/en/tutorial/appendix-f-advanced-java): arrays, wildcards and parent views.
