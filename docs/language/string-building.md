# Building a String in a loop

A String is immutable, so `text += part` makes a new String from the old text
and the part. Run as that concatenation, a loop over n parts copies the
accumulated text n times: on the order of n squared characters.

## A function's local variable

The generated code keeps a local `var` of type `String` that a loop appends to
in a builder (`sprig.runtime.TextAccumulator`). An append copies only the new
part, so the loop takes time in proportion to the text it builds. Nothing in
the source changes:

```sprig
func csv_line(cells: List[String]) -> String:
    var line = ""
    for cell in cells:
        if line.length() > 0:
            line += ","
        line += cell
    return line
```

A variable is kept this way when both of these hold:

- it is declared with `var` inside a function, a method or a block, and its
  type is `String` (not `String?`);
- a `for` or `while` loop that starts after the declaration appends to it with
  `text += part` or `text = text + a + b`.

Every read gives the String the concatenation would give:

- The first read after an append takes the text out of the builder once and
  keeps it until the next append, so a loop that reads the variable on each
  pass costs what it cost before, and repeated reads cost nothing.
- `text.length()`, `text.isEmpty()` and a comparison with `""` are answered from
  the builder, so the usual check for placing a separator is cheap.
- A plain assignment starts the text over. That includes `text = ""` and a
  prepend such as `text = part + text`; each costs the length of the new value.
- In `text = text + a + b` every operand is evaluated before anything is
  appended, so an operand that throws leaves the variable unchanged.

## Module-level variables and fields

A module-level `var` and a field are shared state. A function, another module
or a task may read one between any two statements, so each `+=` on it produces
a real String and copies the text. Build such a text in a function and assign
the result once, or collect the parts in a list and join them:

```sprig
import "@std/text.spr" as text

let line = text.join(cells, ",")
```

## Measurements

One machine (macOS arm64, OpenJDK 26.0.1) and one program, compiled before
and after this was added: a function joins JSON rows with commas, as in the
report (#231).

| Rows | Before | After |
|---:|---:|---:|
| 20,000 | 2,157 ms | 16 ms |
| 50,000 | 8,044 ms | 25 ms |
| 100,000 | 32,012 ms | 26 ms |
| 1,000,000 | not run | 95 ms |

Both builds produce the same text (4,466,779 characters for 100,000 rows). The
times cover building the text only, not compilation or JVM start.

`tests/runtime/61_text_accumulator.spr` checks the text for each way such a
variable is written and read; its expected output was produced by the compiler
before the change. `tests/runtime/check_strings.py` checks which variables are
kept in a builder and runs 400,000 appends within the suite's time limit.
