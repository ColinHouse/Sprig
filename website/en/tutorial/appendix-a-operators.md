# Appendix A. Operators and precedence

This appendix is a reference sheet: every Sprig operator in one place, with its precedence, its associativity and its real behavior. Every program comes from a snippet in the repository, and the compiler produced the outputs.

## A.1 The operators at a glance

**Assignment** (only at the start of a statement, with an expression on the right):

| Operator | Example | Meaning |
|---|---|---|
| `=` | `total = 0` | assignment; the target must be a `var` variable, a `var` field or an element of a mutable collection |
| `+=` | `total += 1` | add, then assign |
| `-=` | `total -= 1` | subtract, then assign |
| `*=` | `total *= 2` | multiply, then assign |
| `/=` | `ratio /= 2.0` | divide, then assign; integer `/` is still not allowed |

**Logic**: `and`, `or`, `not`; the operands must be `Bool`.

**Comparison**: `==`, `!=`, `<`, `<=`, `>`, `>=`, `in`.

**Arithmetic**: binary `+`, `-`, `*`, `/`, `%`, and prefix `+`, `-`.

**Postfix**: `.member`, `(arguments)`, `[index]`, and `[TypeArguments]` for generics.

**Other symbols**: `:` (blocks, type annotations, map literals), `,`, `->` (function result), `=>` (lambda body), `?` (nullable type suffix), `#` (comment).

## A.2 Precedence and associativity

From loosest (evaluated last) to tightest:

| Level | Operators | Associativity | Example and result |
|---|---|---|---|
| 1 | `or` | left | `true or false and false` → `true` |
| 2 | `and` | left | |
| 3 | `not` (prefix) | right | `not 1 == 2` → `not (1 == 2)` → `true` |
| 4 | `==` `!=` `<` `<=` `>` `>=` `in` | no chaining | `1 + 1 in [2]` → `(1 + 1) in [2]` → `true` |
| 5 | `+` `-` (binary) | left | `10 - 2 - 3` → `5` |
| 6 | `*` `/` `%` | left | `1 + 2 * 3` → `7` |
| 7 | `+` `-` (unary) | prefix | `-2 * 3` → `-6` |
| 8 | `.member` `(arguments)` `[index]` | left | `items.size()`, `text[0]` |
| 9 | literals, `(...)`, `[...]`, `{...}` | — | `(true or false) and false` → `false` |

Assignment is not an expression but a whole statement, so it does not chain:

<<< @/snippets/book_en/appendix_a_assign_chain.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:4:7: Expected the end of the line, found '='
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

Comparisons cannot chain either (the full error is in the "Deliberate mistakes" section below).

## A.3 Precedence in action

<<< @/snippets/book_en/appendix_a_precedence.spr

```text
7
7
5
2
-6
3
true
true
false
true
true
true
```

Line by line:

- `1 + 2 * 3` is `1 + (2 * 3)`; `2 * 3 + 1` shows that multiplication and division bind tighter than addition and subtraction.
- `10 - 2 - 3` is `(10 - 2) - 3`: one level evaluates left to right.
- `-2 * 3` negates first, then multiplies; `- -3` is 3.
- In `not 1 == 2`, `not` binds looser than `==`, so the comparison happens first and its result is negated.
- In `true or false and false`, `and` binds tighter than `or`; with parentheses, `(true or false) and false` is `false`.
- In `1 + 1 in [2]`, `+` binds tighter than `in`; `"a" in "abc"` finds a substring in a string, and `"a" in {"a": 1}` finds a key in a map.

## A.4 How the operators behave

<<< @/snippets/book_en/appendix_a_semantics.spr

```text
false
true
called true
true
-1
1
3 then 12
18
3.5
```

Line by line:

- `false and loud_true()` and `true or loud_false()` print no `called ...`, so `and`/`or` short-circuit: the right side is not called when it is not needed.
- `true and loud_true()` does call the right side.
- `-7 % 3` is `-1` and `7 % -3` is `1`: the sign of the result of `%` follows the dividend, as in Java and unlike Python.
- `1 + 2 + " then " + 1 + 2` prints `3 then 12`: the first two `Int`s add, and joining with text begins at the first string.
- `total += 3`, `-=` and `*=` lead to 18; `ratio /= 2.0` gives `3.5` (`/=` works for floating-point values).
- Only `var` can be assigned; `let` binds once (Chapter 3).

