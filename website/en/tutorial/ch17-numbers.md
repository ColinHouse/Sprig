# 17. More about numbers

Chapter 3 introduced `Int` and `Float`: 64-bit integers, binary64 floating point, no integer `/`, and no mixing of `Int` and `Float`. This chapter adds the other four numeric types and shows how to convert between them:

In this chapter you will learn:

- what `Int32`, `Float32`, `Decimal` and `BigInt` are and when to reach for them;
- how narrow types widen for free and wide types narrow only through a method;
- what the `Exact`, `Lossy` and `Trunc` suffixes in conversion names mean;
- exact money arithmetic with `Decimal`, and the real names of its rounding modes;
- `Float`'s `NaN`, `Infinity`, `sqrt` / `floor` / `ceil` / `abs`, `isNaN` and `approxEqual`;
- `abs`, `min`, `max`, `sign`, `clamp`, `floor_div` and `isqrt` in `@std/math`.

## 17.1 The six numeric types

| Type | What it is | How to build one |
|---|---|---|
| `Int` | signed 64-bit integer, overflow-checked | literal `42` |
| `Int32` | signed 32-bit integer, Java's `int` | typed literal: `let n: Int32 = 7` |
| `BigInt` | an integer of any size | `BigInt.parse("...")`, `BigInt.fromInt(7)` |
| `Float` | IEEE 754 binary64 | literal `2.5`, `1.0` |
| `Float32` | IEEE 754 binary32, Java's `float` | typed literal: `let f: Float32 = 0.5` |
| `Decimal` | arbitrary-precision base 10 | `Decimal.parse("19.99")`, `7.toDecimal()` |

Day to day, `Int` and `Float` are enough. `Int32` and `Float32` exist mainly for the Java boundary (chapter 21); `Decimal` is for money; `BigInt` handles integers past 64 bits. They all obey chapter 3's general rule: **different numeric families never convert implicitly**.

## 17.2 Int32 and Float32: the narrow types that match Java

<<< @/snippets/book_en/ch02_int32_float32.spr

```text
2000000001
4000000000
0.1
0.10000000149011612
1.0
```

Line by line:

- `let small: Int32 = 2000000000`: an ordinary integer literal is an `Int`; write the type on the declaration to get an `Int32` (same for `0.5` and `Float32`). The literal must fit; if it does not, the compiler reports `SPR-NUM-RANGE` (an example comes later in this section).
- `let wide: Int = small`: **narrow values widen for free**. An `Int32` goes where an `Int` is expected with no method call, and a `Float32` goes where a `Float` is expected the same way. The reverse (`Int` → `Int32`, `Float` → `Float32`) always needs an explicit method.
- `wide + 1` is `Int` arithmetic and prints `2000000001`.
- `small + wide`: mixing `Int32` with `Int` gives the wider `Int`, printing `4000000000`.
- `let narrow: Float32 = 0.1`: `0.1` has no exact binary representation, so 32 bits hold the closest value. Printing `narrow` gives `0.1` — the shortest decimal that reads back as the same 32-bit value.
- `narrow.toFloat()` widens it exactly to 64-bit `Float`, which exposes the stored value: `0.10000000149011612`.

  A trace of that value's two shapes:

  | Step | Value in memory | Printed |
  |---|---|---|
  | `let narrow: Float32 = 0.1` | closest 32-bit float to 0.1 | `0.1` (shortest 32-bit form) |
  | `narrow.toFloat()` | the same value in 64 bits | `0.10000000149011612` (shortest 64-bit form) |

- `let whole: Float = 1`: an integer literal may go to a floating variable only if it is **exactly representable**. It prints `1.0`.

So what type does `Float32` arithmetic produce? A bare literal follows the other side:

<<< @/snippets/book/ch17_f32_literal.spr

```text
1.1
1.1000000014901161
```

- `a + 1.0`: `a` is `Float32`, so the untyped `1.0` literal is a `Float32`; the arithmetic is 32-bit and prints `1.1`.
- `b + c`: `c` is a `Float` variable, so the operation widens to 64 bits; the value in `b` was never exactly `0.1`, and the sum prints `1.1000000014901161`.

Mixing narrow types has one red line — `Float32` and `Int` do not compute together:

### Deliberate mistake: `Float32` plus `Int`

<<< @/snippets/book/ch17_mixed32.spr

```text
SPR-NUM-MIXED [TYPE] main.spr:3:7: Operator '+' has no implicit conversion between Float32 and Int (expected matching numeric families, actual Float32 and Int)
  hint: Convert the Int side: b.toFloatExact().
```

`expected matching numeric families` means the operands are from different families. Follow the hint and convert `b` to floating point (`b.toFloatExact()`, next section), or make `a` an `Int`.

### Deliberate mistake: a literal that does not fit

