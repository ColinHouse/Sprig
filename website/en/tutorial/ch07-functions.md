# 7. Functions

The loops of the last chapter removed repeated actions; this chapter removes repeated **stretches of code**. Pack a piece of work into a named bundle, and afterwards the name alone does the job — that is a function.

In this chapter you will learn:

- to define and call functions;
- parameters, arguments, results and `-> Unit`;
- early `return`, and why every path must return;
- scope: which names are visible where;
- the order of top-level code;
- why `main` is not special;
- recursion and call traces;
- the naming conventions.

## 7.1 Defining and calling

<<< @/snippets/book/ch07_define.spr

```text
8
20
```

The definition first:

- `func double(n: Int) -> Int:` defines a function; `func` is the keyword and `double` is the name.
- The `n: Int` in the parentheses is a **parameter**: a name and a type, through which the function receives the value passed in from outside.
- `-> Int` is the **result type**: the function hands back an `Int`.
- In the indented body, `return n * 2` computes `n * 2`, hands it back to the place that called, and ends the function there.

Now the call:

- `double(4)` is a call: write the name and put the **argument** 4 in the parentheses. The program jumps into the body with `n` equal to 4, computes 8 and hands it back.
- `double(4)` is itself an `Int` expression, so it can go into `print(...)` or into `let x = ...`.
- The same function can be called any number of times, each from the top; in `double(10)` the `n` is 10 and the result is 20.

With a function, the "copy the same `if` three times" code of the last chapter writes the branches once and calls them with different arguments.

## 7.2 The signature must be complete

Every type in the function's **signature** (its parameters and result) is required. Local variables may be inferred, as in `let x = 3`, but the signature is the contract the caller reads, so it is written out.

### Deliberate mistake: a missing result type

<<< @/snippets/book/ch07_no_result_type.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:1:20: 'func double(n: Int)' has no result type
  hint: Write 'func double(n: Int) -> Unit:' when it returns nothing, or put the result type after '->', for example 'func double(n: Int) -> Int:'.
