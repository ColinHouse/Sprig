# Tutorial: build a small expense tracker

In this tutorial you'll write a small tool from scratch. It reads a list of expenses and adds up how much you spent in each category.

It takes about half an hour. You don't need to know Java; if you've written a bit of Python or JavaScript, you're ready. Every step has code you can paste and run, with the output right below it so you can check your result.

Along the way you'll break things on purpose a few times. Reading the compiler's errors is a big part of learning Sprig, and it's one of the things the language is best at.

## 0. Set up

You need JDK 21 or newer and the Sprig SDK. If you haven't installed them yet, start with [Getting started](/en/guide/getting-started).

Then create a project:

```sh
sprig init ledger
cd ledger
sprig resolve
sprig run
```

If you see `Hello, Sprig!`, you're all set. Open `src/main.spr` and you'll find:

```sprig
# ledger entry point.

func main() -> Unit:
    print("Hello, Sprig!")

main()
```

For each step below, replace the contents of `src/main.spr` with the example and run `sprig run`.

> The error messages in this tutorial come from the latest Sprig source. With an older SDK, a few hints may be worded differently, but the error codes are the same.

## 1. Values and types

<<< @/snippets/tutorial/ledger/01_values.spr

```text
Coffee
3750
true
```

- A name declared with `let` can't change later; one declared with `var` can.
- You can leave types out and Sprig infers them: `item` is a `String`, `price` is an `Int`.
- `cups > 2` is a `Bool`. Conditions in `if` and `while` must be `Bool`; Sprig never treats `0` or an empty string as false.

Now make a mistake on purpose. Write the price with a decimal point and give it to an integer:

<<< @/snippets/tutorial/ledger/01_values_error.spr

```text
SPR-NUM-CONVERSION [TYPE] main.spr:2:18: Cannot implicitly convert Float to Int in initializer; precision or range may change (expected Int, actual Float)
  hint: Use toIntExact() if the value must be whole, toIntTrunc() to drop the fraction, or java.lang.Math.round(x) to round to the nearest Int.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

Plenty of languages would quietly drop the `.5`. Sprig stops and lists your options: insist that the value is whole, cut off the fraction, or round. You pick the one you mean.

`SPR-NUM-CONVERSION` at the start is the error code. Every kind of error has a fixed code; step 8 shows what you can do with it.

::: details Try it: what happens if you add `item = "Tea"` as the last line?
`item` was declared with `let`, so the compiler refuses:

```text
SPR-NAME-LET-ASSIGN [TYPE] main.spr:9:1: Cannot assign to immutable binding 'item'; declare it with var
```

If the name really needs to change, declare it with `var` instead.
:::

## 2. Functions, and why money is stored in cents

<<< @/snippets/tutorial/ledger/02_money.spr

```text
6650
66.50
0.30000000000000004
```

Look at the last line first: `0.1 + 0.2` prints `0.30000000000000004`. That isn't a Sprig bug; floating-point numbers simply can't represent 0.1 exactly. That's why the tracker stores money as whole cents: 12.50 becomes `1250`.

Now the functions:

- Parameters and return values always have types. The signature alone tells you that `total` takes a list of integers and returns one.
- `cents.divTrunc(100)` divides two integers and drops the remainder; `%` gives you the remainder.
- `today` is declared as `List[Int]`. Without the annotation, a list literal is a `MutableList[Int]`, and `total` asks for a read-only `List[Int]`. Sprig won't convert between them behind your back, so you say which one you want.

::: details Try it: why not just write `cents / 100`?
Sprig doesn't let you divide two integers with `/`:

<<< @/snippets/tutorial/ledger/02_money_error.spr

```text
SPR-NUM-DIVISION [TYPE] main.spr:2:7: Integer / would truncate; use a.divTrunc(b), or convert both operands explicitly (expected explicit division, actual Int / Int)
  hint: Write cents.divTrunc(100) to drop the remainder on purpose, or cents.toFloatExact() / 100.toFloatExact() for a Float result. text.fixed(value, decimals) from @std/text.spr prints a Float with that many decimals.
