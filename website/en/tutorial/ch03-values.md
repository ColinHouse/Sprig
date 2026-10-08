# 3. Values, variables and arithmetic

In this chapter you will:

- name values with `let` and `var`;
- meet the basic types: `Int`, `Float`, `Bool`, `String`;
- add, subtract, multiply, take remainders and divide integers;
- see why `Int` and `Float` don't mix;
- use booleans and short-circuit evaluation;
- read operator precedence;
- find out what happens when an integer overflows.

## 3.1 Values and names

Programs need to remember numbers and text so they can compute with them. A name for a value is a **variable**. Here is a complete example:

<<< @/snippets/book_en/ch02_bindings.spr

```text
Ada
2
true
```

Line by line:

- `let name = "Ada"` binds the name `name` to the string `"Ada"`. A `let` binding **cannot be assigned again**. The type isn't written down; Sprig infers `String` from the value on the right.
- `var visits = 0` declares a variable that **can** be reassigned. Here the inferred type is `Int`.
- `visits += 1` is shorthand for "add 1 and store it back", the same as `visits = visits + 1`. `visits` is now 1.
- `visits = visits + 1` makes it 2.
- `let height: Float = 1.68` writes the type out this time. The form is "name, colon, type". You rarely need it; write it to be explicit or to pin down a type.
- `let tall = height > 1.6` compares two decimals and produces a **boolean** (`Bool`): true or false. So `tall` is `true`.
- The three `print` calls print `name`, `visits` and `tall` in order.

Words worth keeping:

| Word | Meaning |
|---|---|
| value | a piece of data in a program, such as `"Ada"`, `2`, `true` |
| variable | a name for a value |
| type | what kind of value it is, such as `Int`, `Float`, `Bool`, `String` |
| binding | the link between a name and a value |
| inference | the compiler working out the type from the value |

**Reach for `let`, and use `var` only when you really reassign.** This is more than style: chapter 13 shows that the compiler can reason much further about `let` bindings (checking one for null makes it non-null afterwards, for instance) than about `var`.

### Deliberate mistake: assigning to a `let`

<<< @/snippets/book/ch02_let_assign.spr

```text
SPR-NAME-LET-ASSIGN [TYPE] main.spr:2:1: Cannot assign to immutable binding 'name'; declare it with var
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

The position `2:1` points at `name` at the start of the second line. The message says it plainly: the binding cannot change; if it must, declare it with `var`. Notice the compiler rejected this **before running anything**; not a single line executes.

## 3.2 Arithmetic

Numbers add, subtract and multiply with `+`, `-` and `*`. Integer division is special, and remainders use `%`:

<<< @/snippets/book/ch03_arithmetic.spr

```text
9
5
14
3
1
-1
1
9000000000000
```

- `a + b`, `a - b`, `a * b` give 9, 5 and 14.
- `a.divTrunc(b)` is "divide, then drop the fraction". 7 divided by 2 is 3.5, so dropping the fraction gives 3.
- `a % b` is the remainder. 7 divided by 2 is 3 remainder **1**.
- `-7 % 3` is **-1** and `7 % -3` is **1**: the sign of the remainder follows the **left** operand.
- `9000000000 * 1000` is nine trillion; it fits in an `Int`, so it prints as `9000000000000`.

### Deliberate mistake: plain integer division

<<< @/snippets/book/ch02_division.spr

```text
SPR-NUM-DIVISION [TYPE] main.spr:3:7: Integer / would truncate; use a.divTrunc(b), or convert both operands explicitly (expected explicit division, actual Int / Int)
  hint: Write a.divTrunc(b) to drop the remainder on purpose, or a.toFloatExact() / b.toFloatExact() for a Float result. text.fixed(value, decimals) from @std/text.spr prints a Float with that many decimals.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

Should `7 / 2` be 3 or 3.5? Languages disagree. Sprig refuses to choose for you: write `a.divTrunc(b)` for 3, or convert both sides to `Float` for 3.5 (next section). The `hint` gives both spellings.

## 3.3 Changing a variable: compound assignment

A `var` can be updated with `+=`, `-=` and `*=`; decimals also get `/=`:

<<< @/snippets/book/ch03_compound.spr

```text
3.0
9
```

Follow the values through:

