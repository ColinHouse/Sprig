# Appendix D. Coming from Python, JavaScript or Java

This appendix does not reteach the language. It maps the habits you already have onto Sprig, one line at a time: on the left is what you are used to, on the right is what Sprig writes. Every Sprig fragment has run on the compiler; each section ends with a complete program that brings the most common differences together.

If you can already program, you can skim Chapters 1–4, but read Chapter 3 (numbers), Chapter 13 (values that may be missing) and Chapter 14 (errors) carefully — those are where Sprig differs from most languages.

## D.1 Coming from Python

| Python habit | How Sprig writes it |
|---|---|
| `print(a, b)` | `print` takes one value: `print(a)`; build the text first |
| `1 / 2` gives `0.5` | `Int` `/` is rejected: `7.divTrunc(2)` or `7.toFloatExact() / 2.toFloatExact()` |
| `//` floor division | `a.divTrunc(b)` |
| `-7 % 3` gives `2` | `-7 % 3` gives `-1`: the sign of `%` follows the dividend (as in Java) |
| `if x:` truthiness | the condition must be `Bool`: `if x != 0:`, `if text != "":` |
| `len(s)`, `len(xs)` | `s.length()` (`String`), `xs.size()` (collections) |
| f-string `f"n = {n}"` | `"n = " + n`; `+` turns the right side into text |
| `None` | `null`, which belongs only to `T?`; check `!= null` before using it |
| `try`/`except` | errors go into the signature: `throws Error` plus `try`/`catch problem: Error:` |
| lists are mutable by default | `List` is read-only and `MutableList` is mutable; `xs[0] = v` becomes `xs.set(0, v)` |
| `for i in range(n)` | the same: `for i in range(n):` |
| `pass` | the same: `pass` |
| tuples `(a, b)` | no tuples: a one-line class `class Pair(first: Int, second: Int)` |
| `and`, `or`, `not` | the same |
| `d.get(k)` returns `None` | `m.get(k)` returns `V?`; `m[k]` is `V?` too, but `m[k] += 1` requires the key to exist |

<<< @/snippets/book_en/appendix_d_python.spr

```text
total: 7.5
found nut
[a, b, c]
3
-1
3.5
```

Line by line:

- `"total: " + total` joins text and a `Float`, with no f-string and no `str()`.
- `find` returns `String?`; after `if found != null:`, `found` is a non-null string inside the branch.
- `items` must be declared `MutableList[String]` before it can `append`; with `List[String]`, `append` would be rejected.
- `7.divTrunc(2)` is deliberate truncation; `-7 % 3` is `-1` because the sign follows the dividend.
- `7.toFloatExact() / 2.toFloatExact()` is the fractional division, and gives `3.5`.

## D.2 Coming from JavaScript

| JavaScript habit | How Sprig writes it |
|---|---|
| `const` / `let` | `let` binds once and `var` can be reassigned (the opposite of JS) |
| `===` / `!==` | `==` / `!=`, which are strict already: there are no implicit conversions |
| `undefined` | does not exist; the only absence is `null`, and only inside `T?` |
| template strings `` `n = ${n}` `` | `"n = " + n` |
| `array.length` | `xs.size()` |
| `array.push(v)` | `xs.append(v)` |
| `array.map(f)` | `xs.map(f)`; `f` is a typed lambda or a named function |
| `{}` object literals | `{"k": v}` is a `Map`; for fixed fields use a one-line class |
| `x?.y`, `x ?? y` | no optional chaining and no `??`: write `if x != null:` first, or use `or_else` from `@std/nulls` |
| `async`/`await`, Promises | virtual threads and tasks from `@std/concurrent` (Chapter 22) |
| `function f(x) {}` | `func f(x: Int) -> Int:`, with a type on every parameter and the result |
| `Math.floor(x)` | `Float.floor(x)`; also `sqrt`, `ceil` and `abs` |
| `parseInt(s)` | `s.toIntOrNull()` (returns `null` when missing) or `s.toInt()` (throws on failure) |
| `NaN === NaN` is `false` | test with `Float.isNaN(x)`; for approximate equality use `x.approxEqual(y, tolerance)` |
| `for (const x of xs)` | `for x in xs:` |
| every number is a double | `Int` is a 64-bit integer and `Float` is binary64; the two never mix implicitly |

