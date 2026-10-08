# Appendix C. Keywords and built-in functions

Keywords are words the language reserves; you cannot use them as variable or function names. Built-in functions and built-in methods need no import and work in any Sprig program. The lists here agree with the compiler's own help; you can check them with `sprig help language`, `sprig help types`, `sprig help numerics`, `sprig help strings` and `sprig help collections`.

## C.1 Keywords

Sprig's 36 keywords:

| Keyword | Purpose | First appears |
|---|---|---|
| `and` | logical and, short-circuiting | Chapter 3 |
| `as` | import alias, match binding, `conform ... as parentView` | Chapters 12, 16, 18 |
| `break` | leave a loop | Chapter 6 |
| `case` | one branch of a match | Chapter 12 |
| `catch` | catch an error | Chapter 14 |
| `class` | declare a class | Chapter 11 |
| `conform` | declare that a class satisfies a contract | Chapter 16 |
| `continue` | skip to the next iteration of a loop | Chapter 6 |
| `elif` | the next arm of a condition chain | Chapter 5 |
| `else` | the fallback arm of a condition chain | Chapter 5 |
| `enum` | declare an enum | Chapter 12 |
| `false` | `Bool` literal: false | Chapter 3 |
| `finally` | a block that always runs when a try ends | Chapter 14 |
| `fn` | function type, lambda | Chapter 15 |
| `for` | iterate | Chapter 6 |
| `func` | declare a function or method | Chapter 7 |
| `generic` | a generic parameter block | Chapter 16 |
| `if` | condition statement and condition expression | Chapter 5 |
| `import` | import a module or Java class | Chapters 4, 18 |
| `in` | the iterated collection; the membership operator | Chapters 4, 6 |
| `let` | a name that binds once | Chapter 3 |
| `match` | match an enum or variant | Chapter 12 |
| `not` | logical not | Chapter 3 |
| `null` | the null literal, which belongs only to `T?` | Chapter 13 |
| `or` | logical or, short-circuiting | Chapter 3 |
| `pass` | a statement that does nothing | Chapter 6 |
| `requires` | a capability constraint on a generic function | Chapter 16 |
| `return` | return from a function | Chapter 7 |
| `rethrows` | the function throws only what its function arguments throw | Chapter 15 |
| `throw` | throw an error | Chapter 14 |
| `throws` | declare the errors a function may throw | Chapter 14 |
| `true` | `Bool` literal: true | Chapter 3 |
| `try` | a block that may throw | Chapter 14 |
| `var` | a name that can be reassigned | Chapter 3 |
| `variant` | declare a variant (a closed set of types) | Chapter 12 |
| `while` | a while loop | Chapter 6 |

Two words that look like keywords are not: `export` and `to` (in `conform A to B`) are "contextual words" with a special meaning only in that position, and they can be used as ordinary names:

<<< @/snippets/book_en/appendix_c_contextual.spr

```text
7
```

Type names (`Int`, `String`, `List`, ...) are not keywords either; they are just names declared ahead of time.

## C.2 Built-in functions

There are only three:

| Function | What it does | Notes |
|---|---|---|
| `print(value)` | prints one value and ends the line | takes exactly one argument; `print(a, b)` is `SPR-CALL-ARITY`; a `null` can be printed too |
| `range(stop)`, `range(start, stop)`, `range(start, stop, step)` | returns a `List[Int]` | `stop` is not included; `step` may be negative; written directly after `for`, it counts without building the list |
| `assert(condition)`, `assert(condition, message)` | throws an `Error` when the condition is `false` | useful in tests and when hunting bugs; without `message` the text is `assertion failed` |

<<< @/snippets/book_en/appendix_c_builtins.spr

```text
hello
[0, 1, 2]
[1, 2, 3]
[0, 5]
assert passed
```

- `range(3)` is `[0, 1, 2]` and `range(1, 4)` is `[1, 2, 3]`: the stop value is not included.
- `range(0, 10, 5)` adds 5 each step and gives `[0, 5]`; a negative `step` counts down.
- `assert(2 > 1, ...)` passes and the program continues.

## C.3 Built-in methods

Methods are called with `.`. Here is the full list; parentheses hold the number of parameters or a note.

**Number types**

| Type | Methods |
|---|---|
| `Int` | `toFloatExact()`, `toFloatLossy()`, `toInt32Exact()`, `toDecimal()`, `divTrunc(b)`, `compareTo(b)`, `toString()` |
| `Int` (static) | `Int.abs(n)`, `Int.min(a, b)`, `Int.max(a, b)` |
| `Int32` | `toInt()`, `toFloat()`, `toDecimal()`, `divTrunc(b)`, `compareTo(b)`, `toString()` |
| `Float` | `toIntExact()` (must be whole), `toIntTrunc()`, `toFloat32Exact()`, `toFloat32Lossy()`, `isNaN()`, `isInfinite()`, `isFinite()`, `approxEqual(other, tolerance)`, `compareTo(b)`, `toString()` |
| `Float` (static) | `Float.sqrt(x)`, `Float.floor(x)`, `Float.ceil(x)`, `Float.abs(x)` |
| `Float32` | `toFloat()`, `isNaN()`, `isInfinite()`, `isFinite()`, `compareTo(b)`, `toString()` |
| `Decimal` | `divide(divisor, decimals, roundingMode)`, `toIntExact()`, `toFloatExact()`, `toFloatLossy()`, `toJava()`, `compareTo(b)`, `toString()` |
| `Decimal` (static) | `Decimal.parse(text)`, `Decimal.fromJava(bigDecimal)` |
| `BigInt` | `divTrunc(b)`, `toIntExact()`, `toFloatExact()`, `toFloatLossy()`, `toDecimal()`, `toJava()`, `compareTo(b)`, `toString()` |
| `BigInt` (static) | `BigInt.parse(text)`, `BigInt.fromInt(n)`, `BigInt.fromJava(bigInteger)` |
| `Bool` | `toString()` |

