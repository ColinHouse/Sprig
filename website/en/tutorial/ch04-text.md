# 4. Text

In this chapter you will:

- work with strings and join them together;
- use escapes: `\n`, `\t`, `\"`, `\\`;
- call the common string methods;
- count "characters" the Sprig way (code points);
- convert between text and numbers;
- make your first `import`, from the `@std/text.spr` standard module.

## 4.1 Strings

Text between double quotes is a **string**, of type `String`. It can hold letters, digits, spaces, Chinese characters, emoji, any Unicode text. A first look:

<<< @/snippets/book_en/ch02_strings.spr

```text
tea x 3
length 3
TEA t
[a, b, c]
padded
2
😀
```

Line by line:

- `item + " x " + count`: when either side of `+` is a `String`, `+` **joins**; the other value automatically becomes text. So `3` goes straight into the string with no manual conversion.
- `"length " + item.length()`: `length()` is a string method returning 3, which is then joined in.
- `item.toUpperCase()` turns `tea` into `TEA`; `item.substring(0, 1)` takes index 0 up to index 1 (**not including 1**) and gives `"t"`.
- `"a,b,c".split(",")` cuts at the commas and prints `[a, b, c]`. That is a **list** (a `List`), the subject of chapter 8; for now, know that a string can be split into parts.
- `"  padded  ".trim()` removes the spaces at both ends.
- `"é😀".length()` is 2: é and 😀 are one "character" each. `[1]` gets the second one, 😀. The unit is explained in 4.5.

Joining with `+` evaluates left to right, and mixing numbers has a small trap; see 4.3.

## 4.2 Escapes: special characters inside strings

Some characters can't be typed directly inside a string: a newline, a tab, a double quote itself, a backslash. A **backslash plus a letter** stands for them:

<<< @/snippets/book/ch04_escapes.spr

```text
first line
second line
tab	between
a "quote" inside
a backslash: \
```

| Escape | Meaning |
|---|---|
| `\n` | newline |
| `\t` | tab |
| `\"` | a double quote (otherwise it would end the string early) |
| `\\` | a backslash |

Note the first line: `print("first line\nsecond line")` calls `print` once, but the output takes two lines because of the `\n`.

That is the whole escape set: **no `\u` Unicode escapes**, and **no multi-line strings**. A string must be closed with a double quote on the same line. For several lines, join pieces with `\n`.

## 4.3 Joining text, and its trap

Joining is simple by itself:

<<< @/snippets/book/ch04_concat.spr

```text
Hello, Ada
cups: 3
3 then 12
```

- `"Hello, " + name` gives `Hello, Ada`.
- `"cups: " + cups` puts the number `3` into the text, giving `cups: 3`. That's the "other value becomes text" rule in action.
- `1 + 2 + " then " + 1 + 2` prints `3 then 12`, not `1 2 then 1 2` and not `3 then 3`. Why? `+` works left to right:

| Step | Expression | Result |
|---|---|---|
| 1 | `1 + 2` | `3` (two Ints, addition) |
| 2 | `3 + " then "` | `"3 then "` (a String appears, so this is joining) |
| 3 | `"3 then " + 1` | `"3 then 1"` |
| 4 | `"3 then 1" + 2` | `"3 then 12"` (still joining) |

For `3 then 3`, add the last two numbers in parentheses first: `1 + 2 + " then " + (1 + 2)`. **There is no string interpolation**: no `f"..."`, no `${...}`; joining is the only way.

## 4.4 The common methods

Here is the family portrait:

<<< @/snippets/book/ch04_methods.spr

```text
false
true
e
101
7
10
true
true
Hello, worldHello, world
Hello, Sprig
43
5.0
7!
```

Match them up:

| Expression | Result | Meaning |
|---|---|---|
| `s.isEmpty()` | `false` | is it empty? `"".isEmpty()` is `true` |
| `s.charAt(1)` | `e` | the character at index 1 (still a `String`) |
| `s.codeAt(1)` | `101` | the code point at index 1 (`e` is 101) |
| `s.indexOf("world")` | `7` | first index of the substring, or -1 |
| `s.lastIndexOf("l")` | `10` | last index of the substring |
| `s.endsWith("world")` | `true` | does it end with that? |
| `s.contains("lo, w")` | `true` | does it contain that? |
| `s.repeat(2)` | `Hello, worldHello, world` | repeat |
| `s.replace("world", "Sprig")` | `Hello, Sprig` | replace |
| `"  42  ".trim().toInt() + 1` | `43` | trim, convert to integer, add 1 |
| `"2.5".toFloat() * 2.0` | `5.0` | convert to decimal, multiply |
| `7.toString() + "!"` | `7!` | any value becomes text with `toString()` (which is what happens automatically when you join a number into a string) |

Two more are **static**: written after the type `String` instead of after a value:

- `String.join(["a", "b", "c"], "-")` gives `a-b-c`: join a group of strings with a separator.
- `String.fromCode(65)` gives `A`: turn a code point into a character.

### Deliberate mistake: a misspelled method

<<< @/snippets/book/ch04_method_typo.spr

```text
SPR-NAME-UNRESOLVED [NAME] main.spr:1:7: Type String has no method 'toUppercase'
  hint: String methods: length, isEmpty, charAt, codeAt, substring, indexOf, contains, startsWith, endsWith, compareTo, toUpperCase, toLowerCase, trim, split, replace, repeat, lastIndexOf, toInt, toIntOrNull, toFloat, toString.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

The mistake is the capitalization: it's `toUpperCase()`, with a capital U and C. The hint kindly lists every string method, so finding the right spelling is easy. Names are case-sensitive throughout Sprig.

::: tip Coming from another language?
**Methods use camelCase** (`toUpperCase`, `lastIndexOf`) — the one place Sprig uses it. Names you declare in a function use lower_snake_case (`read_line`); types and enum cases use UpperCamelCase (`String`, `Decimal`); only built-in methods and Java members use camelCase. Also, Sprig has no `Char` type: "characters" from `charAt`, indexing or iteration are one-code-point `String`s.
:::

## 4.5 A "character" is a code point

`"é😀".length()` is 2, not 3, because `length`, indexing, `charAt`, `codeAt`, `substring` and `indexOf` all count **Unicode code points**, not bytes and not Java's UTF-16 units. A code point is the number of a character: `A` is 65, 😀 is 128512, and so on.

<<< @/snippets/book/ch04_codepoints.spr

```text
3
😀
😀
128512
😀
2
A
```

- `"A😀東".length()` is 3: A, 😀 and 東 are one code point each.
- `[1]` and `charAt(1)` both get 😀. `substring(1, 2)` takes index 1 up to 2 (not including 2), still 😀.
- `codeAt(1)` gives 128512, the code point of 😀.
- `indexOf("東")` is 2: counting code points, 東 is the third character (index 2).
- `String.fromCode(65)` turns code point 65 back into `A`.

This is not the same as "human characters". Accented letters written as a letter plus a combining mark, and emoji families such as 👨‍👩‍👧, consist of several code points; Sprig does not split by visual glyph.

## 4.6 Finding text: `in`

`in` is an operator that asks whether one piece of text occurs inside another. The result is a `Bool`:

<<< @/snippets/book/ch04_contains.spr

```text
true
false
```

`"ell" in "hello"` is `true`; `"z" in "hello"` is `false`. It means the same as `s.contains("ell")`; pick either (and `in` will come back for lists and maps).

## 4.7 Converting between text and numbers

Digits in a string and actual numbers are different things. To compute, convert first:

<<< @/snippets/book/ch04_convert.spr

```text
50
12
null
3.0
```

- `"42".toInt()` gives the integer 42; adding 8 gives 50. `toInt()` requires the whole text to be an integer; something like `"abc"` stops the program at run time with `SPR-RUNTIME-ERROR`, whose message is `Uncaught Error: not an integer: "abc"`.
- `"12".toIntOrNull()` gives 12; `"abc".toIntOrNull()` gives `null` (no value at all). It doesn't fail; it uses `null` to say "couldn't convert". Its type is `Int?` — an `Int` that may have no value, the subject of chapter 13.
- `"2.5".toFloat()` gives 2.5, and adding 0.5 gives 3.0.

`"abc".toIntOrNull()` showing `null` is the one type puzzle you can park for now: remember that `toIntOrNull` gives you a value if it can, and `null` if it can't.

## 4.8 First `import`: the standard library

Sprig ships with standard modules. Use `import` to bring one into your file under a short name, then call it through that name:

<<< @/snippets/book/ch02_text.spr

```text
0.67
007
ab..|
a-b-c
```

- `import "@std/text.spr" as text` imports the standard `text` module under the alias `text`. `import` goes at the very top of the file, before every declaration.
- `text.fixed(2.0 / 3.0, 2)` rounds a `Float` to a chosen number of decimals and returns text: `0.67`.
- `text.pad_left("7", 3, "0")` pads on the left to width 3 with `"0"`: `007`.
- `text.pad_right("ab", 4, ".")` pads on the right: `ab..`, with a `|` joined on so you can see the edge.
- `text.join(["a", "b", "c"], "-")` joins a group of strings with `-`: `a-b-c`.

`@std/text.spr` also has `lines`, `starts_with`, `ends_with`, `strip_prefix`, `strip_suffix`, `is_ascii_digit`, `is_ascii_letter`, `escape_html`, `trim`, `split` and more. To see all of them, run:

```bash
sprig api @std/text.spr
```

It lists every function with its signature and a sentence of documentation. No rush: get comfortable with the common few first.

## Summary

- A string is Unicode text between double quotes; `+` joins when either side is a string, turning the other value into text automatically.
- Joining runs left to right: `1 + 2 + " then " + 1 + 2` is `3 then 12`; there is no string interpolation.
- Escapes are `\n`, `\t`, `\"`, `\\` and friends; no multi-line strings.
- Common methods: `length`, `isEmpty`, `charAt`, `codeAt`, `substring` (end excluded), `indexOf`, `lastIndexOf`, `contains`, `startsWith`, `endsWith`, `toUpperCase`, `toLowerCase`, `trim`, `split`, `replace`, `repeat`, `toString`.
- Static forms: `String.join(list, sep)` and `String.fromCode(code)`.
- Length and indexing count Unicode code points; a "character" is a one-code-point `String`, and there is no `Char` type.
- `"a" in "abc"` tests for a substring; text-to-number uses `toInt`, `toIntOrNull` and `toFloat`.
- `import "@std/text.spr" as text` brings in the standard library.

