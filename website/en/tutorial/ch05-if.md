# 5. Making decisions: if

Until chapter 4 every program had a single path: straight down, every line executed. Real programs need to choose — put on a coat when it is cold, upgrade when the score is high, apply a discount for members. This chapter is about how Sprig chooses.

In this chapter you will learn:

- to let a program pick one of several paths with `if`, `elif` and `else`;
- that a block is indentation, made of spaces and never tabs;
- why the compiler rejects `else if` with a message of its own;
- to use `if` as an expression that produces a value.

## 5.1 If this, do that

The simplest choice: when a condition holds, run a block of code.

<<< @/snippets/book/ch05_if_basic.spr

```text
wear a t-shirt
go outside
```

Line by line:

- `let temperature = 30` binds 30 to the name `temperature` (chapter 3).
- `if temperature > 25:` is "if". `temperature > 25` is a `Bool` expression, called the **condition**; the `:` at the end says "the indented block below belongs to me".
- `print("wear a t-shirt")` is indented by four spaces, so it is inside the `if` **block**. The condition is `true` (30 is indeed greater than 25), so the line runs.
- `print("go outside")` is not indented, so it is outside the block and always runs. Change 30 to 10 and the first line stops printing while the second still does.

Indentation is structure. Braces `{}` and trailing semicolons do not exist here; don't write them.

## 5.2 The other path: else

When the condition fails and you want a different path, add `else`:

<<< @/snippets/book/ch05_if_else.spr

```text
not yet
```

`age` is 15, so `age >= 18` is `false`; the `if` block is skipped and the `else` block runs, printing `not yet`. `else` carries no condition of its own, and it is written in the same column as `if`. Exactly one of the two blocks ever runs.

## 5.3 More than two paths: elif

For three or more paths, continue with `elif` (short for else-if):

<<< @/snippets/book/ch05_if_elif.spr

```text
freezing
cool
warm
```

The program runs the same decision three times, changing `celsius` first each time. The variable is declared with `var` on line 1 because its value changes.

- `celsius = -5`: `-5 < 0` is true, so it prints `freezing` and every later branch is skipped.
- `celsius = 12`: the first condition is false; `12 < 20` is true, so it prints `cool`.
- `celsius = 30`: the first two conditions are false, so it falls through to `else` and prints `warm`.

The conditions are checked from top to bottom and the chain is left as soon as one matches, so at most one branch runs. `else` is optional, but it always goes last. The three copies differ only in their data — chapter 6 uses loops to get rid of that repetition.

## 5.4 The indentation rule: spaces, not tabs

Blocks are indentation, and the choice is strict: **spaces only**. Press the Tab key and the compiler rejects the file, even if the tab looks exactly as wide as the spaces around it. This program indented line 3 with a Tab:

<<< @/snippets/book/ch05_tab.spr

```text
SPR-LEX-TAB [LEX] main.spr:3:1: Tabs are not allowed for indentation or inline whitespace
  hint: Sprig code blocks use spaces only; replace the tab with spaces.
SPR-SYNTAX-ERROR [SYNTAX] main.spr:3:2: Expected an indented block after 'if ...:'
```

How to read it:

- The first line is the important one. `main.spr:3:1` is the position: file `main.spr`, line 3, column 1. `SPR-LEX-TAB` is the diagnostic code — a tab error caught in the lexer; `sprig explain SPR-LEX-TAB` prints the full explanation.
- The `hint:` line gives the fix directly: replace the tab with spaces. Setting your editor to "insert spaces for tabs" saves a lot of trouble.
- The second line is a consequence of the first: once the tab is rejected, the parser never sees an indented block under the `if`. Fix the first problem and this one usually disappears.

Four spaces per level is the convention, and the examples here use it. Every line in the same block must line up; the compiler also accepts different widths in different blocks (one block with 2 spaces and another with 4, say), but keeping the book's width everywhere reads better.

## 5.5 Deliberate mistake: else if

People coming from Java, JavaScript or C almost always write `else if` for the second condition. In Sprig it is spelled `elif`:

<<< @/snippets/book/ch03_else_if.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:4:6: Sprig spells else-if as 'elif'
  hint: Write 'elif condition:' in place of 'else if condition:'.