```

Is `7 / 2` equal to 3 or 3.5? Languages disagree. In Sprig you write which one you mean: `divTrunc` for 3, or convert to `Float` first for 3.5.
:::

## 3. A class for one expense

<<< @/snippets/tutorial/ledger/03_expense.spr

```text
Expense(item=Coffee, cents=1250, note=)
October
true
```

- Every field has a type. A `let` field is fixed once the object exists; a `var` field can change.
- `note` has a default value, so you can leave it out.
- You create objects with field names, as in `Expense(item="Coffee", cents=1250)`. It's a few more keystrokes, but nobody reading the code has to remember the field order.
- Methods can use fields directly: `cents` inside `is_big` is this expense's amount.
- Printing an object lists all its fields, which comes in handy when debugging.

::: details Try it: what if you remove `cents=1250`?
`cents` has no default, so you have to provide it:

```text
SPR-CALL-MISSING-FIELD [TYPE] main.spr:9:14: Missing required field 'cents:Int'
```
:::

## 4. Categories: variant and match

Every expense belongs to a category, and there's a fixed set of them. That's exactly what a `variant` is for:

<<< @/snippets/tutorial/ledger/04_category.spr

```text
food
other: movie
```

- A `variant` lists every shape a value can take. `Food` and `Transport` carry no data; `Other` carries a `label`.
- `match` handles each case. `case Category.Other as other:` binds the value to `other`, so you can read `other.label`.
- There's no catch-all branch in a `match`. Every case has to be written out.

That last rule pays off the moment you forget a case. In the `match`, delete the `Other` branch (`case Category.Other as other:` and the line below it) and run again:

```text
SPR-MATCH-NONEXHAUSTIVE [FLOW] main.spr:7:12: Missing case: Category.Other
  hint: Add 'case Category.Other:' (there is no default case)
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

Now picture adding a `Shopping` category a month from now. Every `match` that doesn't handle it shows up at compile time, one by one, before any user runs into it.

## 5. When there's nothing to find: nullable values

<<< @/snippets/tutorial/ledger/05_lookup.spr

```text
3600
no tea today
MONDAY
```

- The `?` in `Expense?` means "might be `null`": looking something up by name doesn't always find it.
- You have to check before using it. After `if taxi != null:`, Sprig knows `taxi` has a value inside that branch.
- Java methods work the same way. Sprig treats every object returned by Java as possibly `null`, so the result of `LocalDate.parse` gets checked too.

Skip the check and use the value directly:

<<< @/snippets/tutorial/ledger/05_lookup_error.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:12:12: Cannot access 'cents' on a value that may be null (receiver type Expense?)
  hint: Bind the receiver to a let and check it with 'if value != null:' before using 'cents'.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

Wrapping code in `if x != null:` isn't the only option. You can also return early: after `if x == null: return ...`, the rest of the function treats `x` as non-null.

## 6. Things that can fail: errors in the signature

People type amounts like `12.50`, and sometimes like `abc`. When parsing fails, it's better to say so than to return a default that hides the problem:

<<< @/snippets/tutorial/ledger/06_parse.spr

```text
1250
800
skipped: not an amount: twelve
```

- `throws Error` in the signature tells every caller that this function can fail.
- `throw Error("...")` fails with a reason.
- A caller has two choices: handle it with `try` / `catch`, or add `throws Error` to its own signature and let its caller deal with it.
- After `if units == null or ...: throw ...`, `units` is known to have a value. It's the same early-exit idea as in step 5.

::: details Try it: what if a function without `throws` calls `parse_amount`?
Here `show` calls `parse_amount` with no `try` and no `throws`:

<<< @/snippets/tutorial/ledger/06_parse_unhandled.spr

The compiler asks you to pick one of the two:

```text
SPR-FLOW-THROWS [FLOW] main.spr:14:11: Call may throw Error; declare 'throws Error' or handle it with try/catch
  hint: Declare it on 'show' by changing its header to 'func show(text: String) -> Unit throws Error:', and its callers then handle or declare it too (top-level statements need neither), or handle it where it happens: 'try:' around the call, then 'catch problem: Error:' with what to do instead. Sprig keeps recoverable errors explicit; there is no implicit propagation.
```
:::

## 7. Putting it together: read a file, print a report

Time to turn the pieces into a working program. A real tracker would read your own expense file; so that the example runs as-is, this one first writes a few sample lines to a temporary file and then reads them back.