## Exercises

1. Store a name in a variable, print a greeting, and use `\n` to print a second line.
   Hint: `"Hello, " + name + "!"`; put `\n` inside the string.

::: details Answer
<<< @/snippets/book/ch04_ex_greeting.spr

```text
Hello, Ada!
Line one
Line two
```
:::

2. Split `"red,green,blue"` at the commas and print the result.
   Hint: use `.split(",")`.

::: details Answer
<<< @/snippets/book/ch04_ex_split.spr

```text
[red, green, blue]
```

The `[...]` is a list, covered in chapter 8.
:::

3. Turn the text `"101"` into an integer and print it plus 1; then convert `"nope"` with the version that doesn't fail.
   Hint: the failing one is `toInt()`; the safe one is `toIntOrNull()`.

::: details Answer
<<< @/snippets/book/ch04_ex_convert.spr

```text
102
null
```
:::

4. Check whether `"cat"` and `"dog"` occur in `"concatenate"`.
   Hint: use the `in` operator.

::: details Answer
<<< @/snippets/book/ch04_ex_in.spr

```text
true
false
```
:::

5. Take `"Sprig"` out of `"I love Sprig!"` with `substring` (remember the end is excluded).
   Hint: indexes start at 0, and `"I love "` is 7 code points.

::: details Answer
<<< @/snippets/book/ch04_ex_substring.spr

```text
Sprig
```

`I love ` is 7 characters, so take from index 7 up to 12.
:::

Next chapter starts making decisions: [Chapter 5: Making decisions: if](/en/tutorial/ch05-if).
