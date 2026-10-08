# 19. Testing

Change a line, run the program by hand, look at the output — after three rounds this gets tiring, and tired eyes miss things. Tests write down "I expect it to behave like this" as a program, so the machine checks it for you every time. Chapter 18 showed the `tests/` directory; this chapter covers it properly.

In this chapter you will learn:

- the four tools of `@std/test`: `check`, `check_error`, `equal_*` and `finish`;
- a test is an ordinary program; finishing normally counts as a pass;
- `sprig test` runs every test under `tests/` at once, and `--filter` picks the matching ones;
- `tests/compile_fail/` tests that "the compiler must reject this";
- why a test imports the module that does the work and never `main.spr`.

## 19.1 A test is a program

Start with the smallest test file; running it directly with `sprig run` shows each check's result:

<<< @/snippets/book_en/ch19_checks.spr

```text
ok parses a number
ok rejects
ok rejects text
3 passed, 0 failed
```

Line by line:

- `import "@std/test.spr" as testing` imports the test tools from the standard-library module `@std/test.spr`, under an alias as usual.
- `testing.check("parses a number", fn() => ...)` registers and immediately runs one check. The first argument is the check's name; the second is a function whose body passing means the check passes.
- `testing.equal_int(parse_count("42"), 42, "parse")` compares two integers and throws an `Error` naming both when they differ. `check` catches that error and prints the reason.
- `testing.check_error("rejects", fn() => parse_count("abc"))` asserts the opposite: this code **must** fail. `parse_count("abc")` throws an `Error`, so the check passes.
- `testing.finish()` prints `N passed, N failed` and ends the program with exit status 1 when something failed, so `sprig test` knows the file failed.
- Every passing check prints one `ok name` line, then the totals.

`parse_count` is the code under test: it turns text into an integer or throws. `toIntOrNull()` was introduced in chapter 4: it returns `Int?`, and `null` means the text is not an integer.

### What a failure looks like

A failed `equal` throws an `Error` carrying both values. You can catch it like any other error and read exactly what it says:

<<< @/snippets/book_en/ch19_failure.spr

```text
greeting: expected "Hi, Ada!", got "Hello, Ada!"
sum: expected 4, got 3
```

The message format is `what: expected EXPECTED, got ACTUAL`. `equal_text` quotes both sides and escapes newlines and tabs, so invisible differences such as a trailing space or a Windows `\r\n` versus a Unix `\n` are immediately visible. `equal_int`, `equal_bool`, `equal_text` and `equal` (any value that supports `==`, lists and maps included) are one family with the same shape.

Uncaught, a failure prints and ends the program:

```text
$ sprig test --filter failing
FAIL failing.spr
ok two plus two
FAIL greeting: greeting: expected "Hi, Ada!", got "Hello, Ada!"
1 passed, 1 failed
  Program exited with status 1

0 passed, 1 failed
```

That output came from a test file whose first check passed and whose second compared `"Hello, Ada!"` with `"Hi, Ada!"` the wrong way round. Reading it:

- `FAIL failing.spr`: this test file did not pass;
- `ok two plus two`: the first check passed;
- `FAIL greeting: ...`: the second failed; `greeting` is the label passed to `equal_text`, followed by the two values;
- `1 passed, 1 failed`: the file's own tally, printed by `finish()`;
- `Program exited with status 1`: the file exited with a failure status;
- the last line `0 passed, 1 failed` is `sprig test`'s tally over all test files: one file, zero passing.

A failing `check` does not stop the later checks — both checks ran. That is the point of `check`: one run tries everything you wrote instead of stopping at the first error.

## 19.2 Tests in a project: sprig test

Real tests live in a project's `tests/` directory. The layout and three files:

```text
greeter/
├── sprig.toml
├── src/
│   ├── main.spr
│   └── app.spr
└── tests/
    ├── app_test.spr
    └── compile_fail/
        └── not_int.spr
```

`app.spr` is the module that does the work, and it has only functions:

<<< @/snippets/book_en/ch19_testing/src/app.spr

`main.spr` only handles arguments and hands the work to `app.run`:

<<< @/snippets/book_en/ch19_testing/src/main.spr

`tests/app_test.spr` imports `app.spr` and calls its functions directly:

<<< @/snippets/book_en/ch19_testing/tests/app_test.spr

```text
ok greets
usage: greet NAME
ok needs a name
2 passed, 0 failed
```

The middle line, `usage: greet NAME`, is not printed by the test framework; `app.run([])` prints it itself, complaining about the empty argument list. Run a test file directly with `sprig run` and you see each check; hand it to `sprig test` and it reports only whether the whole file passed:

```text
$ sprig test
PASS app_test.spr
PASS compile_fail/not_int.spr
PASS tempdir.spr

3 passed, 0 failed
```

- `sprig test` runs every `.spr` file under `tests/`, subdirectories included, each in its own JVM;
- `PASS file` means the file finished normally;
- `sprig test --filter app` runs only the files whose path contains `app`:

