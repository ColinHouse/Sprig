# 2. Your first program, and reading errors

In this chapter you will:

- write and run your first Sprig program;
- learn what `print` does and what comments are;
- see the difference between `sprig run` and `sprig check`;
- read a compiler error, part by part;
- meet three common mistakes, plus `sprig explain` and `sprig fmt`.

## 2.1 Your first program

A program is a file full of instructions for the computer. Create a file named `hello.spr` (the `.spr` extension marks it as a Sprig program) with this content:

<<< @/snippets/book_en/ch02_hello.spr

```text
Hello, Sprig
One file, two lines.
```

In a terminal, change into the directory that holds the file and run:

```bash
sprig run hello.spr
```

Those two lines appear on screen. Read the program line by line:

- `# A full line starting with # is a comment; the compiler ignores it.` A **comment** is a note for humans; the compiler skips it. Anything after `#` is up to you, in any language.
- `print("Hello, Sprig")` calls `print`, which prints the text in the parentheses and then ends the line. `print` is one of Sprig's built-in functions.
- `"Hello, Sprig"` is a **string**: text between double quotes. The quotes themselves are not printed.
- The second `print(...)` prints the next line.

One more thing: **there is no `main`**. The top-level statements are the program; they run from top to bottom. You don't write "start" and "end"; Sprig runs the first line and stops after the last one.

`print` takes exactly one argument. To print two things on one line, join them into one string with `+` first; `print(1, 2)` is rejected with the code `SPR-CALL-ARITY` ("wrong number of arguments"). Chapter 4 covers joining text.

### Comments

Comments explain code to your future self. There are two forms:

- A whole-line comment: the line starts with `#`.
- A trailing comment: after some code, `#` starts a comment that runs to the end of the line, as in `print("hi")  # say hello`.

`#` only starts a comment outside a string: `print("# not a comment")` prints the whole string `# not a comment`.

### Save the file, run the file

`sprig` must be able to find your file. Use `cd` to enter its directory, then run `sprig run hello.spr`. The first run is slower because the program is first compiled to Java; an unchanged program skips recompiling and runs quickly.

## 2.2 Check without running

The command you'll use all the time is:

```bash
sprig check hello.spr
```

It only checks the code; it does not run it. When everything is fine it prints **nothing**; when something is wrong it lists every error at once, and it never executes your program. Write code with `check`; run it when it's clean.

## 2.3 What an error is made of

Now make a mistake on purpose. Replace the contents of `hello.spr` with this one line:

<<< @/snippets/book/ch02_type_error.spr

```text
SPR-NUM-MIXED [TYPE] main.spr:1:7: Operator '+' has no implicit conversion between Int and Float (expected matching numeric families, actual Int and Float)
  hint: Convert the Int side: 1.toFloatExact().
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

The file is shown as `main.spr` here; your own file shows its own name. An error line has these parts, left to right:

| Part | In the line above | Meaning |
|---|---|---|
| Code | `SPR-NUM-MIXED` | A fixed identifier for this kind of error; every code is documented |
| Phase | `[TYPE]` | Where it went wrong: lexing (LEX), syntax (SYNTAX), types (TYPE), and so on |
| Position | `main.spr:1:7` | File, line, column (both count from 1) |
| Message | `Operator '+' has no implicit conversion...` | One sentence about what is wrong |
| Hint | `hint: Convert the Int side: 1.toFloatExact().` | How to fix it; not every error has one |

The last line is the summary: how many errors there were and where to look up codes. Wherever this book shows a `SPR-...` code, you can run `sprig explain CODE` in a terminal to get the full explanation.

This particular error is very Sprig: `1` is an integer and `2.0` is a decimal, and Sprig won't guess which one you meant. It asks you to convert explicitly. Chapter 3 explains why and shows the fix.

### Deliberate mistake: a misspelled name

<<< @/snippets/book/ch02_typo.spr

```text
SPR-NAME-UNRESOLVED [NAME] main.spr:1:1: Unresolved name 'pritn'
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