```

`1:20` points at the colon at the end of the line: the function header stopped there, with no `->` or result type. The hint offers the two possibilities: a result means `-> Int`, no result means `-> Unit` (the next section). A missing parameter type will not compile either; add it at the definition.

## 7.3 Returning nothing: Unit

Not every function hands a value back. A function that just does something, such as printing, writes `-> Unit`:

<<< @/snippets/book/ch07_unit.spr

```text
Hello, Ada!
Hello, Grace!
```

`Unit` means "no value": the function finishes its work and ends without a result. A call to it is a statement by itself; there is nothing to use.

### Deliberate mistake: storing a Unit result

With no value, there is nothing to bind a name to:

<<< @/snippets/book/ch07_unit_bind.spr

```text
SPR-TYPE-UNIT [TYPE] main.spr:4:1: Cannot bind a Unit result to a variable
```

The compiler stops you here because "save what the printing function returned" is almost always a mistake. To make a function hand something back, change its result type to a real type and put the value after `return`.

## 7.4 Early return

The moment `return` runs, the function ends and no later line runs. That makes it the tool for "leave as soon as something is wrong", which reads much better than a stack of nested `if`s:

<<< @/snippets/book/ch07_return.spr

```text
invalid
minor
adult
negative, skipping
value 5
```

- `label(-1)`: the first `if` holds, `return "invalid"` ends the function at once and the two later `if`s are never checked. `label(10)` reaches the second `return`, and `label(30)` fails both `if`s and falls to the final `return "adult"`.
- `show(-3)`: `n < 0` holds, so it prints `negative, skipping` and then a **valueless** `return` ends the function.
- `show(5)`: the condition fails and it prints `value 5`. A function returning `Unit` needs no `return` at the end; to leave early, a bare `return` does it.

Note `print("value " + n)`: when one side of `+` is a `String`, the `Int` is turned into text and joined on.

## 7.5 Deliberate mistake: a path that returns nothing

When a function's result type is not `Unit`, every path of execution must end in `return`:

<<< @/snippets/book/ch04_missing_return.spr

```text
SPR-FLOW-MISSING-RETURN [FLOW] main.spr:1:1: Function 'sign' must return String on every path
```

`n > 0` and `n < 0` both return, but when `n == 0` neither branch runs and the function reaches its end with nothing to hand back. The compiler performs **flow analysis** (the category is `FLOW`) over every possible path before it finds this. The fix: add an `else` branch, or a final `return` at the end.

## 7.6 Scope: where names are visible

Each function is its own small world. Its parameters and the names declared in its body mean something only inside it:

### Deliberate mistake: a local name that escaped

<<< @/snippets/book/ch07_scope.spr

```text
SPR-NAME-UNRESOLVED [NAME] main.spr:6:7: Unresolved name 'hidden'
```

`hidden` exists only inside `setup`. The name is gone when the function ends, so the outer `print(hidden)` cannot find it. The other direction works: a function body can see top-level names:

<<< @/snippets/book/ch07_global.spr

```text
15
```

`triple` uses the top-level `rate`. A function definition only "writes the code down"; it runs when it is called, and by then `rate` exists.

## 7.7 Top-level order: functions may be called early, variables may not

A function definition does not take part in the running order. Calling a function before its definition is fine:

<<< @/snippets/book/ch07_call_before.spr

```text
6
```

Top-level statements (code outside every function) run exactly once, strictly in the order they appear. Using a top-level variable before it is declared is a compile error:

<<< @/snippets/book/ch07_forward.spr

```text
SPR-NAME-FORWARD-REFERENCE [NAME] main.spr:1:7: Top-level 'n' is used before its declaration; top-level statements run once, in source order
  hint: Move the declaration of 'n' above this statement.
  Declared here at 3:1
```

Note the last line, `Declared here at 3:1`: the compiler marks where `n` is declared for comparison.

There is a subtler case too: where the function is defined does not matter, but any top-level variable it uses **at run time** must already be initialized. This compiles, and fails only when `value()` runs:

```sprig
func value() -> Int:
    return n

print(value())

let n = 7
```

```text
SPR-RUNTIME-EXCEPTION [RUNTIME] main.spr:2:5: Top-level 'n' was used before its initializer ran
  hint: Top-level statements run in source order; declare the binding above the first top-level statement that calls code using it. Run with --stacktrace to see the JVM stack.
