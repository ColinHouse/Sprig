# 15. Functions as values

In this chapter you will learn:

- to write a small piece of behaviour as a **function value** (a lambda), and to store it, pass it and return it;
- to write a function value's type, such as `fn(Int) -> Int`;
- how named functions, module functions and object methods work as values, and why `print` is a value only in certain places;
- how lambda **capture** works: it reads outer `let` bindings, cannot read a function's local `var`, and a top-level `var` is module state;
- function values that can fail or be missing: `throws Error`, `rethrows`, `(fn(Int) -> Int)?`;
- the function-taking helpers in `@std/lists`: `sort_by`, `fold`, `any`, `all`, `find`, `count`.

## 15.1 Why function values

`lists.sort` from chapter 8 only sorts in natural order; so far a function's behaviour has always been fixed in its body. To let the caller decide *how* to sort or *which* items to keep, you have to pass behaviour around as a value. Such a value is a **function value**, written with `fn`, and usually called a **lambda**.

The smallest example: give "multiply by 2" a name.

<<< @/snippets/book/ch15_lambda.spr

```text
42
```

Line by line:

- `fn(n: Int) => n * 2` is a function value. After `fn` come the parameters, and they must have types; after `=>` comes **one expression**, whose value is the function's result. There is no multi-line lambda body: write a named function for real logic, or bind an if expression to a `let` (section 15.4).
- It is assigned to `double`. From then on `double` is an ordinary variable: store it, pass it.
- `double(21)` calls it like any function and prints `42`.

A lambda takes zero to three parameters:

```sprig
let greet = fn() => "hi"
let add = fn(a: Int, b: Int) => a + b
```

The type of a function value is written `fn(parameter types) -> result type`; the two above are `fn() -> String` and `fn(Int, Int) -> Int`. Parameter types cannot be left out — the compiler does not guess:

### Deliberate mistake: a lambda parameter without a type

<<< @/snippets/book/ch15_untyped_param.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:1:15: mismatched input ')' expecting ':' (unexpected ')')
```

Reading the error: it never even reaches type checking — parsing fails first, because `n` in `fn(n)` needs a `:` and a type after it. `main.spr:1:15` points at the incomplete parameter. The fix is `fn(n: Int) => n + 1`.

## 15.2 Functions as parameters and results

The most useful thing about a function value is handing it to someone else. `apply_twice` takes a `fn(Int) -> Int` and applies it to the same value twice.

<<< @/snippets/book/ch15_fn_type.spr

```text
7
45
```

Line by line:

- `func apply_twice(value: Int, step: fn(Int) -> Int) -> Int`: the second parameter `step` has the type "a function that takes an `Int` and returns an `Int`". `value` is an ordinary parameter.
- `step(step(value))` evaluates from the inside: `step(5)` is `6`, then `step(6)` is `7`.
- With both calls:

  | Call | First step | Second step | Printed |
  |---|---|---|---|
  | `apply_twice(5, fn(n: Int) => n + 1)` | `5 + 1` = `6` | `6 + 1` = `7` | `7` |
  | `apply_twice(5, triple)` | `5 * 3` = `15` | `15 * 3` = `45` | `45` |

- The last line passes the named function `triple` with no parentheses. **A named function's name, without a call, is a function value**, exactly like `fn(n: Int) => triple(n)`. With parentheses, `triple(5)` means "call it now and take the result".

A function value can also be returned from a function. Each call to `multiplier` builds a new function that remembers the value it was born with:

<<< @/snippets/book/ch15_return_fn.spr

```text
21
70
```

- `multiplier(3)` returns the `scale` lambda, which uses the parameter `factor`. A parameter never changes, so `scale` can safely keep it.
- `triple(7)` computes `7 * 3` and `tenfold(7)` computes `7 * 10`. Each function remembers its own value. A function value that carries its birth environment is called a **closure** in other languages.

Function values cannot be compared with `==`: two references to the same name are two different values, and comparing them only misleads.

### Deliberate mistake: comparing two function values

<<< @/snippets/book/ch15_fn_compare.spr

```text
SPR-TYPE-OPERAND [TYPE] main.spr:3:7: Function values cannot be compared with '==' (expected comparable values, actual fn(Int) -> Int and fn(Int) -> Int)
  hint: Compare what the functions compute, or compare a name, enum or variant that chooses the function; a nullable function value may still be compared with null.