| Statement | `total` | `count` |
|---|---|---|
| `var total = 6.0` | 6.0 | — |
| `total *= 2.0` | 12.0 | — |
| `total /= 4.0` | 3.0 | — |
| `var count = 7` | 3.0 | 7 |
| `count += 3` | 3.0 | 10 |
| `count -= 1` | 3.0 | 9 |

`total *= 2.0` is simply `total = total * 2.0`; `count -= 1` is `count = count - 1`. There is no `%=`, and no `++` or `--`: `count++` is rejected, and the error tells you to write `count += 1`.

::: tip Coming from another language?
Sprig's `let` / `var` are like `const` / `let` (or `final` / a plain variable) elsewhere. There is no `++`/`--` and no `%=`; `/=` exists only for decimals, since integer `/` itself is not allowed. None of this is missing functionality: every change to a value stays visible.
:::

## 3.4 Integers and decimals don't mix

Two numeric types do most of the work:

- `Int`: a signed 64-bit integer, written `7`, `-3`, `9000000000`.
- `Float`: a double-precision decimal (IEEE 754 binary64), written with a decimal point or exponent: `2.5`, `1.0`, `10.0`.

They **do not convert implicitly**. A deliberate mistake:

<<< @/snippets/book/ch02_mixed.spr

```text
SPR-NUM-MIXED [TYPE] main.spr:3:7: Operator '*' has no implicit conversion between Float and Int (expected matching numeric families, actual Float and Int)
  hint: Convert the Int side: count.toFloatExact().
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

`price` is a `Float`, `count` is an `Int`, and `*` refuses "a Float times an Int". Convert explicitly, as the hint says:

<<< @/snippets/book/ch03_float.spr

```text
1.0
3.0
10.0
0.3333333333333333
```

- `let whole: Float = 1` allows an integer literal where a `Float` is expected, because this 1 is represented exactly.
- `1.toFloatExact() + 2.0` is 3.0: convert first, then add. The `Exact` in the name means "fail rather than silently lose precision"; `toFloatLossy()` accepts the rounding instead.
- `2.5 * 4.0` is 10.0: multiplying two Floats gives a Float, and a Float always prints with a decimal part (`10.0`, never `10`).
- `1.0 / 3.0` is `0.3333333333333333`: `/` works for decimals. Binary floating point cannot represent 0.1 or 1/3 exactly, which is why the tail is long. Never use `Float` for money; use cents as integers, or `Decimal` from chapter 17.

The other direction is explicit too: `2.7.toIntTrunc()` is 2 (the fraction is dropped), and `6.0.toIntExact()` is 6 (anything but a whole number is an error). The rule in one sentence: **the name says what may be lost, and you choose**.

## 3.5 Booleans and short-circuiting

Comparisons `>`, `<`, `>=`, `<=`, `==`, `!=` produce a `Bool`. Logic uses the words `and`, `or` and `not`, not `&&`, `||` or `!`:

<<< @/snippets/book_en/ch02_bool.spr

```text
true
true
```

- `count > 0 and count < 10`: both comparisons are true, so `and` gives `true`.
- `not open or count == 3`: `not open` is `false` and `count == 3` is `true`, so `or` gives `true`.
- `==` compares for equality (two equals signs); `=` assigns (one). Don't confuse them.

`and` and `or` **short-circuit**: when the left side of `and` is false the right side is not evaluated at all, and the same goes for a true left side of `or`. This is not an optimization; it prevents errors:

<<< @/snippets/book/ch03_short_circuit.spr

```text
false
true
```

`1.divTrunc(0)` divides by zero and would fail if it ran. But the right side of `false and ...` never runs, so the first line calmly prints `false`; likewise for `true or ...`. Without short-circuiting, this program would have crashed.

### Deliberate mistake: there is no truthiness

<<< @/snippets/book/ch03_bool_operand.spr

```text
SPR-TYPE-OPERAND [TYPE] main.spr:1:10: 'and' requires Bool operands (expected Bool, actual Int)
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

In many languages `0`, an empty string or an empty list counts as "false". Sprig has no such rule: logic operations, and later `if` and `while`, accept `Bool` only. "The count is positive" is `count > 0`; "the list is not empty" is `items.size() > 0`.

## 3.6 Order of operations

As in arithmetic class, `*` and `%` bind tighter than `+` and `-`; comparisons come after arithmetic; parentheses win:

<<< @/snippets/book/ch03_precedence.spr