```

`4:6` points at the misplaced `else`. The compiler recognises this habit from other languages and, instead of a generic syntax error, tells you the right spelling — replace `else if` with `elif` and you are done.

## 5.6 if as a value

Sometimes you do not want "which block runs" but "which value is chosen", for example a size picked from a count. `if` can also stand on the right-hand side of `=` and hand the chosen value straight to a variable:

<<< @/snippets/book/ch05_if_expr.spr

```text
some
```

Here `if` is an **expression**: the whole structure produces a value, and `size` receives `"some"`. The rules are few:

- Each branch is **one expression** on its own indented line, not a block of statements; the branches here are `"many"`, `"some"` and `"none"`.
- `elif` and `else` line up with the `if`; `else` is **required**, because an expression must always have a value.
- Every branch must produce the same type — here they are all `String`.
- It can only appear **where a value belongs**: after `=`, after `return` (chapter 7), after `throw` (chapter 14), and similar places. An `if` at the start of a statement is still the statement, whose `else` is optional.

Many languages use the `condition ? a : b` ternary operator; Sprig has none. The if expression is what it has instead.

## 5.7 Deliberate mistake: an if expression inside a call

Since an if expression is a value, it is tempting to put one into `print(...)`:

<<< @/snippets/book/ch05_if_expr_in_call.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:2:7: An if expression cannot be written inside parentheses, brackets or braces
  hint: Line breaks are ignored there, so the branches cannot go on their own lines. Bind the if expression, or the lambda that holds it, to a let first, then use the name.
```

The hint explains why: line breaks inside parentheses, brackets or braces are ignored, and each branch of an if expression must have a line of its own. Bind the result to a name first, then pass the name:

<<< @/snippets/book/ch05_if_expr_fix.spr

```text
yes
```

## Summary

- `if condition:` opens one path; the condition must be a `Bool`.
- `elif` checks the next path, `else` catches the rest; at most one branch runs.
- Blocks are indentation (four spaces), spaces only, never tabs.
- `else if` is spelled `elif`.
- An if expression on the right of `=` produces a value: one line per branch, matching types, and `else` is required.
- A standalone `if` is the statement, whose `else` is optional; an if statement cannot sit inside a call as an argument.

## Exercises

### Exercise 1: positive, negative or zero

`n` is `-7`. Print the right one of `positive`, `negative` or `zero`.

Hint: one `if` / `elif` / `else` chain testing `n > 0` and `n < 0`.

::: details Answer

<<< @/snippets/book/ch05_ex_sign.spr

```text
negative
```

:::

### Exercise 2: letter grades

`score` is 85. Print the grade: `A` for 90 and above, `B` for 80 and above, `C` for 60 and above, otherwise `F`.

Hint: check from the top down—ask `score >= 90` first, then `score >= 80`.

::: details Answer

<<< @/snippets/book/ch05_ex_grade.spr

```text
B
```

:::

### Exercise 3: backpack size

`bottles` is 5: print `large` for more than 9, `medium` for more than 3, and `small` otherwise. This time use an if expression: assign it to `size` in one go, then `print(size)`.

Hint: each branch holds a string, and the `else` holds `"small"`.

::: details Answer

<<< @/snippets/book/ch05_ex_if_expr.spr

```text
medium
```

:::

### Exercise 4: member discount

`is_member` is `true` and the total is 120. The rules: print `no discount` when not a member; when a member, print `20% off` if the total is at least 100 and `10% off` otherwise.

Hint: an outer `if is_member:` covers membership, and an inner `total >= 100` covers the amount — a block can contain another block, indented four spaces further.

::: details Answer

<<< @/snippets/book/ch05_ex_nested.spr

```text
20% off
```

:::

::: tip Coming from another language?

- Python: colons, indentation and `elif` are the same; but the condition must really be a `Bool`, so `if 0:` or `if "":` will not compile. Python's `a if cond else b` becomes the multi-line if expression here.
- Java / JavaScript / C: there is no `else if` — write `elif`; there is no `?:` ternary; braces and semicolons are left out and blocks are indentation.
- Deep nesting is hard to read. The early `return` of chapter 7 flattens nested `if`s.

:::

Next chapter: [Chapter 6: Repeating: loops](/en/tutorial/ch06-loops).