<<< @/snippets/book_en/appendix_d_javascript.spr

```text
Ada has 1
missing
3
3
true
Grace
true
```

Line by line:

- `var count` can take `count += 1`; assigning to `let name` again would report `SPR-NAME-LET-ASSIGN`.
- `lookup` returns `Int?` and `{"ada": 36}` is a `Map[String, Int]`; when nothing is found, `get` returns `null`.
- `numbers.size()` is 3 and `numbers[0]` is 3; the array method is `size()`, not `.length`.
- `person.get("name")` reads a value out of the map, and `"city" in person` tests keys.

## D.3 Coming from Java

| Java habit | How Sprig writes it |
|---|---|
| `public static void main(String[] args)` | top-level statements are the program and run in source order; a function named `main` is not called automatically |
| `System.out.println(x)` | `print(x)` |
| `final` local variables | `let`; for a local that can be reassigned, `var` |
| `int` overflow wraps around | `Int` checks overflow, and overflow is a runtime error |
| integer `/` truncates | `/` is rejected: deliberate truncation is `a.divTrunc(b)` |
| `int` is 32-bit, `long` is 64-bit | `Int` is 64-bit; write `Int32` for 32 bits |
| `double`, `float` | `Float` (binary64), `Float32` |
| `a.equals(b)` | `==` already compares by value (numbers, `Bool`, `String`, lists, maps, enums, variants) |
| `==` on class objects compares references | the same: identity; equal fields do not make two objects equal |
| interfaces | contract classes (methods without bodies) plus `conform` (Chapter 16) |
| `extends` | no inheritance; use contracts, composition or variants |
| generics `<T>` | a `generic T:` block (Chapter 16) |
| checked exceptions | recoverable errors are `throws Error` or an error class you define; checked Java exceptions are checked too |
| a Java method may return `null` | in Sprig that result is `T?`; you must check it before use |
| annotations, reflection | no annotations and no reflection-derived schemas; write explicit fields and functions |
| `int[]` arrays | `List[Int]`; a Java array crosses as an opaque value (Appendix F) |

<<< @/snippets/book_en/appendix_d_java.spr

```text
Ada: 100
through the contract
3
```

Line by line:

- `Account(owner="Ada")` constructs the object with a named argument; `deposit` changes the `var balance`.
- `Sink`'s method has no body, so `Sink` is a contract class; after `conform Console to Sink`, a `Console` value can be used as a `Sink`.
- `7.divTrunc(2)` is 3: integer division must state its "truncate" intent.
- Java's `main` is not called automatically, so the whole program above is top-level statements from beginning to end.

## Related chapters

- [Chapter 3: Values, variables and arithmetic](/en/tutorial/ch03-values): the number families and explicit conversions.
- [Chapter 4: Text](/en/tutorial/ch04-text): string methods and code points.
- [Chapter 8: Lists](/en/tutorial/ch08-lists) and [Chapter 9: Maps and sets](/en/tutorial/ch09-maps-sets): `List`/`MutableList`, `Map`.
- [Chapter 11: Classes and objects](/en/tutorial/ch11-classes): one-line classes, fields, methods.
- [Chapter 13: Values that may be missing](/en/tutorial/ch13-nullable): `T?` and narrowing.
- [Chapter 14: Handling errors](/en/tutorial/ch14-errors): `throws`, `try`/`catch`.
- [Chapter 16: Generics and contract classes](/en/tutorial/ch16-generics-contracts): `generic`, contracts and `conform`.
- [Chapter 22: Concurrency](/en/tutorial/ch22-concurrency): `@std/concurrent` in place of async/await.