```text
14
20
true
true
```

- `2 + 3 * 4` is 14, not 20: `3 * 4` happens first. To add first, write `(2 + 3) * 4`, which is 20.
- `1 + 1 == 2` is `true`: add, then compare.
- `not false` is `true`.
- Comparisons don't chain. `1 < 2 < 3` does not mean "1 is less than 2 and 2 is less than 3"; the compiler rejects it. Write `1 < 2 and 2 < 3`.

## 3.7 Overflow: integers never wrap around

Adding, subtracting or multiplying `Int`s checks whether the result still fits in 64 bits. If it doesn't, the program **stops with an error** instead of quietly wrapping to a negative number. Try this two-line program:

```sprig
let top = 9223372036854775807
print(top + 1)
```

Run `sprig run overflow.spr` and the screen shows:

```text
SPR-RUNTIME-EXCEPTION [RUNTIME] overflow.spr:2:1: Numeric error: Int addition overflow
  hint: The exact result does not fit in the integer type. Check before the operation, for example 'if value > 0 and total > 9223372036854775807 - value:' for an Int addition, or catch it: import java.lang.ArithmeticException as ArithmeticException, then put the operation in 'try:' with 'catch problem: ArithmeticException:'. See `sprig help numerics`. Run with --stacktrace to see the JVM stack.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

This is a **runtime error**: the program passed `sprig check` and failed when it reached that line; run with `--stacktrace` to see the JVM stack. The hint suggests checking the range before the operation, or catching the exception (`try`/`catch` arrives in chapter 14). `Float` is different: `1.0 / 0.0` gives `Infinity`, `0.0 / 0.0` gives `NaN`, and neither raises an error, matching IEEE 754.

## Summary

- `let` binds once, `var` can change; prefer `let`. Types are inferred, or written as `height: Float`.
- The basic types are `Int` (64-bit integer), `Float` (double-precision decimal), `Bool` and `String`.
- Divide integers with `divTrunc`; take remainders with `%`, whose sign follows the left operand.
- Compound assignment `+= -= *=` works on numbers, plus `/=` for decimals; there is no `++`, `--` or `%=`.
- `Int` and `Float` never mix; conversion names say whether information can be lost (`Exact` fails, `Lossy`/`Trunc` lose).
- `and`, `or`, `not` accept `Bool` only, and `and`/`or` short-circuit.
- `*` before `+`, parentheses first, comparisons never chain.
- Integer overflow is a runtime `SPR-RUNTIME-EXCEPTION`; it never wraps around.

## Exercises

1. A day has 24 hours, an hour has 60 minutes, and a minute has 60 seconds. Compute, in one arithmetic expression, how many seconds 2 hours 30 minutes is.
   Hint: work out the minutes first, then multiply by 60; parentheses change the order.

::: details Answer
<<< @/snippets/book/ch03_ex_seconds.spr

```text
9000
```

`2 * 60 + 30` multiplies before it adds.
:::

2. Before looking, write down `17 % 5` and `-17 % 5`, then run them to check.
   Hint: the sign of the remainder follows the left operand.

::: details Answer
<<< @/snippets/book/ch03_ex_remainder.spr

```text
2
-2
```

The remainder takes the sign of `-17`.
:::

3. `7 / 2` doesn't compile. Change it into a version that produces 3.5.
   Hint: `toFloatExact()` turns an integer into a decimal.

::: details Answer
<<< @/snippets/book/ch03_ex_divide.spr

```text
3.5
```

Convert both sides to `Float`, then divide. For 3 instead, write `7.divTrunc(2)`.
:::

4. Use `var` and `+=` to write a counter that counts from 0 to 3, printing the current value after each step.
   Hint: `var counter = 0`, then three `counter += 1` lines, each followed by a `print`.

::: details Answer
<<< @/snippets/book/ch03_ex_counter.spr

```text
1
2
3
```
:::

5. Why doesn't this compile? Make it work.

```sprig
let ok = 1 and true
```

Hint: `and` needs `Bool` on both sides; write a comparison first, like `1 > 0`.

::: details Answer
`and` takes two `Bool`s, and `1` is an `Int`. Compare first, then `and`:

<<< @/snippets/book/ch03_ex_and.spr

```text
true
```
:::

Next chapter is about text: [Chapter 4: Text](/en/tutorial/ch04-text).
