# 14. Handling errors

In this chapter you will learn:

- how to write "this may fail" into a function signature with `throws`;
- how an error travels up the call chain until somebody catches it;
- `throw`, `try`/`catch` and `finally`, and the order in which they run;
- error classes and the order of several `catch` clauses;
- how to rethrow with `throw problem`, adding context to an error;
- how `require` from `@std/nulls` turns "this should have had a value, but it was `null`" into an error;
- which failures are not `Error`s, and why you fix them instead of catching them.

## 14.1 Failure in the type: throws

Some operations fail: text does not parse, a file is missing, the network is down. Sprig does not use hidden exceptions; it puts failure in the **signature**: `-> Int throws Error` reads "returns an `Int`, or fails with an `Error`".

<<< @/snippets/book/ch09_errors.spr

```text
42
failed: not a number: abc
done
```

The error travels up the call chain like this:

```text
top-level try:                  ← caught here
  total(["12", "abc"])          ← does not handle it; its signature says throws Error too
    parse_amount("abc")         ← throw Error("not a number: abc")
```

Line by line:

- `func parse_amount(text: String) -> Int throws Error:` says in its signature that it may throw an `Error`.
- `text.toIntOrNull()` returns `Int?`; `if value == null: throw Error("not a number: " + text)` throws an error with a message. `throw` ends the current path at once, and, like `return`, **it narrows**: after the `throw`, `value` is known to have a value, so `return value` works.
- `total` calls `parse_amount` and does not handle the error, so its signature says `throws Error` too. The error climbs one explicit level at a time; any level may choose to handle it or to declare it onward.
- `try:` wraps the call that may fail; `catch problem: Error:` catches it and `problem.message` is the text; `finally:` runs whether the try succeeded, failed or returned early, and may be left out.

Follow the three output lines:

| what happens | output |
|---|---|
| `total(["12", "30"])` returns 42 normally | `42` |
| `total(["12", "abc"])` throws in `parse_amount`; it travels up to the top level | (nothing yet) |
| `catch problem: Error:` catches and prints the message | `failed: not a number: abc` |
| `finally:` runs | `done` |

**Top-level statements are special**: they may call throwing functions without declaring anything. An error nobody catches ends the program with `SPR-RUNTIME-ERROR`, as 14.6 shows.

A `return` inside `finally` is allowed (it overrides the value from the `try`), but it easily confuses readers; do not write it.

## 14.2 Deliberate mistake: neither handling nor declaring

<<< @/snippets/book/ch09_unhandled.spr

```text
SPR-FLOW-THROWS [FLOW] main.spr:8:11: Call may throw Error; declare 'throws Error' or handle it with try/catch
  hint: Declare it on 'show' by changing its header to 'func show(text: String) -> Unit throws Error:', and its callers then handle or declare it too (top-level statements need neither), or handle it where it happens: 'try:' around the call, then 'catch problem: Error:' with what to do instead. Sprig keeps recoverable errors explicit; there is no implicit propagation.
```

How to read it:

- the position is the `parse_amount(text)` call inside `show`;
- the message gives two choices: add `throws Error` to `show`'s signature, or `try`/`catch` here;
- the hint spells out both fixes and reminds you that once `show` declares it, its callers must handle or declare it too (top-level statements excepted);
- the last sentence is the design: **there is no implicit propagation**. Read a signature and you know whether it can fail; read a call and you know where failure is handled.

## 14.3 Error classes

With a single `Error`, callers can only tell failures apart by their message text. When you need to tell them apart, define the error as a class:

<<< @/snippets/book_en/ch09_error_classes.spr

```text
took 2
unknown sku ZZ
problem: only 5 left
```

- An error class is an ordinary `class` with a **message field** (here `let message: String`) and any other fields (`sku`, `missing`).
- `conform NotFound to Error(message)` declares it an `Error`; the parentheses name the message field. Chapter 16 explains `conform`; for now, copy the shape.
- `throws NotFound, OutOfStock` lists each error it may throw; `throw NotFound(message=..., sku=...)` writes field names like any class construction.
- You can write several `catch` clauses; **the first match from the top wins**. So the specific `NotFound` goes before the general `Error`; the other way around, `catch problem: Error:` catches everything and the later `catch problem: NotFound:` can never run — the compiler reports `SPR-FLOW-THROWS` with the message `Catch of NotFound is unreachable: Error already catches it`.
- Inside `catch problem: NotFound:`, `problem` is a `NotFound`, so `problem.sku` works; joining the error into a string (`"problem: " + problem`) uses its message.