<<< @/snippets/book/ch02_literal_range.spr

```text
SPR-NUM-RANGE [TYPE] main.spr:1:16: Integer literal 9007199254740993 is not exactly representable as Float (expected Float, actual 9007199254740993)
```

2^53 + 1 is beyond the integers binary64 can represent exactly, and Sprig will not quietly turn it into 2^53. An `Int32` literal past 32 bits is the same code.

Overflow the compiler cannot see fails at run time. Save the next two lines as `overflow.spr` and run `sprig run overflow.spr`:

```sprig
let a: Int32 = 2000000000
print(a + a)
```

```text
SPR-RUNTIME-EXCEPTION [RUNTIME] overflow.spr:2:1: Numeric error: Int32 addition overflow
  hint: The exact result does not fit in the integer type. Check before the operation, for example 'if value > 0 and total > 9223372036854775807 - value:' for an Int addition, or catch it: import java.lang.ArithmeticException as ArithmeticException, then put the operation in 'try:' with 'catch problem: ArithmeticException:'. See `sprig help numerics`. Run with --stacktrace to see the JVM stack.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

`2000000000 + 2000000000` is `4000000000`, which does not fit in `Int32`. As with `Int`, Sprig reports an error instead of wrapping around. The hint's second option needs Java's exception type, deferred to chapter 21.

## 17.3 How conversions are named

Every conversion between number types follows one naming rule: **the name says whether information can be lost**.

<<< @/snippets/book/ch02_conversions.spr

```text
300
300.0
2
-1
true
```

- `n.toInt32Exact()`: `300` is within `Int32`, so it succeeds and prints `300`.
- `n.toFloatExact()`: prints `300.0`. If the `Int` were too large for an exact `Float`, `Exact` would fail **at run time** rather than round quietly.
- `f.toIntTrunc()`: `2.75` drops its fraction and gives `2`. `Trunc` says plainly "the fraction is thrown away".
- `a.compareTo(5)`: every numeric type has `compareTo`, its argument must be the receiver's own type, and it returns `Int32`: `3` is less than `5`, so `-1`.
- `"pear".compareTo("apple") > 0`: `String` has it too, in lexicographic order; `true`.

The rule table:

| Name | Meaning | Examples |
|---|---|---|
| no suffix | always succeeds | `Int32.toInt()`, `Int32.toFloat()`, `Float32.toFloat()`, `value.toDecimal()` |
| `Exact` | fails when information would be lost | `Int.toFloatExact()`, `Float.toIntExact()`, `Int.toInt32Exact()` |
| `Lossy` | rounding is allowed | `Int.toFloatLossy()`, `Float.toFloat32Lossy()` |
| `Trunc` | the fraction is dropped | `Float.toIntTrunc()` |

The common conversions:

| From → to | How to write it |
|---|---|
| `Int32` → `Int`, `Float32` → `Float` | assignment (widens for free), or `.toInt()` / `.toFloat()` |
| `Int` → `Int32` | `.toInt32Exact()`, out-of-range fails |
| `Int` → `Float` | `.toFloatExact()` (fails on lost precision) or `.toFloatLossy()` (rounds) |
| `Int` → `Decimal` | `.toDecimal()` |
| `Float` → `Int` | `.toIntExact()` (fails on a fraction) or `.toIntTrunc()` (drops it) |
| `Float` → `Float32` | `.toFloat32Exact()` or `.toFloat32Lossy()` |
| `BigInt` → `Int` / `Float` / `Decimal` | `.toIntExact()` / `.toFloatExact()`, `.toFloatLossy()` / `.toDecimal()` |
| `Decimal` → `Int` / `Float` | `.toIntExact()` / `.toFloatExact()`, `.toFloatLossy()` |

Write the wrong name and the compiler tells you the right one:

### Deliberate mistake: `Int` has no `toFloat()`

<<< @/snippets/book/ch17_no_tofloat.spr

```text
SPR-NAME-UNRESOLVED [NAME] main.spr:2:7: Type Int has no method 'toFloat'
  hint: An Int beyond 2^53 has no exact Float: write value.toFloatExact(), which fails instead of rounding, or value.toFloatLossy(), which rounds.
