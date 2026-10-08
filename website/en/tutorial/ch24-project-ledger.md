# 24. Project: an expense tracker

The last chapter teaches no new syntax. It puts the previous 23 chapters together into one complete little program: read a list of expenses, total them by category, and print the grand total. You will see why each part is written the way it is, plus two real compiler errors and one runtime error message.

In this chapter you will learn to:

- break a small requirement into a `variant`, a `class` and a set of functions;
- why amounts are stored as whole cents instead of decimals;
- use `try` / `finally` so the program deletes only the file it created;
- the whole flow: read a file, parse each line, total by category;
- read the two deliberate mistakes.

## 24.1 What to build

The input is text, one expense per line, three comma-separated fields: item, amount, category.

```text
Coffee, 12.50, food
Taxi, 36.00, transport
Noodles, 18.00, food
Movie, 45.00, fun
```

The program prints each category's total and the grand total. There are only three categories: `food`, `transport` and "other"; `fun` is neither `food` nor `transport`, so it lands in "other".

## 24.2 The complete program

The program is below. It creates and writes a temporary file first (so you can run it without preparing data), then deletes that file after reading it.

<<< @/snippets/book_en/ch24_ledger.spr

```text
Food: 30.50
Transport: 36.00
Other: 45.00
Total: 111.50
```

Look at the output first:

- `Food: 30.50`: `Coffee`'s 12.50 plus `Noodles`' 18.00 is 30.50.
- `Transport: 36.00`: just the one `Taxi` line.
- `Other: 45.00`: `fun` is not a known category, so it falls to `Other`.
- `Total: 111.50`: all four expenses added up.

The sections below follow the program in order.

## 24.3 Categories: variant and match

A category is "one of a fixed few, each carrying no data (`Other` is just a name)". That is exactly chapter 12's `variant`:

```sprig
variant Category:
    Food
    Transport
    Other
```

`category_of` turns a text name into a `Category`, returning `Category.Other` for anything unknown. `label` goes the other way, turning a `Category` into the name shown in the report; it uses `match`, with all three branches present—`match` has no fallback, the compiler requires every case (chapter 12).

## 24.4 One expense: a class

```sprig
class Expense:
    let item: String
    let cents: Int
    let category: Category
```

All three fields are `let`: once created, an expense should not change (chapter 11). Construction always names the fields: `Expense(item=item, cents=cents, category=category)`.

`cents` is an `Int`, not a `Float`. Chapter 3 already showed why: `0.1 + 0.2` prints `0.30000000000000004`, and money in decimals drifts. So 12.50 yuan is stored as `1250` cents and converted back for printing.

## 24.5 The two money functions

`parse_amount("12.50")` turns text into `1250` cents:

```sprig
let parts = text.split(".")
```

`split(".")` splits on the decimal point into `["12", "50"]`, `parts.get(0)` is `"12"`, and `toIntOrNull()` turns it into `12` or `null` if it cannot (chapters 4 and 13). The units part must exist and there can be at most one decimal point; the fraction must be exactly two digits, or the function throws:

```sprig
if cents == null or parts.get(1).length() != 2:
    throw Error("not an amount: " + text)
```

`throws Error` is in the signature, so the caller must catch it or declare it too (chapter 14).

`format_money` goes the other way:

```sprig
let units = cents.divTrunc(100)
let rest = cents % 100
```

`divTrunc` is "integer division that drops the remainder" (chapter 17): 1250 divided by 100 is 12; `%` gives the remainder 50. When `rest < 10` a zero must be added: 50 cents is `".50"`, 5 cents is `".05"`.

## 24.6 A line of text becomes one expense

`parse_line` takes `"Coffee, 12.50, food"` apart:

- `line.split(",")` gives the three fields; not three means an error.
- `strings.trim(...)` strips spaces around each field (`@std/text`, chapter 20).
- `parse_amount` parses the amount; `category_of` parses the category.
- Finally an `Expense` is created and returned.

`load_file` reads the file into lines:

```sprig
for line in strings.lines(files.read_utf8(path)):
    if strings.trim(line) != "":
        expenses.append(parse_line(line))
```

`strings.lines` splits on line endings; a final line ending leaves one extra empty string, so `trim` filters empty lines out. Each parsed line is `append`ed to a `MutableList[Expense]` (chapter 8).

## 24.7 Totalling: the map counting pattern

`report` uses a `MutableMap[String, Int]` to hold each category's total (chapter 9):

```sprig
let previous = totals[name]
if previous == null:
    totals[name] = expense.cents
else:
    totals[name] = previous + expense.cents
sum += expense.cents
```

Reading a category that has not been recorded yet gives `null`, so the code reads first, checks, and then either creates or adds. You cannot write `totals[name] += expense.cents` here: with a missing key that is a runtime error.

Printing walks the fixed order `["Food", "Transport", "Other"]`; a category with no expenses prints nothing.

## 24.8 main: read, clean up, delete only what you created

`main` has four steps, and the first one and the cleanup come as a pair:

```sprig
let path = files.temp_file()
files.write_utf8(path, "Coffee, 12.50, food\n...")
try:
    report(load_file(path))
finally:
    files.remove_file(path)
```

- `files.temp_file()` creates a new file in the system temporary directory and returns its path (`@std/files`, chapter 20).
- `files.write_utf8(path, ...)` writes the sample data. `\n` is a newline (chapter 4).
- `files.read_utf8(path)` reads it back and `load_file` parses it.
- The `files.remove_file(path)` in the `finally` block **deletes exactly the temp file created by line 1**; `finally` guarantees cleanup even when parsing fails (chapter 14).