Using specific types in low-level functions and `Error` at the boundary is a common split.

## 14.4 The message field may have another name

The message field does not have to be called `message`. The field named in the `conform` parentheses is the message:

<<< @/snippets/book/ch14_error_field.spr

```text
found A1
no item ZZ / ZZ
joined: no item ZZ
```

- `conform NotFound to Error(text)` makes `text` the message field, so `problem.text` reads it, and `"joined: " + problem` builds the same value.
- The message field must be a `String`:

::: details Deliberate mistake: the message field is not a String
<<< @/snippets/book/ch14_bad_message.spr

```text
SPR-CONFORM-TARGET [TYPE] main.spr:3:1: sprig.runtime.SprigError has no public or protected constructor taking (long) (expected (java.lang.String) | (java.lang.String, java.lang.Throwable), actual (long))
  hint: Name fields whose JVM shapes match one constructor exactly, in its parameter order.
```

`code` is an `Int`, and the compiler finds no error constructor accepting it: `SPR-CONFORM-TARGET`. You do not need every word: the position points at the `conform` line, the message says no constructor matches, and the hint tells you to use fields whose types line up. Making it a `String` fixes it.
:::

## 14.5 Rethrowing: adding context

An error message from a low-level function is not always useful to its caller. A `catch` may `throw` a new error that carries the old message:

<<< @/snippets/book/ch14_rethrow.spr

```text
12
failed: cannot load amount: not a number: abc
done
```

- `load` calls `parse`; in `catch problem: Error:` it builds a new message, `"cannot load amount: " + ...`, and throws a new `Error`.
- The original error is replaced by the one with context, and `load`'s signature still says `throws Error`.
- The first call returns 12; the second is caught at the top, which prints the new message; `finally` prints `done` last.

The argument of `throw` need not be a new error: `throw problem` rethrows the caught error unchanged, and is valid too (the signature does not change). Wrapping exists to bring the message closer to what the caller cares about.

## 14.6 The bridge between null and Error: require

`require(value, message)` from `@std/nulls` returns the value when it has one and throws an `Error` carrying the message when it is `null`. It turns the surprise of "this should have had a value, but it was `null`" into an explicit failure:

<<< @/snippets/book/ch08_nulls.spr

```text
12
0
8080
port must be a number: eighty
```

- `nulls.or_else(stock["tea"], 0)` is as in the previous chapter: 12 when there is a value, 0 when `"milk"` is missing.
- `port`'s signature is `-> Int throws Error`, because it calls `require`, which may throw.
- `port("8080")` returns 8080; in `port("eighty")`, `toIntOrNull()` is `null`, so `require` throws `Error("port must be a number: eighty")`.
- The top-level `catch problem: Error:` catches it and prints `problem.message`, which is the last line. Without the `try`/`catch`, the program would end with `SPR-RUNTIME-ERROR` and print the same message.

`T?` or `throw`? A simple dividing line: **a result that does not exist is normal** (lookups, map misses, optional settings), so return `T?`; **the caller passed something they should not have, or the outside world failed** (a parse failure, a missing file), so `throw`.

## 14.7 Not every failure is an Error

Integer overflow and list indexes out of bounds are bugs in the program; at run time they report `SPR-RUNTIME-EXCEPTION` (note the `[RUNTIME]` tag, not `[TYPE]`). They are **not** `Error`s, cannot be expressed with `throws Error`, and `catch problem: Error:` does not catch them:

```sprig
try:
    let big = 9223372036854775807 + 1
    print(big)
catch problem: Error:
    print("caught")
```

```sh
$ sprig run main.spr
```

```text
SPR-RUNTIME-EXCEPTION [RUNTIME] main.spr:2:5: Numeric error: Int addition overflow
  hint: The exact result does not fit in the integer type. Check before the operation, for example 'if value > 0 and total > 9223372036854775807 - value:' for an Int addition, or catch it: import java.lang.ArithmeticException as ArithmeticException, then put the operation in 'try:' with 'catch problem: ArithmeticException:'. See `sprig help numerics`. Run with --stacktrace to see the JVM stack.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

`catch problem: Error:` did not print `caught`; the program ended on this diagnostic. A list index out of bounds is the same:

```sprig
let items: List[Int] = [1, 2, 3]
print(items[5])
```

```text
SPR-RUNTIME-EXCEPTION [RUNTIME] main.spr:2:1: List index 5 is out of bounds; size is 3
  hint: Check the list length before indexing. Run with --stacktrace to see the JVM stack.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

