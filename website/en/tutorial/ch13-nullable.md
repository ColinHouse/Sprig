# 13. Values that may be missing

In this chapter you will learn:

- the difference between `Int` and `Int?`: the latter may hold a value, or `null`;
- **narrowing**: where the compiler believes you have checked for `null`;
- that only `let` bindings, parameters and loop variables narrow, and what to do about `var`s and fields;
- that `continue` and `break` narrow too;
- `or_else` from `@std/nulls` and what it means that its fallback is evaluated eagerly;
- three deliberate mistakes: using a value without checking, narrowing a `var`, and putting `null` into an `Int`.

## 13.1 A value that may be missing: the question-mark type

"A value that may be missing" is one of the most common sources of bugs. Many languages use a special `null` for it, and you find out at run time that you forgot to check. Sprig writes it into the type: `Int` always has a value, while `Int?` may hold an `Int` or may be `null`. They are not the same type, and the compiler will not let you use the second as the first.

The `find` below looks for a target in a list of strings; it returns the position when it finds it, and `null` when it does not:

<<< @/snippets/book/ch08_nullable.spr

```text
found at 1
true
null
```

- `-> Int?` is the result type; the question mark says the function may return `null`: it looks by name and does not always find it.
- `return null` is allowed because the result type carries the question mark. The other way around, `let n: Int = null` does not compile (13.5 shows it).
- In `let index = find(words, "beta")`, `index` is `Int?`. `words` is `["alpha", "beta"]` and `"beta"` is at position 1, so the function returns 1.
- After `if index != null:`, inside that branch `index` is `Int`, so it joins a string directly: `"found at " + index` prints `found at 1`. This change from "may be `null`" to "definitely has a value" is called **narrowing**.
- `find(words, "gamma")` finds nothing and returns `null`; `other == null` is `true`.
- `print(other)` prints `null`: a nullable value can be printed, and compared with `null` or with other values.

How the type of `index` changes:

| position | type of `index` | why |
|---|---|---|
| `let index = find(words, "beta")` | `Int?` | the signature is `-> Int?` |
| inside the `if index != null:` branch | `Int` | the check just proved it is not `null` |
| using it again outside the branch | `Int?` | the check is only valid inside that branch |

You have met nullable types elsewhere already: the map lookup `stock["tea"]` in chapter 9 returns `Int?`, and `"12".toIntOrNull()` in chapter 4 does too. They all work the same way.

## 13.2 Deliberate mistake: using a value without checking

<<< @/snippets/book/ch08_deref.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:2:7: Operator '+' cannot use a value that may be null (expected non-null operands, actual Int? and Int)
  hint: count may be null (Int?): check it first with 'if count != null:', and inside that block it is Int, or give a fallback with or_else from @std/nulls.spr.
```

How to read it:

- `count` is the result of `count.toIntOrNull()`, of type `Int?`;
- line 2, column 7 is the `+`;
- the message says `+` cannot be used on a value that may be `null`, with actual types `Int?` and `Int`;
- the hint gives two ways out: check with `if count != null:` first, or give a fallback with `or_else` from `@std/nulls`. Both appear later in this chapter.

String concatenation is the same: in `"count " + count`, a `count` of type `Int?` gives this error; only after the check does it work.

## 13.3 The four narrowing positions

The compiler does not narrow only inside `if != null`. It recognizes four common shapes:

<<< @/snippets/book_en/ch08_narrowing.spr

```text
count 3
none
true
false
empty
hi
42
```

1. **Early return.** `if count == null: return "none"`; after that, `count` is no longer nullable. Both `describe` and `double` do this: `describe(3, true)` passes the `count == null` check without returning, so it prints `count 3`.
2. **Inside an `if x != null:` branch — including its `elif` and `else`.** `describe(none, false)` receives `null` and takes `return "none"`, printing `none`.
3. **The right side of `and`.** In `value != null and value > 0`, the right side only runs when the left is true, so `value` has a value there. `first_positive(5)` is `true`, `first_positive(none)` is `false`.
4. **The right side of `or`.** In `text == null or text.length() == 0`, the right side only runs when `text` is not `null`. `label("")` prints `empty`, `label("hi")` prints `hi`.

All of this has one condition: the name being checked must be **unchangeable** — a `let` binding, a parameter or a loop variable. `describe`'s `count` is a parameter; `double`'s `value` is too.

## 13.4 Deliberate mistake: a `var` does not narrow

With the same check, a `var` is refused:

<<< @/snippets/book/ch08_var_narrowing.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:3:11: Operator '+' cannot use a value that may be null (expected non-null operands, actual Int? and Int)
  hint: count may be null (Int?), and a var never narrows: copy it into a let (let current = count), check 'if current != null:', and use current inside that block.
```

Why? A `var` may have been changed somewhere between the check and the use (for example by a function call that modifies it). The compiler assumes nothing. The fix is the hint's: first `let current = count`, then check `current`.

**Class fields behave the same way**: a field does not narrow either, and the compiler's hint is the same one it gives for a `var`: copy it into a `let` first. This is one more reason chapter 3 suggested `let` as the default.