```

`Int.toFloat()` and `Float.toInt()` deliberately do not exist: neither could tell you whether it loses information. The hint names both candidates and their difference — `Exact` for safety, `Lossy` when rounding is fine. Likewise `Decimal` has no static `fromInt` (write `value.toDecimal()`), and `BigInt` uses `BigInt.fromInt(value)`.

## 17.4 Decimal: the money type

`0.1 + 0.2` is `0.30000000000000004` in `Float`, because binary floating point cannot represent most decimal fractions. `Decimal` is arbitrary-precision base 10, so addition, subtraction and multiplication are exact:

<<< @/snippets/book_en/ch02_decimal.spr

```text
59.97
0.3333
123456789012345678901234567891
1
```

- `Decimal.parse("19.99")` builds one from text (`Decimal` and `BigInt` have no literals). `price * 3.toDecimal()` is exactly `59.97`.
- `1.toDecimal().divide(3.toDecimal(), 4, "HALF_EVEN")`: division **must** state how many decimal places and which rounding mode; this one gives `0.3333`.
- `BigInt.parse("123456789012345678901234567890")` is as long as it needs to be; `+ BigInt.fromInt(1)` gives `...891` with no overflow.
- `huge.compareTo(BigInt.fromInt(1))` returns `1`, meaning `huge` is larger.

The third argument to division is a **rounding-mode name**, and it must be one of Java's `RoundingMode` names; the common ones are `"HALF_UP"` (round half away from zero), `"HALF_EVEN"` (banker's rounding, ties to the even digit) and `"DOWN"` (truncate toward zero):

<<< @/snippets/book/ch17_decimal_modes.spr

```text
3
2
true
0
0.3333
59.97
```

- `2.5` to zero decimal places: `HALF_UP` gives `3`, `HALF_EVEN` gives `2` (ties to the even digit).
- `Decimal` `==` compares values, not text: `0.30 == 0.3` is `true` and `compareTo` returns `0`.
- `"DOWN"` truncates to four places and gives `0.3333`.
- Multiplication is exact as above.

### Deliberate mistake: dividing `Decimal` with `/`

<<< @/snippets/book/ch17_decimal_div.spr

```text
SPR-NUM-DIVISION [TYPE] main.spr:3:7: Decimal division needs an explicit scale and rounding mode
  hint: Use a.divide(b, scale, "HALF_EVEN") or another rounding mode.
```

`1/3` has no finite decimal expansion and Sprig will not pick a precision for you. Write `a.divide(b, scale, mode)` as the hint says.

A wrong mode name is not a compile error — it fails at run time. Save these two lines as `modes.spr` and run:

```sprig
let third = 1.toDecimal().divide(3.toDecimal(), 4, "NEAREST")
print(third)
```

```text
SPR-RUNTIME-EXCEPTION [RUNTIME] modes.spr:1:1: Numeric error: Decimal division failed: No enum constant java.math.RoundingMode.NEAREST
  hint: Guard the checked arithmetic or use an explicit conversion; see `sprig help numerics`. Run with --stacktrace to see the JVM stack.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

`NEAREST` is not a legal name, and the error even recites the Java enum it was looking in. The legal names are exactly Java's `RoundingMode` constants (`HALF_EVEN`, `HALF_UP`, `DOWN` and the rest).

::: tip Coming from another language?
`Decimal` is Java's `BigDecimal` and `BigInt` is `BigInteger`. Other languages often hide these behind a library; Sprig makes them native types but keeps the "the name states the cost" rule: no implicit `Float`/`Decimal` conversion, and no `Decimal.fromInt`. The Java interop chapter shows `toJava()` / `fromJava()` moving between the wrapper types and Java's own.
:::

## 17.5 BigInt: as large as it needs to be

<<< @/snippets/book/ch17_bigint.spr

```text
9999999999999999999800000000000000000001
14285714285714285714
1
1.0E20
```

- `big * big`: a 20-digit number squared is a 40-digit number, exactly, with no overflow. `+`, `-` and `*` are all exact.
- `big.divTrunc(BigInt.fromInt(7))` and `big % BigInt.fromInt(7)`: integer division and remainder use `divTrunc` and `%`, and a zero divisor fails.
- `big.toFloatLossy()`: converting to `Float` may round, so it prints `1.0E20` in scientific notation.

Converting a `BigInt` back to `Int` uses `.toIntExact()`; when the number is too large it fails at run time with `Numeric error: BigInt outside Int range` — like every `Exact` conversion, refusing is better than silently truncating. `toDecimal()` cannot fail.

## 17.6 Float's special values and helper methods

Floating point has values beyond ordinary numbers, and a few static helpers (written on the type name `Float`):

<<< @/snippets/book/ch17_float_helpers.spr

```text
1.4142135623730951
-2.0
-1.0
2.5
NaN
0.30000000000000004
Infinity
NaN
true
true
true
true
true
```

Line by line:

