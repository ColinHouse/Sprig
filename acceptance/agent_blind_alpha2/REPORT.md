# Sprig alpha.2 blind SDK exercise

Input: `../sprig-v0.1.0-alpha.2-jdk.zip`. I extracted it beside `work/`, read only the extracted SDK, and used its CLI plus the installed JDK. The archive and external project were not modified. All source files, compiler responses, and run output are in this directory.

SDK version: `sprig-compiler 0.1.0-alpha.2`, language `0.7`. The compiler's `check --json` results use exit code 0 for success. I treat `phase: TYPE` on the deliberate error as evidence that parsing completed before type checking failed. Each `run` invokes the shipped compiler and JDK; all ten runs exited 0 and wrote no stderr.

| Task | Source | First parse | First type check | Repair iterations | Final check | Final run | Observed stdout |
|---|---|---|---|---:|---|---|---|
| 1 Hello World | `01_hello.spr` | pass | pass | 0 | pass | pass | `Hello, world!` |
| 2 Class with methods | `02_class.spr` | pass | pass | 0 | pass | pass | `visits: 2` |
| 3 Recursive variant, exhaustive match | `03_variant.spr` | pass | pass | 0 | pass | pass | `9` |
| 4 Nullable handling | `04_nullable.spr` | pass | pass | 0 | pass | pass | `<missing>`; `SPRIG` |
| 5 Immutable/mutable collections | `05_collections.spr` | pass | pass | 0 | pass | pass | `[1, 2]`; `[1, 2, 3]`; `[1, 2, 3, 4]`; `2`; `3` |
| 6 Explicit conversion | `06_numeric.spr` | pass | pass | 0 | pass | pass | `3.5`; `3`; `7` |
| 7 JDK API | `07_jdk.spr` | pass | pass | 0 | pass | pass | `2026`; `26` |
| 8 Diagnostic and repair | `08_invalid.first-attempt.spr`, `08_invalid.spr` | pass | fail | 1 | pass | pass | `[1, 2]`; `[1, 2, 3]` |
| 9 Two-file module | `09_module/math.spr`, `09_module/main.spr` | pass | pass | 0 | pass | pass | `25` |
| 10 Medium program, original 34-line attempt | `10_medium.first-attempt.spr` | pass | pass | 0 | pass | pass | `Ada balance=12`; `[10, 7, 12]`; `average=9.666666666666666` |

Task 8 deliberately called `append` on `List[Int]`. Its first `check` returned `SPR-COLLECTION-IMMUTABLE` in the TYPE phase. `explain SPR-COLLECTION-IMMUTABLE --json` described the immutable collection rule. The one repair converted the list with `toMutableList()` before appending. The original source and both compiler responses are preserved.

No unsupported syntax or nonexistent JVM API was submitted. Syntax was taken from the archive's `AGENT_GUIDE.md`, quick reference, examples, and `help` topics. For task 4, I tried the familiar `String.toUpperCase()` name without first seeing that particular member in an example; the first check and run accepted it. For task 7, I queried `api java.time.LocalDate --json` before writing the call; the saved response identifies `of(Int32, Int32, Int32) -> LocalDate?` and `getDayOfMonth() -> Int32` as usable. The source narrows the nullable result before calling instance methods. Task 6 deliberately uses `toFloatExact()`, `divTrunc()`, and `toInt32Exact()`.

## Tool calls and evidence

The per-task counts below cover the initial phase and are shipped `bin/sprig` CLI invocations, not shell commands. `check` and `run` were each called once for tasks 1–7, 9, and the original task 10; their output is in `<task>.first-check.json`, `<task>.run.stdout`, and `<task>.run.stderr`. Task 8 additionally called `explain` and a second `check`; task 7 additionally called `api`. The shared discovery calls were `version` (1), `capabilities` (1), and `help` (10: language, classes, variants, nullability, collections, numerics, modules, then JSON help for numerics, collections, modules). Initial-phase calls totaled 23 task-specific and 12 shared discovery, or 35 before the extension.

| Task | SDK calls | Saved check evidence | Saved run evidence |
|---|---:|---|---|
| 1 | 2 | `01_hello.first-check.json` | `01_hello.run.*` |
| 2 | 2 | `02_class.first-check.json` | `02_class.run.*` |
| 3 | 2 | `03_variant.first-check.json` | `03_variant.run.*` |
| 4 | 2 | `04_nullable.first-check.json` | `04_nullable.run.*` |
| 5 | 2 | `05_collections.first-check.json` | `05_collections.run.*` |
| 6 | 2 | `06_numeric.first-check.json` | `06_numeric.run.*` |
| 7 | 3 | `07_jdk.first-check.json`, `localdate-api.json` | `07_jdk.run.*` |
| 8 | 4 | `08_invalid.first-check.json`, `08_invalid.repaired-check.json` | `08_invalid.run.*` |
| 9 | 2 | `09_module.first-check.json` | `09_module.run.*` |
| 10 original | 2 | `10_medium.first-check.json` | `10_medium.run.*` |

The first check for each task was run with `bin/sprig check <source> --json`. The final execution was `bin/sprig run <source>`. Source paths were relative to the extraction directory. The module was checked and run through `09_module/main.spr`, which imports `math.spr` relative to itself.

## Task 10 extension follow-up

The original 34-line attempt is preserved verbatim as `10_medium.first-attempt.spr`, with its original check and run evidence above. I then expanded `10_medium.spr` to 128 total lines (115 nonblank). It models a ledger with credit/debit variants, exhaustive matches, spending categories, accepted/rejected transactions, nullable summary values, an immutable history snapshot, and explicit exact integer-to-float conversion for an average. The source includes a rejected overdraft and a rejected zero credit so both validation paths execute.

The extension took **one additional check and one additional run**, with **zero repair iterations** and **no diagnostics**. Exact calls:

```text
bin/sprig check work/10_medium.spr --json
bin/sprig run work/10_medium.spr
```

The check exited 0 (`10_medium.extension-check-1.json`, empty `diagnostics`). The run exited 0, with empty `10_medium.extension-run-1.stderr`. No new SDK help/API calls were needed, and no new syntax or JVM API was invented. The extension adds 2 task-specific SDK calls, bringing the exercise to **25 task-specific and 12 shared discovery calls, 37 total**.

Observed stdout, saved in `10_medium.extension-run-1.stdout`:

```text
opening average: none
credit 100 pay: true
debit 25 food groceries: true
debit 60 rent rent: true
debit 30 travel train: false
credit 20 refund: true
debit 10 travel bus: true
credit 0 invalid: false
Ada balance=30 accepted=6 rejected=2
food=25
rent=60
travel=10
snapshot=[100, 75, 15, 35, 25]
current=[100, 75, 15, 35, 25, 30]
high=100
average=46.666666666666664
pay -> 100
groceries -> 75
rent -> 15
rejected insufficient funds
refund -> 35
bus -> 25
rejected credit amount
bonus -> 30
```