It starts with imports: `@std/files` for reading and writing files, and `@std/text` for text helpers. The second one is called `strings` so it doesn't collide with `parse_amount`'s parameter `text`.

<<< @/snippets/tutorial/ledger/07_report.spr#imports

Then the data: categories and expenses.

<<< @/snippets/tutorial/ledger/07_report.spr#model

`format_money` from step 2 and `parse_amount` from step 6 come over unchanged, so they aren't repeated here. Two small functions turn text into a category and back:

<<< @/snippets/tutorial/ledger/07_report.spr#categories

Turning a line like `Coffee, 12.50, food` into an expense:

<<< @/snippets/tutorial/ledger/07_report.spr#parse

Adding up each category. The totals change as we go, so they live in a `MutableMap`. Reading from a map gives you an `Int?`, because the category may not have an entry yet:

<<< @/snippets/tutorial/ledger/07_report.spr#report

And finally the entry point: write the file, read it, parse each line, print the report:

<<< @/snippets/tutorial/ledger/07_report.spr#main

Output:

```text
Food: 30.50
Transport: 36.00
Other: 45.00
Total: 111.50
```

The complete program is [on GitHub](https://github.com/ColinHouse/Sprig/blob/main/website/snippets/tutorial/ledger/07_report.spr).

::: details Try it: change the movie's amount to `45.5`
`parse_amount` wants exactly two digits after the point, so that line fails to parse. The error travels up to the outermost `catch`:

```text
not an amount: 45.5
```

To read your own expenses, delete the `temp_file()`, `write_utf8` and the cleanup `files.remove_file(path)` lines, then set `path` to your file. Deleting only the first two leaves the program deleting your expense file.
:::

## 8. When you're stuck

Most of the time you don't have to guess. Ask the compiler.

**Check without running.** `sprig check` is faster than `sprig run` and lists every error at once:

```text
$ sprig check
SPR-MATCH-NONEXHAUSTIVE [FLOW] main.spr:7:12: Missing case: Category.Other
  hint: Add 'case Category.Other:' (there is no default case)
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

**Don't understand an error?** Give its code to `sprig explain`. You get the reason, the usual causes and a good and a bad example:

```text
$ sprig explain SPR-MATCH-NONEXHAUSTIVE
SPR-MATCH-NONEXHAUSTIVE: Every enum/variant case must have a match branch; there is no default.
Why it matters: An exhaustive match makes adding a variant case a compile-time change, not a silent fallthrough.
Common causes:
  - A variant or enum gained a case, or the match omitted one.
Safe fixes:
  - Add an explicit case branch for every missing case reported by the diagnostic.
...
```

**Forgot some syntax?** `sprig help` takes a topic, like `sprig help nullability`, `sprig help numerics` or `sprig help match`. Run `sprig help` to see them all.

**Not sure how a Java method behaves?** `sprig api` shows its Sprig signature. That's how you find out why `LocalDate.parse` in step 5 returns a `LocalDate?`:

```text
$ sprig api java.time.LocalDate --member parse
Java API: java.time.LocalDate
constructors:
staticMethods:
  public static java.time.LocalDate java.time.LocalDate.parse(java.lang.CharSequence) => parse(CharSequence) -> LocalDate?
  ...
```

All of these commands accept `--json` and return structured results: the error code, the position down to the column, and a suggested fix. If you write code with an AI assistant, let it run these commands itself. It gets precise information instead of having to guess from a paragraph of text. Sprig was designed with that in mind from the start.

## Next steps

- A bigger program: the [task tracker example](https://github.com/ColinHouse/Sprig/tree/main/examples/task-tracker) is a complete command-line tool that keeps its data in a JSON file.
- The rest of the syntax: the [language quick reference](/en/guide/language-tour).
- Using other people's code: [projects and dependencies](/en/guide/projects) covers local packages and Git and Maven dependencies; [JVM interop](/en/guide/jvm-interop) covers calling Java libraries.
- Something bigger: [web and SQLite](/en/guide/web-sqlite), or [writing Minecraft mod logic in Sprig](/en/guide/fabric).
- Found a problem, or something feels awkward? [Open an issue](https://github.com/ColinHouse/Sprig/issues).
