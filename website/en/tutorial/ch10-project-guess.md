# 10. Project: guess the number

In this chapter you will learn:

- how to split a small problem into functions;
- how to make reproducible random numbers with `@std/random`;
- how to test game logic with a fixed list of input;
- how to read input line by line with `@std/process`;
- command-line arguments, standard error and exit status.

## 10.1 The rules

The program thinks of a secret number between 1 and 100. You guess; it answers:

- too small: `too low`
- too large: `too high`
- right: `correct`, and how many tries it took

The project is small, but it uses everything from the first nine chapters: variables, loops, functions, nullable values and lists. We build it in four steps, each of which runs on its own.

## 10.2 Step 1: the secret number

Random numbers live in `@std/random.spr`. `seeded(seed)` creates a random source from a seed: the same seed gives the same sequence on every run, which is why the outputs in this book can be fixed. To get a different sequence every time, use `fresh()`.

<<< @/snippets/book/ch10_secret.spr

```text
1
5
5
21
```

Line by line:

- `let dice = random.seeded(7)` creates a random source with seed 7.
- `dice.next_int(6)` returns an integer from `0` to `5` inclusive; adding 1 makes it 1 to 6, like a die.
- The first is `1` and the second is `5`: successive calls move along the source's sequence.
- The third `dice.next_int(6) + 1` is `5` again, continuing the same sequence.
- `random.seeded(7).next_int(100) + 1` starts a fresh source with the same seed, so the sequence begins again and gives `21` — this is how the game makes a secret from 1 to 100 (`next_int(100)` is 0 to 99, plus 1 is 1 to 100).

`next_int` requires a positive bound; a nonpositive bound fails with an `Error`. That is chapter 14 material; here the bound is always a valid positive number.

## 10.3 Step 2: judging one guess

Write the judgement for one guess as a function. It takes the secret and the guess, and returns the line to print:

<<< @/snippets/book/ch10_hint.spr

```text
too low
too high
correct
```

- `func hint(secret: Int, guess: Int) -> String:` declares two `Int` parameters and a `String` result.
- `guess < secret` takes the first `return "too low"`.
- Otherwise `guess > secret` returns `"too high"`.
- If neither `if` ran, the guess is right, and the last line returns `"correct"`. Every path returns, and the compiler checks that (chapter 7).

## 10.4 Step 3: playing a whole round

Keyboard input cannot be verified automatically in a book, so first we pass "what the player guesses" in as a list. The function returns how many tries were used:

<<< @/snippets/book/ch10_guess_fixed.spr

```text
secret is 21
50: too high
10: too low
skipped: oops
21: correct
finished after 3 guesses
```

Read `play`'s loop together with the trace table:

| Step | `text` | `guess` | `tries` | printed |
|---|---|---|---|---|
| start | — | — | 0 | — |
| 1 | `"50"` | 50 | 1 | `50: too high` |
| 2 | `"10"` | 10 | 2 | `10: too low` |
| 3 | `"oops"` | null | 2 | `skipped: oops` |
| 4 | `"21"` | 21 | 3 | `21: correct`, then `return 3` |

- `text.toIntOrNull()` converts a string to an `Int`; when it cannot, the result is `null` and the type is `Int?`.
- The `if guess == null:` branch prints `skipped: oops`, and `continue` jumps to the next guess without adding to `tries`.
- `hint(secret, guess)` reuses the function from 10.3; here `guess` has been narrowed to `Int`.
- On a correct guess, `return tries` ends the function at once and the rest of the list is not read — an early return.
- If the list runs out without a hit, it prints `out of guesses; the number was 21` and returns the number of tries used.
- The top-level code computes `secret`, calls `play`, and prints the total. `"finished after " + used + " guesses"` shows that an `Int` can be joined to a string with `+`.

## 10.5 Step 4: reading from the keyboard

The real game reads with `process.read_line()` from `@std/process.spr`: it returns the next input line without its line ending, or `null` at the end of input (EOF). This program has the same logic as 10.4, with "the list" replaced by "one line at a time":

<<< @/snippets/book/ch10_guess_interactive.spr

Compare it with the 10.4 version point by point:

- `while true:` loops forever; when `read_line()` gives `null` (input ended) it prints the result and returns. Without that test the loop would never stop.
- `line` is a `String?`. As before, `toIntOrNull()` is followed by a `null` test; on input `oops` it prints `not a number: oops` and continues.
- A correct guess prints `correct in 3 tries`; `tries` counts only valid guesses.
- The secret still uses `seeded(7)`, so the book's output is the same on every run. For a real release, change it to `fresh()` and every run differs.

The signature of `play_interactive` gained `throws Error`: `read_line` can fail, and a function that calls it must declare that in its signature. Chapter 14 has the full rules; for now, copy the signature.

Save the program as `guess.spr`, run it in a terminal, and feed it input:

```bash
printf '50\n10\noops\n21\n' | sprig run guess.spr
```

```text
I am thinking of a number between 1 and 100.
too high
too low
not a number: oops
correct in 3 tries
```