```text
$ sprig test --filter app
PASS app_test.spr

1 passed, 0 failed
```

`--filter` matches the **file name or path**, not check names: `--filter greets` runs nothing just because a check is called `greets`. `sprig test --json` gives a machine-readable result for tools; like the project's other commands, `sprig test` needs a current `sprig.lock` (run `sprig resolve` first).

### A temporary directory per test file

Tests often write files, and they should not dirty your working directory. `testing.temp_dir()` returns the directory `sprig test` prepared for this test file; it is deleted when the file finishes. This test writes a file and reads it back:

<<< @/snippets/book_en/ch19_testing/tests/tempdir.spr

It is the third `PASS tempdir.spr` line in the `sprig test` output above. Note that `temp_dir()` works only inside `sprig test`: running the file with plain `sprig run` fails because there is no test environment.

## 19.3 Deliberate mistake: a test imports main.spr

What happens when a test file starts like this?

```sprig
import "../src/main.spr" as main
```

Recall 18.1: importing a module runs its top-level statements. The top-level statements of `main.spr` are the entire program — it reads arguments, runs `app.run` and calls `process.exit`. So the program runs once before the test even starts:

```text
$ sprig test --filter wrong_way
FAIL wrong_way.spr
usage: greet NAME
  Program exited with status 2

0 passed, 1 failed
```

Reading it: `main.spr` gets an empty argument list, prints `usage: greet NAME` and returns 2; `process.exit(2)` ends the process on the spot. None of the checks in the test file ran (there is not even an `ok never runs` line), and `sprig test` only sees a process that exited with status 2, so the file fails. If the program happened to exit 0, `sprig test` might even report a pass — while the checks never ran.

The rule is simple: keep `main.spr` down to argument handling, put the real work in another module (`app.spr` here), and have both `main.spr` and the tests import it.

## 19.4 Testing what must not compile

Some guarantees are about the compiler, not runtime behaviour: for example, "assigning `null` to an `Int` must be rejected". Files like that live in `tests/compile_fail/`, each with a sibling `.expect.toml` listing the expected error codes.

`tests/compile_fail/not_int.spr`:

<<< @/snippets/book_en/ch19_testing/tests/compile_fail/not_int.spr

```text
SPR-TYPE-NULL [TYPE] main.spr:2:14: null is not assignable to Int (initializer); use Int? (expected Int, actual null)
```

`tests/compile_fail/not_int.expect.toml`:

```toml
codes = ["SPR-TYPE-NULL"]
```

The `expect.toml` does not restate the whole message, only the code. The checker really compiles the file and looks for the expected failure and code. This file is the `PASS compile_fail/not_int.spr` line in the `sprig test` output above: it "passes" meaning "it really did fail to compile, in exactly the expected way".

If someone ever loosens the compiler, that line turns into `FAIL` — that is what this kind of test is for.

## Summary

- A test is an ordinary program: `check`/`check_error` register checks, `equal_int`, `equal_bool`, `equal_text` and `equal` compare values, `finish()` prints the tally and sets the exit status.
- On success `sprig run` prints `ok name` lines and the totals; `sprig test` prints only `PASS`/`FAIL` per file.
- `--filter` selects by file path; tests need a current `sprig.lock`.
- `testing.temp_dir()` is a test-only temporary directory, available only under `sprig test`.
- A test imports the module that does the work, never `main.spr`.
- `tests/compile_fail/` plus `.expect.toml` turns "the compiler must reject this" into a test.

## Exercises

### Exercise 1: write a first passing check

Write a test file: the function `double(x)` returns `x * 2`; use `check` and `equal_int` to verify that `double(3)` is `6`, then call `finish()`.

Hint: all three parts are needed — the import, the check, and `finish()`; without `finish()` there is no tally.

::: details Answer

<<< @/snippets/book_en/ch19_ex1.spr

```text
ok doubles 3
1 passed, 0 failed
```

:::

### Exercise 2: assert "this must fail"

Given a function `parse_count(text)`, write one `check_error` that its input must throw on the empty string.

Hint: what the `check_error` body returns does not matter, as long as it throws an `Error`.

::: details Answer

<<< @/snippets/book_en/ch19_ex2.spr

```text
ok rejects empty
1 passed, 0 failed
```

:::

### Exercise 3: read a failure message

Call `testing.equal_text(greet("Ada"), "Hi, Ada!", "greeting")` where `greet` returns `"Hello, Ada!"`. Catch the failure with `try`/`catch` and print `problem.message` to see how the message separates the expected and the actual value.

Hint: the format is `what: expected EXPECTED, got ACTUAL`.

::: details Answer

<<< @/snippets/book_en/ch19_ex3.spr

```text
greeting: expected "Hi, Ada!", got "Hello, Ada!"
```

:::

Next chapter: [The standard library at work](/en/tutorial/ch20-stdlib).