```

`a` and `b` happen to compute the same result, but they are two independent values and Sprig does not compare them. Note the hint's last sentence: a nullable function value *can* be compared with `null` (used in 15.7).

## 15.3 Named functions, methods and module functions are values

Lambdas are not the only function values. A named function, a module function from `@std/lists`, or an object's method, used without a call, is a function value:

<<< @/snippets/function_references.spr

```text
[PEAR!, FIG!]
6
2
5
5
pear
fig
[fig, pear]
```

Line by line:

- `words.map(shout)`: `shout` is a named function and `map` wants a `fn(String) -> String`; hand it the name.
- `let total: fn(List[Int]) -> Int = lists.sum`: a module function assigned to a `let` with a function type. The type on the variable is useful here: it tells the compiler "I want a function value".
- `let bump = counter.bump`: a **method reference**. Taking a method off its object fixes the receiver `counter` once; every later `bump(...)` acts on that same `counter`, so `count` ends at `5`.
- `words.forEach(print)`: `print` accepts any type, so it has no single fixed type; it is a value only where a parameter type is known (here `fn(String) -> Unit`).
- `lists.reversed[String]`: `reversed` is generic and writes its type argument. Why the `[String]` is required is the subject of the next chapter.

### Deliberate mistake: storing `print` on its own

<<< @/snippets/book/ch15_print_value.spr

```text
SPR-TYPE-NOT-CALLABLE [TYPE] main.spr:1:9: print as a value needs the parameter type of the place it goes to
  hint: Pass print where a fn(T) -> Unit is expected, such as items.forEach(print), or write the lambda: fn(value: String) => print(value).
```

The right-hand side of `let p = print` has no "place to go", so the compiler does not know what `T` is and refuses. The hint gives both ways out: use it directly where a function type is expected (`items.forEach(print)`), or write the lambda `fn(value: String) => print(value)`.

Two related limits should not surprise you: Java methods and built-in methods (`names.add`, `text.length`) cannot be detached either — write a lambda. The error is `SPR-TYPE-NOT-CALLABLE`; its hint says `Built-in method 'length' is not a value`.

::: tip Coming from another language?
`fn(n: Int) => n * 2` is Python's `lambda n: n * 2` or JS's `n => n * 2`, but Sprig requires parameter types; there is no multi-line lambda (the compiler calls it a block lambda and does not support it). A function type like `fn(Int) -> Int` resembles Java's `Function<Integer, Integer>`, but takes at most three parameters and has no boxing ceremony at the source level.
:::

## 15.4 if expressions inside a lambda body

After `=>` there is one expression. An if expression from chapter 5 is an expression, so it fits — but each branch takes its own line, so bind it to a `let` first:

<<< @/snippets/book/ch11_if_lambda.spr

```text
[small, big, big]
[odd, even, odd]
```

- `size_label` is a plain named function; it `return`s the result of an if expression.
- `parity` binds the if expression to a `let`, then passes `parity` to `map`. When `n % 2 == 0` is true the value is `"even"`, otherwise `"odd"`.

### Deliberate mistake: stuffing a multi-line if into `map(...)`

<<< @/snippets/book/ch11_if_in_call.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:1:42: An if expression cannot be written inside parentheses, brackets or braces
  hint: Line breaks are ignored there, so the branches cannot go on their own lines. Bind the if expression, or the lambda that holds it, to a let first, then use the name.
```

Inside parentheses line breaks are ignored, but an if expression's branches must each take a line — the two rules collide, so the parser refuses. Do what the hint says: bind the whole lambda to a `let` (the `parity` example above) or write a named function.

## 15.5 Capture: what a lambda can see

Besides its own parameters, a lambda can read names that already exist in the surrounding scope. This is called **capture**. Capturing a `let` is the common case:

<<< @/snippets/book/ch15_capture_let.spr

```text
15
```