The right response to these is to **fix the code**: check before adding, check the length before indexing. The hint shows two roads, "check first" and "import the Java exception and catch it"; the second belongs to chapter 21.

An uncaught `Error` takes the other road, reported as `SPR-RUNTIME-ERROR`:

```sprig
func parse(text: String) -> Int throws Error:
    throw Error("boom: " + text)

print(parse("x"))
```

```text
SPR-RUNTIME-ERROR [RUNTIME] main.spr:2:5: Uncaught Error: boom: x
  hint: Catch it with try/catch or declare throws in the calling function. Run with --stacktrace to see the JVM stack.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

Both diagnostics tell you: adding `--stacktrace` prints a JVM stack, and its bottom shows which Sprig function started the call. The syntax is `sprig run --stacktrace file.spr`.

::: tip Coming from another language?
- There are no hidden throw paths: the signature says `throws Error`, and the caller must handle or declare it. The compiler does not let you forget.
- It looks like Java's checked exceptions, but Sprig has one `Error` family and no resource-management syntax beyond `finally` (no try-with-resources, no `using`).
- Java's runtime exceptions (such as `ArithmeticException`) are not `Error`s in Sprig. After importing the Java class you may write `throws ArithmeticException` and `catch problem: ArithmeticException:`, but nobody forces you to, and a beginner does not need to.
:::

## Summary

- `throws` is part of the signature; callers either `try`/`catch` or declare it too; top-level statements may call directly, and there is no implicit propagation.
- `throw` ends the current path and narrows; `finally` runs no matter what.
- An error class is a `class` with a `message` (or any other `String` field) plus `conform X to Error(field)`; several `catch` clauses match top to bottom, so the specific one goes first.
- A `catch` may `throw` a new error or `throw problem` to rethrow; `nulls.require` turns `null` into an error.
- Runtime exceptions and `SPR-RUNTIME-ERROR` are not `Error`s: fix the code rather than catching them, and use `--stacktrace` to see the call stack.

## Exercises

**Exercise 1 (parse and catch).** Write `parse(text: String) -> Int throws Error` that throws `Error("bad number: " + text)` when parsing fails. Call `parse("7")` and `parse("seven")`, and print the message with `try`/`catch`.

::: details Answer
<<< @/snippets/book/ch14_ex1.spr

```text
7
error: bad number: seven
```
:::

**Exercise 2 (division by zero is an error, not an exception).** Write `divide(a: Int, b: Int) -> Int throws Error`: throw `Error("division by zero")` when `b` is 0, otherwise return `a.divTrunc(b)`. Check that 6/2 gives 3 and 6/0 is caught.

::: details Answer
<<< @/snippets/book/ch14_ex2.spr

```text
3
cannot divide: division by zero
```
:::

**Exercise 3 (an error class).** Define `Empty` with two `String` fields, `message` and `name`, and `conform Empty to Error(message)`. `greeting(name)` throws `Empty(...)` for an empty name; the caller writes `catch problem: Empty:` and prints the message and `problem.name`.

::: details Answer
<<< @/snippets/book/ch14_ex3.spr

```text
hello Ada
name is empty []
```
:::

**Exercise 4 (rethrow).** `read_count` fails with `not a number: x`. Write a `load` layer that wraps it with `throw Error("config: " + problem.message)` for the caller, then catch and print at the top.

::: details Answer
<<< @/snippets/book/ch14_ex4.spr

```text
3
config: not a number: x
```
:::

**Exercise 5 (the order of finally).** Write `report()`: the `try` prints `working` and then `throw Error("boom")`; `catch problem: Error:` prints `caught: boom`; `finally:` prints `cleanup`. Guess the three lines before you run it.

::: details Answer
<<< @/snippets/book/ch14_ex5.spr

```text
working
caught: boom
cleanup
```
:::

Next chapter: [Functions as values](/en/tutorial/ch15-functions-as-values).