## 13.5 Deliberate mistake: putting `null` into an `Int`

<<< @/snippets/book/ch13_null_assign.spr

```text
SPR-TYPE-NULL [TYPE] main.spr:1:14: null is not assignable to Int (initializer); use Int? (expected Int, actual null)
```

- the code is `SPR-TYPE-NULL`, at the `null` in `= null`;
- the message says `null` is not assignable to `Int` and gives the fix: `use Int?`.

`let n: Int? = null` compiles. To hold `null`, the type must carry the question mark.

Collection types work the same, and the position of the question mark matters:

- `List[Int?]`: the list itself always exists; its **elements** may be `null`;
- `List[Int]?`: the **list itself may be `null`**; its elements do not.

## 13.6 continue and break narrow too

Inside a loop, `continue` and `break` end the current path, just like an early `return`. Here are two common patterns:

<<< @/snippets/book/ch13_loop_narrow.spr

```text
Ada
none
3
```

- `first_seen` returns the first name that is not `null`. The loop variable `name` is `String?`; `if name == null: continue` skips the `null`s, and in `return name` after it, `name` has narrowed to `String`.
- `sum_until_missing` adds until it meets a `null`: `if value == null: break` ends the loop, and `total += value` can only be written after the narrowing.
- `first_seen([null, "Ada", "Bo"])` skips `null` on the first round and returns `"Ada"` on the second.
- `first_seen([null])` is all `null` and returns `"none"`.
- `sum_until_missing([1, 2, null, 4])` reaches 1 + 2 = 3 when it meets the `null` and stops, printing `3`.

Follow `first_seen([null, "Ada", "Bo"])`:

| round | `name` | check | next |
|---|---|---|---|
| 1 | `null` | `name == null` is true | `continue` to the next round |
| 2 | `"Ada"` | it is not `null` | `return "Ada"` |

## 13.7 Giving a default with or_else

When you do not want to write the `if`, `@std/nulls` provides `or_else(value, fallback)`: use the value if it has one, otherwise the second argument:

<<< @/snippets/book/ch13_or_else.spr

```text
12
0
computing the fallback
5
```

- `stock["tea"]` has 12, so `or_else` returns it; `stock["milk"]` is `null`, so it returns the default 0.
- The last two lines carry a point: `fallback()` is **evaluated before `or_else` is called**, whether or not the first argument is `null`. So `nulls.or_else(5, fallback())` has the value 5, and still prints `computing the fallback` first, then `5`.

This "eager" evaluation means that if the second argument is expensive, or has a side effect (printing, reading a file), it runs even when the first argument has a value. When you want it computed only when needed, write the `if value != null:` yourself.

`@std/nulls` also has `require(value, message)`: it returns the value, and throws an error carrying the message when the value is `null`. Its signature says `throws`, which belongs to the next chapter.

::: tip Coming from another language?
- Sprig has no `?.`, `??` or `?:` nullable operators; the check has to be written as an `if`, so where a `null` was handled is visible at a glance.
- "Optional value" is a library type in many languages (such as `Optional`); Sprig's question mark is built into the language.
- Objects returned by Java methods are treated as nullable in Sprig; chapter 21 shows how to work with them.
:::

## Summary

- `T?` may be `null`; `T` always has a value; the two are not interchangeable, and `null` cannot go into a type without the question mark.
- The four narrowing positions: early return, an `if != null` branch (with `elif`/`else`), the right of `and`, the right of `or`; `continue` and `break` narrow too.
- Only unchangeable names (`let`, parameters, loop variables) narrow; copy a `var` or a field into a `let` before checking it.
- There is no `?.` and no `??`; defaults come from `nulls.or_else` (whose second argument is always evaluated), and "must have a value" from `nulls.require`.

## Exercises

**Exercise 1 (returning a nullable value).** Write `first_long(words: List[String], min: Int) -> String?` returning the first word at least `min` characters long, or `null`. The caller checks it and prints it.

::: details Answer
<<< @/snippets/book/ch13_ex1.spr

```text
hello
```
:::

**Exercise 2 (a default for a map lookup).** Given a `Map[String, Int]`, use `or_else` to print the value of an existing key, then 0 for a missing one.

::: details Answer
<<< @/snippets/book/ch13_ex2.spr

```text
36
0
```
:::

**Exercise 3 (skip the nulls).** Write `sum_known(values: List[Int?]) -> Int` adding the values that are not `null` and skipping the `null`s with `continue`. Check that `[1, null, 2, null, 3]` gives 6.

::: details Answer
<<< @/snippets/book/ch13_ex3.spr

```text
6
```
:::

**Exercise 4 (fix the `var`).** This does not compile; make it print `40`:

```sprig
var maybe: Int? = 4
if maybe != null:
    print(maybe * 10)
```

::: details Answer
<<< @/snippets/book/ch13_ex4.spr

```text
40
```

Read `maybe` into `let value = maybe`, then check `value != null`.
:::

Next chapter: [Handling errors](/en/tutorial/ch14-errors).