`pritn` was never declared. The position `1:1` points at the start of the line, so the name itself is the problem. This one has no `hint:`, so compare the name with the one you meant: `print` is spelled p-r-i-n-t. Fix it and move on.

### Deliberate mistake: a missing closing paren

<<< @/snippets/book/ch02_paren.spr

```text
SPR-LEX-UNCLOSED [LEX] main.spr:1:6: Unclosed grouping delimiter at end of file
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

`[LEX]` means the error happened while reading characters: an opening `(` was seen and no matching `)` arrived before the end of the file. `1:6` is where the unclosed paren is. Add the `)` back.

## 2.4 Looking up a code with `sprig explain`

When you meet a code you don't know, hand it to `sprig explain`:

```bash
sprig explain SPR-NAME-UNRESOLVED
```

```text
SPR-NAME-UNRESOLVED: A name has no declaration in the current scope chain.
Why it matters: Every name is declared before use; there is no implicit global or dynamic lookup.
Common causes:
  - A typo, a missing import, or a declaration placed after first use.
  - A spelling from another language, such as readLine, input, len, str, True, None, self or the types int and str; the hint names the Sprig spelling.
  - A Java class such as Math or Scanner used without 'import java.lang.Math as Math'.
Safe fixes:
  - Check the spelling, add the import, or move the declaration before use.
  - Read standard input with @std/process.spr (read_lines, read_line, read_all).
```

It says why the error happens and how to fix it. When a code is unfamiliar, start here instead of a web search.

## 2.5 Uniform formatting with `sprig fmt`

Loose spacing and indentation don't stop a program from running, but they make it harder to read. `sprig fmt` reformats a file in one canonical style, keeping your comments. If the file contains:

```sprig
print(  "Hello, Sprig"  )
let   name="Ada"
print( name )
```

running `sprig fmt hello.spr` rewrites the file in place and prints a line `Formatted ...` (`...` is the file's full path). The file now reads:

```sprig
print("Hello, Sprig")
let name = "Ada"
print(name)
```

A formatted program produces the same output. To recap: `fmt` changes the style, `check` finds errors, `run` runs.

::: tip Coming from another language?
You may be used to "run and find out". Sprig is statically typed: `sprig check` reports every static error before anything runs, and it never executes your code. The error format is fixed: code, phase, position, message, hint; codes are documented under `sprig explain`. Use `check` like a compiler and `run` like a runner.
:::

## Summary

- Top-level statements are the program, in source order; there is no `main`.
- `print` prints one value and ends the line; strings sit between double quotes.
- A full line starting with `#` is a comment; a trailing comment works too.
- `sprig run FILE.spr` checks and runs; `sprig check FILE.spr` only checks, silently on success.
- An error is: code + phase + `file:line:column` + message + (sometimes) a hint.
- `sprig explain CODE` explains a code; `sprig fmt FILE` formats a file.

## Exercises

1. Write a program that prints your name and your city, each on its own line.
   Hint: two lines of text need two `print` calls.

::: details Answer
<<< @/snippets/book/ch02_ex_lines.spr

```text
Ada
Amsterdam
```
:::

2. This program has a spelling mistake; fix it.

```sprig
prnit("hi")
```

Hint: compare it letter by letter with the `print` used in this chapter.

::: details Answer
Change `prnit` to `print`:

<<< @/snippets/book/ch02_ex_typo.spr

```text
hi
```
:::

3. Without running anything, guess what `print("Oops)` reports; then save it to a file and run `sprig check` to see.
   Hint: count the quotes and the parentheses; does each have a partner?

::: details Answer
The string isn't closed and the paren isn't closed, so there are two errors:

<<< @/snippets/book/ch02_ex_unclosed.spr

```text
SPR-LEX-STRING [LEX] main.spr:1:7: Unterminated string literal
SPR-LEX-UNCLOSED [LEX] main.spr:1:6: Unclosed grouping delimiter at end of file
2 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

The fix: write `print("Oops")`.
:::

Next chapter starts playing with numbers and answers the question this one left open: [Chapter 3: Values, variables and arithmetic](/en/tutorial/ch03-values).