```

The `RUNTIME` in `SPR-RUNTIME-EXCEPTION` says it happens at run time, not at compile time. Move `let n = 7` above the call.

## 7.8 main is not special

Some languages start a program at a `main` function. Sprig has no such rule: **top-level statements are the program**, run from top to bottom; `sprig init` merely writes a `main` function by habit and calls it:

<<< @/snippets/book_en/ch04_main.spr

```text
from main
```

`main` here is an ordinary function name. Delete the `main()` call and the definition stays but nothing prints — defining is not running; `sprig run` will even remind you that no top-level statement calls `main`.

## 7.9 Recursion: a function that calls itself

A function body may call the function itself; that is **recursion**. The factorial of 4 (4×3×2×1) is the example:

<<< @/snippets/book/ch07_recursion.spr

```text
24
```

`factorial(4)` wants 4 times `factorial(3)`, which wants 3 times `factorial(2)`, and so on down to `factorial(1)`. The branch `n <= 1` returns 1 directly and asks nothing further — that is the **base case**. The results then come back up:

| Step | Being computed | Result |
|---|---|---|
| 1 | `factorial(4)` | 4 × `factorial(3)`, waiting |
| 2 | `factorial(3)` | 3 × `factorial(2)`, waiting |
| 3 | `factorial(2)` | 2 × `factorial(1)`, waiting |
| 4 | `factorial(1)` | base case, 1 |
| 5 | `factorial(2)` receives | 2 × 1 = 2 |
| 6 | `factorial(3)` receives | 3 × 2 = 6 |
| 7 | `factorial(4)` receives | 4 × 6 = 24 |

A recursive function must have a branch that stops it. Without one the calls pile up and the run ends with `StackOverflowError`.

## 7.10 How to name things

The naming rules for Sprig (the full text is in `sprig help language`):

- Names you declare — functions, methods, parameters, variables, fields, top-level `let`s — use `lower_snake_case`: `read_utf8`, `max_by`.
- Types — classes, contracts, enums, variants, their cases and type parameters — use `UpperCamelCase`: `Light`, `Expense`.
- Built-in members (`toString`, `divTrunc`) and Java APIs keep their `camelCase` names.

The compiler does not complain about a name like `myFunc`, but the book and the standard library follow the convention, and following it saves trouble.

## Summary

- `func name(parameter: Type, ...) -> ResultType:`, with the body indented.
- Signature types are required; write `-> Unit` when nothing is returned.
- Arguments are passed by position; a call is the name plus parentheses.
- `return` ends the function at once; a non-`Unit` function must return on every path.
- Parameters and locals live only inside the function; top-level names are visible inside it.
- Functions may be called before they are defined; top-level statements run in order, so top-level variables must be declared before use.
- There is no special `main`: top-level statements are the program.
- Recursion needs a base case; a variable trace table works for function calls too.
- Declared names use `lower_snake_case`, types use `UpperCamelCase`.

## Exercises

### Exercise 1: a square function

Write `square(n: Int) -> Int` returning `n * n`, and print `square(6)`.

Hint: the body is the single line `return n * n`.

::: details Answer

<<< @/snippets/book/ch07_ex_square.spr

```text
36
```

:::

### Exercise 2: the largest of three

Write `max3(a: Int, b: Int, c: Int) -> Int` returning the largest of the three, and print `max3(3, 9, 5)`.

Hint: keep `var best` starting at `a`, challenge it with `b` and then `c` in two `if`s, and `return best`.

::: details Answer

<<< @/snippets/book/ch07_ex_max3.spr

```text
9
```

:::

### Exercise 3: even or not

Write `is_even(n: Int) -> Bool` returning `true` for an even `n` and `false` otherwise; print `is_even(10)` and `is_even(7)`.

Hint: return a `Bool` expression: `n % 2 == 0`.

::: details Answer

<<< @/snippets/book/ch07_ex_even.spr

```text
true
false
```

:::

### Exercise 4: a countdown function

Write `countdown(start: Int) -> Unit` that prints from `start` down to 1 and then `liftoff`; call `countdown(3)`.

Hint: parameters cannot be assigned (they are like `let`), so start the function with `var n = start` and use the `while` from the last chapter.

::: details Answer

<<< @/snippets/book/ch07_ex_countdown.spr

```text
3
2
1
liftoff
```

:::

### Exercise 5: recursive sum

Write `sum_to(n: Int) -> Int` recursively, returning 1+2+…+`n`; print `sum_to(5)`.

Hint: the base case returns 0 when `n <= 0`; otherwise return `n + sum_to(n - 1)`.

::: details Answer

<<< @/snippets/book/ch07_ex_sum_to.spr

```text
15
```

:::

::: tip Coming from another language?

- Python: `def` becomes `func`, and colons and indentation are the same; but the types must be written in the signature, and there are no default arguments, no `*args` and no nested functions.
- Java / JavaScript: no `function` keyword; functions cannot share a name (no overloading); there is no "last line is the result", so every path needs an explicit `return`.
- Parameters are immutable like `let`; for a mutable copy, declare a `var` inside the function.
- When you need a "small local function", use the lambdas of chapter 15.

:::

Next chapter: [Chapter 8: Lists](/en/tutorial/ch08-lists).