The program ends with a `try` / `catch` around `main`, printing the message if it throws an `Error` (chapter 14).

::: details To read your own expense file, replace main
Do not just change `path`: the program above would first write the sample data over your file, and the `finally` would delete it afterwards. The safe way is to make `main` read only, with no write and no delete:

```sprig
func main() -> Unit throws Error:
    report(load_file("my-expenses.txt"))
```

That is, drop all three lines: `temp_file()`, `write_utf8` and `remove_file`. Only a file the program created itself is the program's to delete.
:::

## 24.9 Deliberate mistake: integers cannot be divided with /

Replace `cents.divTrunc(100)` in `format_money` with `cents / 100`:

<<< @/snippets/book/ch24_divide.spr

```text
SPR-NUM-DIVISION [TYPE] main.spr:2:7: Integer / would truncate; use a.divTrunc(b), or convert both operands explicitly (expected explicit division, actual Int / Int)
  hint: Write cents.divTrunc(100) to drop the remainder on purpose, or cents.toFloatExact() / 100.toFloatExact() for a Float result. text.fixed(value, decimals) from @std/text.spr prints a Float with that many decimals.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

The error is at line 2, column 7, pointing at the `/`. The message lays out both choices: `divTrunc` when you mean to drop the remainder, or convert both sides to `Float` when you want a decimal. Sprig will not guess whether 7 / 2 should be 3 or 3.5.

## 24.10 Deliberate mistake: a missing match case

If `label` only had `Food` and `Transport` branches:

<<< @/snippets/book/ch24_nonexhaustive.spr

```text
SPR-MATCH-NONEXHAUSTIVE [FLOW] main.spr:7:12: Missing case: Category.Other
  hint: Add 'case Category.Other:' (there is no default case)
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

The compiler says at line 7, column 12 (where `match` is) which case is missing, and the hint gives the exact line to add. When you add a new case to `Category` later, every `match` that forgot to handle it is found like this.

## 24.11 Runtime failure: a bad amount

Static checking catches type errors, not "the text is shaped wrong". Write the amount in the `Movie` line as `45.5` and the program throws while parsing that line, caught by the outermost `catch`:

<<< @/snippets/book_en/ch24_bad_parse.spr

```text
not an amount: 45.5
```

`parse_amount` insists on exactly two digits after the decimal point, `45.5` has one, so `throw Error("not an amount: " + text)` runs. The message carries the offending original text, which is far more useful than a silent default.

## Summary

- `variant` describes a fixed set of categories; `match` forces every case to be handled.
- One record is a `class` with `let` fields; amounts are whole cents.
- `divTrunc` and `%` do the cents conversion; `throws Error` puts failure in the signature.
- The map totalling pattern is: read, check for `null`, then add.
- A program deletes only the files it created; `try` / `finally` guarantees cleanup. To read your own file, replace the whole `main` with a read-only version.
- For a static error read the code, the position and the hint; runtime error messages are yours to design well.

## Exercises

**Exercise 1 (easy).** What does `format_money` print for 5 cents and 0 cents? Write a small program to check. Hint: remember the zero padding when `rest < 10`.

::: details Answer
<<< @/snippets/book_en/ch24_ex_money.spr

```text
0.05
0.00
```

5 cents is `0.05` yuan, 0 cents is `0.00`. `divTrunc(100)` gives 0, `%` gives 5, padded to `".05"`.
:::

**Exercise 2 (easy).** What do `category_of("fun")` and `category_of("transport")` print after passing through `label`?

::: details Answer
<<< @/snippets/book_en/ch24_ex_label.spr

```text
Other
Transport
```

`category_of` returns `Category.Other` for the unknown `"fun"`.
:::

**Exercise 3 (medium).** Add one line to `report` so the report prints `Records: 4` (the number of records) before the total. Hint: `expenses.size()` is an `Int`; when concatenating with `+`, the other side must be a `String`.

::: details Answer
<<< @/snippets/book_en/ch24_ex_count.spr

```text
Food: 30.50
Transport: 36.00
Other: 45.00
Records: 4
Total: 111.50
```

The added line is `print("Records: " + expenses.size())`, placed after the category totals and before the grand total.
:::

**Exercise 4 (harder).** Change the program to read your own `my-expenses.txt`; write the new `main` and explain why changing only `path` is not enough.

::: details Answer
```sprig
func main() -> Unit throws Error:
    report(load_file("my-expenses.txt"))
```

Changing only `path` is not enough: the original `main` would run `write_utf8` first, overwrite your file with the sample data, and then the `remove_file` in `finally` would delete it. When reading your own data, leave out `temp_file()`, `write_utf8` and `remove_file`; only a file the program created itself is the program's to delete.
:::

Previous chapter: [23. Tools and AI assistants](/en/tutorial/ch23-tooling).

To go deeper into the Java boundary: [Appendix F. Java interop in depth](/en/tutorial/appendix-f-advanced-java).

::: tip Coming from another language?
If you reach for `BigDecimal` or Java `double` for money: whole cents is what many payment systems really do—no floating-point rounding surprises, and you own every carry rule. `try` / `finally` works as in Java, except a Sprig `try` may have only `finally` and no `catch`, which is exactly the "clean up either way" shape used here.
:::