`compareTo` returns an `Int32`: negative, zero or positive, in the same order as `<`; `a.compareTo(b)` is exactly Java's `Comparator`.

**`String`**

| Type | Methods |
|---|---|
| `String` | `length()`, `isEmpty()`, `charAt(i)`, `codeAt(i)`, `substring(start)`, `substring(start, end)`, `indexOf(text)`, `lastIndexOf(text)`, `contains(text)`, `startsWith(text)`, `endsWith(text)`, `compareTo(other)`, `toUpperCase()`, `toLowerCase()`, `trim()`, `split(separator)`, `replace(old, new)`, `repeat(n)`, `toInt()`, `toIntOrNull()`, `toFloat()`, `toString()` |
| `String` (static) | `String.join(parts, separator)`, `String.fromCode(codePoint)` |

- Positions count Unicode code points, not UTF-16 code units (Chapter 4).
- The `end` of `substring(start, end)` is not included.
- `toInt()` fails with an error when the text is not an integer; `toIntOrNull()` returns `Int?`.
- `String.join` takes the list first and the separator second: `String.join(["a", "b"], ",")`.

**Collections**

| Type | Methods |
|---|---|
| `List[T]` | `size()`, `isEmpty()`, `get(i)`, `contains(v)`, `indexOf(v)`, `toMutableList()`, `toList()`, `map(f)`, `filter(f)`, `forEach(f)`, `toString()` |
| `MutableList[T]` | all `List` methods, plus `append(v)`, `set(i, v)`, `insert(i, v)`, `removeAt(i)`, `remove(v)`, `clear()`, `sort()` |
| `Map[K, V]` | `size()`, `isEmpty()`, `get(k)` (returns `V?`), `containsKey(k)`, `keys()`, `values()`, `toMutableMap()`, `toMap()`, `toString()` |
| `MutableMap[K, V]` | all `Map` methods, plus `set(k, v)`, `remove(k)`, `clear()` |

`List` and `Map` are read-only; to change one, convert it first with `toMutableList()`/`toMutableMap()` (Chapters 8 and 9).

A run through a few of each:

<<< @/snippets/book_en/appendix_c_methods.spr

```text
7
3
3.0
7.0
2
2
Sprig
SPRIG
[a, b, c]
abab
x,y
4
null
[1, 2, 3, 4]
true
3
36
true
[ada]
```

- `Int.abs(-7)` is 7 and `Int.min(3, 9)` is 3: static methods are called on the type name.
- `Float.sqrt(9.0)` is `3.0`; `7.toFloatExact()` is `7.0`; `2.5.toIntTrunc()` is 2; `7.divTrunc(3)` is 2.
- `trim()` strips the whitespace at both ends and `toUpperCase()` uppercases; `split("-")` returns a list; `repeat` repeats a string; `String.join` builds `x,y`.
- `"3".toInt() + 1` is 4; `"x".toIntOrNull()` prints `null`.
- `xs.append(4)` and then `xs.sort()` give `[1, 2, 3, 4]`; `contains` and `indexOf` look up by value.
- `ages.get("ada")` is 36; `"ada" in ages` tests keys; `keys()` returns `[ada]`.

The `@std` modules of the standard library hold many more functions (list helpers, text, files, JSON, ...). Those need an `import` and are not "built in" in the sense of this appendix. To see a module's full signatures, run `sprig api @std/<module>.spr`.

## Related chapters

- [Chapter 3: Values, variables and arithmetic](/en/tutorial/ch03-values): `let`/`var`, `Bool`, numeric operations.
- [Chapter 4: Text](/en/tutorial/ch04-text): string methods.
- [Chapter 5: Making decisions: if](/en/tutorial/ch05-if) and [Chapter 6: Repeating: loops](/en/tutorial/ch06-loops): `if`/`elif`/`else`, `while`/`for`/`in`.
- [Chapter 7: Functions](/en/tutorial/ch07-functions): `func`, `return`.
- [Chapter 8: Lists](/en/tutorial/ch08-lists) and [Chapter 9: Maps and sets](/en/tutorial/ch09-maps-sets): collection methods.
- [Chapter 12: Enums, variants and match](/en/tutorial/ch12-enums-variants): `enum`, `variant`, `match`, `case`.
- [Chapter 14: Handling errors](/en/tutorial/ch14-errors): `try`/`catch`/`finally`, `throw`/`throws`.
- [Chapter 15: Functions as values](/en/tutorial/ch15-functions-as-values): `fn`, `rethrows`.
- [Chapter 16: Generics and contract classes](/en/tutorial/ch16-generics-contracts): `generic`, `requires`, `conform`.