Details by type:

| Operator | Operands | Behavior |
|---|---|---|
| `+` | either side is `String` | turns the other side into its text and joins, with no need to call `toString()` first |
| `+ - *` | two numbers of the same family | `Int`, `Int32`, `Float`, `Float32`, `Decimal`, `BigInt` all work; the result stays in the same family |
| `%` | two numbers of the same family | `Int`, `Int32`, `Float`, `Float32`, `BigInt`; `Decimal` is not allowed |
| `/` | two `Float` or two `Float32` | fractional division |
| `/` | two `Int`/`Int32`/`BigInt` | **not allowed**, see "Deliberate mistakes" |
| `/` | two `Decimal` | **not allowed**; use `a.divide(b, scale, roundingMode)` |
| `==` `!=` | numbers, `Bool`, `String`, enum, variant, lists, maps | compare by value |
| `==` `!=` | class objects | compare by identity: only the same object is equal, even when fields match (Chapter 11) |
| `< <= > >=` | numbers, `String` | `String` compares by UTF-16 code units; numbers never mix `Int` and `Float` implicitly |
| `in` | `List`, `Map`, `String` | finds an element in a list, a key in a map, or a substring in a string; uses `==` |
| `and` `or` `not` | `Bool` | short-circuit; there is no "nonzero is true" |
| `+ -` | one number | positive or negative sign |

Two nullable scalars can be compared directly with `==`, and two `null`s are equal; but `+` cannot join a value that may be `null`, so check it first (Chapter 13).

## A.5 Operators that do not exist

| What you might write | What Sprig actually does | What to write instead |
|---|---|---|
| `x++`, `x--` | syntax error | `x += 1`, `x -= 1` |
| `a ** b` | no power operator | `Float.sqrt` (or Java's `Math.pow`, Chapter 21) |
| `<<` `>>` `&` `|` `^` `~` | no bitwise operators | `floor_div` and friends from `@std/math`; bitwise work goes through Java (Chapter 21) |
| `&&` `||` `!` | syntax errors | `and`, `or`, `not` |
| `a ? b : c`, `a if c else b` | no ternary expression | an if expression (Chapter 5) |
| `x ?? y`, `x?.y` | syntax errors | `if x != null:`, or `or_else`/`require` from `@std/nulls` (Chapter 13) |
| `1 < x < 3` | comparisons do not chain | `1 < x and x < 3` |
| `===`, `!==` | syntax errors | `==`, `!=` (they are already strict: there are no implicit conversions) |

## A.6 Deliberate mistakes

Chain two comparisons and the compiler stops at the second:

<<< @/snippets/book_en/appendix_a_chained_comparison.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:2:13: missing ')' (unexpected '<')
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

Write `++` and the error hands you the replacement:

<<< @/snippets/book_en/appendix_a_increment.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:3:8: Sprig has no ++ or -- operator
  hint: Write 'count += 1' or 'count -= 1'.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

Integer division is rejected even when the quotient is a whole number, because `/` must mean "real division":

<<< @/snippets/book_en/appendix_a_int_division.spr

```text
SPR-NUM-DIVISION [TYPE] main.spr:2:7: Integer / would truncate; use a.divTrunc(b), or convert both operands explicitly (expected explicit division, actual Int / Int)
  hint: Write 7.divTrunc(2) to drop the remainder on purpose, or 7.toFloatExact() / 2.toFloatExact() for a Float result. text.fixed(value, decimals) from @std/text.spr prints a Float with that many decimals.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

## Related chapters

- [Chapter 3: Values, variables and arithmetic](/en/tutorial/ch03-values): `let`/`var`, the number families, `Bool`.
- [Chapter 4: Text](/en/tutorial/ch04-text): `+` joining and string methods.
- [Chapter 5: Making decisions: if](/en/tutorial/ch05-if): conditions and if expressions.
- [Chapter 13: Values that may be missing](/en/tutorial/ch13-nullable): `== null` and narrowing.
- [Chapter 15: Functions as values](/en/tutorial/ch15-functions-as-values): `=>` and function types.
- [Appendix B: Error codes and where to read about them](/en/tutorial/appendix-b-error-codes): the error codes on this page.