- `Float.sqrt(2.0)`: the square root; `1.4142135623730951` is the closest binary64 value.
- `Float.floor(-1.5)` rounds down to `-2.0` and `Float.ceil(-1.5)` rounds up to `-1.0`. Both return a `Float` (note the `.0`); for an integer, use `toIntExact()` once the value is whole.
- `Float.abs(-2.5)` is `2.5`.
- `Float.sqrt(-1.0)` is `NaN` (Not a Number), not an error — an IEEE 754 rule.
- `0.1 + 0.2` is `0.30000000000000004`: binary floating point's classic error, printed as it is rather than hidden.
- `1.0 / 0.0` is `Infinity` and `0.0 / 0.0` is `NaN`; floating division by zero is not an error (only integer division by zero is).
- `-0.0 == 0.0` is `true`: positive and negative zero compare equal, although `compareTo` distinguishes them.
- `(0.1 + 0.2).approxEqual(0.3, 1e-15)` is `true`: `approxEqual(value, absolute tolerance)` is an explicit near-equality, kinder than `==` but still not a mathematical proof.
- `(0.0 / 0.0).isNaN()`, `(1.0 / 0.0).isInfinite()` and `1.0.isFinite()` classify the three special-value families, all `true` here.

`NaN` compares `false` to everything, itself included; never search for it with `==`, use `isNaN()`.

## 17.7 `@std/math`

The small integer helpers live in `@std/math` (the `import` was taught in chapter 4):

<<< @/snippets/book/ch17_math.spr

```text
5
3
7
-1
10
-4
4
```

- `math.abs(-5)` is `5`; `math.min(3, 7)` and `math.max(3, 7)` give `3` and `7`.
- `math.sign(-5)` is `-1` (negative), `0` for zero and `1` for positive.
- `math.clamp(12, 0, 10)` limits `12` to the range `0..10` inclusive, giving `10`.
- `math.floor_div(-7, 2)` gives `-4`: floor toward negative infinity. That differs from `divTrunc` (toward zero, `-3`), so pick deliberately for negative operands — exactly why chapter 3 insists integer division say what it means.
- `math.isqrt(17)` gives `4`: the integer floor of the square root.

Failing `@std/math` functions use chapter 14's `throws Error`, so they go straight into `try`:

<<< @/snippets/book/ch17_math_errors.spr

```text
caught: isqrt input must not be negative
caught: clamp lower bound must not exceed upper bound
```

- `math.isqrt(-1)` throws an `Error` saying `isqrt input must not be negative`.
- `math.clamp(5, 10, 0)` has a lower bound above its upper bound and throws `clamp lower bound must not exceed upper bound`.
- Both are caught by `catch problem: Error` — standard-library failures travel the same `Error` channel as yours (Java exceptions do not; chapter 21).

## 17.8 Summary

- Six numeric types: `Int`, `Int32`, `BigInt`, `Float`, `Float32`, `Decimal`; families never convert implicitly.
- Narrow values widen for free (`Int32` → `Int`, `Float32` → `Float`); wide to narrow always writes a method.
- Conversion naming: no suffix always succeeds, `Exact` fails when information would be lost, `Lossy` rounds and `Trunc` drops the fraction.
- `Decimal` adds, subtracts and multiplies exactly; division writes `divide(scale, mode)` with a Java `RoundingMode` name; `BigInt` is unbounded and narrows only through `Exact`.
- Floats have `NaN`, `Infinity` and `-0.0`; handle them with `isNaN`, `isInfinite`, `isFinite` and `approxEqual`, never `==`.
- `@std/math` offers `abs`, `min`, `max`, `sign`, `clamp`, `floor_div` and `isqrt`, throwing `Error` on failure.

## 17.9 Exercises

**Exercise 1 (easy)** Print `0.1 + 0.2` (`Float`) and `Decimal.parse("0.1") + Decimal.parse("0.2")` and compare.

Hint: one carries the binary error, the other is exact base 10.

::: details Answer
<<< @/snippets/book/ch17_ex1.spr

```text
0.30000000000000004
0.3
```
:::

**Exercise 2** Print `(-7).divTrunc(2)` and `math.floor_div(-7, 2)` and explain the difference.

Hint: `divTrunc` rounds toward zero, `floor_div` toward negative infinity.

::: details Answer
<<< @/snippets/book/ch17_ex2.spr

```text
-3
-4
```
:::

**Exercise 3** Use `math.isqrt` on `50` and `math.clamp` to limit `12` to `0..10`.

Hint: `isqrt(50)` is 7 (7×7=49) and `clamp`'s upper bound 10 pulls 12 down to 10.

::: details Answer
<<< @/snippets/book/ch17_ex3.spr

```text
7
10
```
:::

**Exercise 4 (hard)** Convert `12345` to `Int32` and to `Float` (rounding allowed), then truncate `2.5` to an `Int`.

Hint: `Int` → `Int32` is `toInt32Exact()`; `Int` → `Float` can lose precision, so use `toFloatLossy()`; `Float` → `Int` with a fraction uses `toIntTrunc()`.

::: details Answer
<<< @/snippets/book/ch17_ex4.spr

```text
12345
12345.0
2
```
:::

Next chapter: [Modules, projects and dependencies](/en/tutorial/ch18-modules-projects) — splitting code into files and pulling in other people's libraries.