`add_base` has no `base` parameter; it reads the outer `let base = 10`, so `add_base(5)` is `5 + 10`.

**A function's local `var` cannot be captured**, because the lambda may be called later, or on another thread, and at that point "the current value" of a mutable local is unclear:

### Deliberate mistake: capturing a function's local `var`

<<< @/snippets/book/ch11_capture.spr

```text
SPR-TYPE-CAPTURE [TYPE] main.spr:3:16: Lambda captures mutable local 'counter'
  hint: Copy it into a 'let' binding before the lambda, or use a class field.
```

`counter` is a local `var` of `count_up`. Both fixes are in the hint: if the value is already final when the lambda is built, copy it into a `let` first (`let fixed = counter`); if the state must survive across calls, make it a class field (chapter 11).

A top-level `var` is different: it is not a function local but **module state**, and a lambda reads its value at call time:

<<< @/snippets/book/ch15_capture_top.spr

```text
1
6
```

| Moment | `hits` | `peek()` returns | Printed |
|---|---|---|---|
| When `peek` is bound | `0` | — | — |
| First `peek()` | `0` | `0 + 1` | `1` |
| After `hits = 5` | `5` | `5 + 1` | `6` |

The second call is not `2`: the top-level `var` is shared, and `peek` reads its current value every time. Under concurrency several tasks read and write it at once, and then it needs a lock or a dedicated counter — chapter 22.

## 15.6 Function values that can fail

A lambda that calls a `throws Error` function has that clause in its own type. Places that accept such values must say so, and `rethrows` lets the caller decide:

<<< @/snippets/book/ch15_throws.spr

```text
21
caught: not a number: two
18
second: not a number: x
```

Piece by piece:

- `parse` fails the chapter 14 way: `toIntOrNull()` yields an `Int?`, and text that is not a number is `throw`n.
- `let parser = fn(text: String) => parse(text)`: the lambda calls a throwing function, so `parser` has type `fn(String) -> Int throws Error`.
- `twice` declares `step: fn(Int) -> Int throws Error`, and puts `rethrows` after its result, meaning "*`twice` throws exactly when the `step` it was given throws*". So:
  - `twice(fn(n: Int) => n * 3, 2)` gets a lambda that cannot fail; the call needs no `try` and prints `18` (`2 * 3 = 6`, then `6 * 3 = 18`);
  - passing `failing` makes `parse("x")` throw, the `try` catches it and prints `second: not a number: x`.
- In the first `try`, `parser("21")` prints `21` normally; `parser("two")` throws and is caught by `catch problem: Error`.

The reverse does not hold: a throwing function value cannot be used where a non-throwing one is expected.

### Deliberate mistake: passing a throwing lambda where there is no throws

<<< @/snippets/book/ch15_throws_mismatch.spr

```text
SPR-TYPE-CALLABLE-THROWS [TYPE] main.spr:10:13: A function value that may throw Error cannot be used as fn(Int) -> Int (argument 1 of twice) (expected fn(Int) -> Int, actual fn(Int) -> Int throws Error)
  hint: Declare the target as fn(Int) -> Int throws Error and make the receiving function rethrows or throws Error, or handle the error inside a named function.
```

Reading the error: `expected fn(Int) -> Int` is what `twice` wants, `actual fn(Int) -> Int throws Error` is what you gave — the extra `throws Error` is the problem. Follow the hint: change `twice`'s signature (add `throws Error` and `rethrows`), or handle the error inside the lambda.

::: tip Coming from another language?
Java's checked exceptions force every caller to write `throws`; Sprig puts `throws Error` in the function type instead and makes it part of the type: `fn(A) -> R throws Error` goes where `fn(A) -> R` is expected, never the reverse. `rethrows` propagates the clause the way Kotlin's `@Throws` does, but inferred. Only `Error` crosses a function value today; checked Java exceptions stay in named functions (chapter 21).
:::

## 15.7 Nullable function values

A function value may be missing: its type is `(fn(Int) -> Int)?` (the `?` from chapter 13). Check for null before calling:

<<< @/snippets/book/ch15_nullable_fn.spr

```text
3
true
```