You can also run `sprig run guess.spr` directly; the program then waits for you. Type one guess per line and finish the input with Ctrl-D (macOS, Linux) or Ctrl-Z then Enter (Windows).

::: tip Coming from another language?
Python's `input()` raises `EOFError` when the input ends, and you catch it; Sprig turns the end of input into `null`, tested with `if line == null:`. Randomness is reproducible in the same way as Python's `random.seed(s)`, but Sprig hands you the source (`let dice = random.seeded(7)`) and you keep drawing from it; calling `random.seeded(7)` again restarts the sequence. Command-line arguments are not a global like `sys.argv`: ask for them with `process.arguments()`, and only the words after `--` are passed in.
:::

### Deliberate mistake: forgetting `throws Error`

Delete `throws Error` from the function header, keeping only this smallest piece:

<<< @/snippets/book/ch10_throws_missing.spr

```text
SPR-FLOW-THROWS [FLOW] main.spr:4:16: Call may throw Error; declare 'throws Error' or handle it with try/catch
  hint: Declare it on 'ask' by changing its header to 'func ask() -> Unit throws Error:', and its callers then handle or declare it too (top-level statements need neither), or handle it where it happens: 'try:' around the call, then 'catch problem: Error:' with what to do instead. Sprig keeps recoverable errors explicit; there is no implicit propagation.
```

The code `SPR-FLOW-THROWS` says: `read_line()` may throw an `Error`, and `ask` neither handles it (no `try`/`catch`) nor declares it, so the compiler will not let it slip by. The `hint` gives the fix: change the header to `func ask() -> Unit throws Error:`. Chapter 14 is about error handling, and by then you will know how to choose between the two roads, `try`/`catch` and `throws`.

## 10.6 Arguments, standard error and exit status

**Command-line arguments.** `process.arguments()` returns the argument list as `List[String]`. Words after `--` on the command line are passed through unchanged:

<<< @/snippets/book/ch10_args.spr

```text
no arguments; run me as: sprig run main.spr -- Ada Bob
```

Run with no arguments, `args` is empty and it prints the hint. With arguments:

```bash
sprig run main.spr -- Ada Bob
```

```text
hello Ada
hello Bob
```

**Standard error.** `process.print_error(text)` writes to standard error, the place for messages that are not the program's normal output, such as warnings. Normal `print` writes to standard output:

<<< @/snippets/book/ch10_print_error.spr

```text
this line goes to standard output
```

When only standard output is piped onward, that one line is all you get. Run in a terminal and you also see `this line goes to standard error`. When both are merged (`2>&1`, for example) their order is not guaranteed, so do not depend on it.

**Exit status.** `process.exit(status)` ends the program at once; `0` means success and a nonzero value means failure. You rarely call it yourself; it is for reporting failure to a command-line script:

<<< @/snippets/book/ch10_exit.spr

```text
before exit
```

`print("this never prints")` does not run; code after `exit(0)` is unreachable at run time.

## Summary

- `random.seeded(seed)` repeats on every run; `random.fresh()` does not. `next_int(n)` gives `0..n-1`.
- Pass the input as an argument (a list of guesses) and the game logic can be tested automatically; then swap in `process.read_line()` to read the keyboard.
- `read_line()` gives a `String?`: `null` means the input ended. `toIntOrNull()` uses `null` for "not a number".
- A function that calls something that can fail must write `throws Error` in its signature, or the compiler reports `SPR-FLOW-THROWS`.
- `process.arguments()` reads the words after `--`; `process.print_error` writes standard error; `process.exit(0)` ends normally and a nonzero status means failure.

## Exercises

**Exercise 1 (warm-up)** Change the secret's seed to `42` and the range to 1..10, then print it.

Hint: change the two numbers inside `seeded(7)` and `next_int(100)`; keep the `+ 1`.

::: details Answer
<<< @/snippets/book/ch10_ex1_answer.spr

```text
secret is 2
```
:::

**Exercise 2** When the list of guesses runs out, report how many tries were used as well as the secret.

Hint: in 10.4, change the final `out of guesses...` line to include `tries`.

::: details Answer
<<< @/snippets/book/ch10_ex2_answer.spr

```text
too low
too high
out of guesses after 2 tries; the number was 21
```
:::

**Exercise 3** Reject guesses outside 1..100: print a message and do not count them as tries.

Hint: after `toIntOrNull` and before `tries += 1`, add an `if guess < 1 or guess > 100:` branch.

::: details Answer
<<< @/snippets/book/ch10_ex3_answer.spr

```text
out of range: 0
out of range: 200
too low
correct in 2 tries
```
:::

**Exercise 4** Deal three rounds in a row: use seeds 1, 2 and 3 to make a secret from 1..100 each and print them.

Hint: `for seed in [1, 2, 3]:`, then `seeded(seed)` inside the loop.

::: details Answer
<<< @/snippets/book/ch10_ex4_answer.spr

```text
round 1: secret 97
round 2: secret 89
round 3: secret 77
```
:::

The next chapter starts the "describing the world with types" part: defining your own classes: [Classes and objects](/en/tutorial/ch11-classes).