- `maybe` holds a value (here `inc`); after `if maybe != null:` the compiler narrows it to `fn(Int) -> Int`, so `maybe(2)` is allowed and prints `3`.
- `missing` is `null`, and `missing == null` is `true`. Comparing a nullable function value with `null` is allowed — the exception the hint in 15.2 mentioned.

### Deliberate mistake: calling without the null check

<<< @/snippets/book/ch15_nullable_call.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:2:7: Cannot invoke a nullable function value; check for null first (expected fn(Int) -> Int, actual (fn(Int) -> Int)?)
```

The `?` in `actual (fn(Int) -> Int)?` is the problem. Do what the hint says: `if missing != null:` first, or give it a default. It is the same rule as for nullable numbers and strings.

## 15.8 Arming `@std/lists` with lambdas

Chapter 8 already used `map`, `filter` and `forEach`. `@std/lists` has more helpers that take function values, and now you can read them all:

<<< @/snippets/book/ch15_lists.spr

```text
[fig, pear, apple]
10
true
false
8
2
```

- `sort_by(items, key)`: sorts by the key the function computes, keeping equal keys in input order (stable). The three word lengths are `4, 3, 5`, so `fig` comes first.
- `fold(items, initial, step)`: combines left to right. `0 + 1 + 2 + 3 + 4` gives `10`; an empty list gives `initial`.
- `any`: `true` when some item passes (`5` is odd); `all`: `true` only when every item passes (`5` is not even, so `false`).
- `find`: the first matching item, or `null` when none does; here it prints `8`.
- `count`: how many pass; the even numbers are `2` and `4`, so `2`.

The key/step/accept parameters of these functions are typed `throws Error`: if the lambda you pass can fail, the compiler makes the call handle or declare it. That is also why `find` returns `T?` — when nothing matches, there is no value to give.

## 15.9 Summary

- `fn(parameter: Type) => expression` builds a function value; its type is `fn(T) -> R`, with zero to three parameters.
- Function values go into `let`s, parameters and results; a named function, module function or method without a call is a value; `print` is a value only where the parameter type is known; function values cannot be compared with `==`.
- The body after `=>` is one expression; a multi-line if expression is bound to a `let` first.
- Capture: outer `let`s and parameters are readable, a function's local `var` is not; a top-level `var` is module state read at call time.
- `throws Error` is part of a function type; `rethrows` hands the clause to the caller.
- A nullable function value `(fn(T) -> R)?` is checked for null before it is called.

## 15.10 Exercises

**Exercise 1 (easy)** Use `filter` and a lambda to pick the even numbers out of `[1, 2, 3, 4, 5, 6]`.

Hint: write the test as `fn(n: Int) => n % 2 == 0`.

::: details Answer
<<< @/snippets/book/ch15_ex1.spr

```text
[2, 4, 6]
```
:::

**Exercise 2** Write a named function `half(n: Int) -> Int` returning `n.divTrunc(2)`, then use the `apply_twice` from 15.2 to apply it twice to `20`.

Hint: a named function goes where a function value is expected without parentheses.

::: details Answer
<<< @/snippets/book/ch15_ex2.spr

```text
5
```
:::

**Exercise 3** Use `lists.sort_by` to sort `["bb", "aa", "c", "dd"]` by length, noting that equal lengths keep their original order.

Hint: the key is `fn(w: String) => w.length()`; a stable sort leaves `bb, aa, dd` in their relative order.

::: details Answer
<<< @/snippets/book/ch15_ex3.spr

```text
[c, bb, aa, dd]
```
:::

**Exercise 4 (hard)** Write a `parse` (throwing `"bad: " + text` for non-numbers) and a `twice` that takes `fn(Int) -> Int throws Error` and uses `rethrows`. Call it once with a lambda that cannot fail and once with one that can, printing `caught: ...` for the latter.

Hint: the non-failing lambda needs no `try`; the failing one calls `parse("x")` right in its body.

::: details Answer
<<< @/snippets/book/ch15_ex4.spr

```text
21
caught: bad: x
```
:::

Next chapter: [Generics and contract classes](/en/tutorial/ch16-generics-contracts) — one piece of code for many types, and contracts that ask for methods without asking for an identity.
