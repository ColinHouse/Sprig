# Examples

The tutorials below are executable documentation: each program lives under
`website/snippets/tutorial/`, and the documentation gate runs it and compares
its output with the checked `.out` oracle on every verification. The listings
are the real files, not retyped copies; the Chinese and English pages include
the same Sprig sources. For programs that build or do something useful with
Sprig, see [Application examples](#application-examples).

## Hello world

<<< @/snippets/tutorial/hello.spr

<<< @/snippets/tutorial/hello.out

## FizzBuzz

<<< @/snippets/tutorial/fizzbuzz.spr

<<< @/snippets/tutorial/fizzbuzz.out

<details>
<summary>Why the output looks like this</summary>

The program iterates `range(1, 16)` and prints one value per line, replacing
multiples of 3 with `Fizz`, of 5 with `Buzz` and of 15 with `FizzBuzz`.

</details>

## Shapes: classes, variants and match

<<< @/snippets/tutorial/shapes.spr

<<< @/snippets/tutorial/shapes.out

This example combines a class with defaults, a sealed `variant`, an `enum`,
two exhaustive `match` statements, a nullable return type and immutable
collections.

## Word counting

<<< @/snippets/tutorial/word_count.spr

<<< @/snippets/tutorial/word_count.out

## Numerical precision policy

<<< @/snippets/tutorial/numeric_science.spr

<<< @/snippets/tutorial/numeric_science.out

The mean uses binary64 `Float`; the money-like value uses `Decimal`, so
`0.1 + 0.2` is exactly `0.3` instead of a binary approximation. The last line
makes the floating-point error explicit instead of hiding it.

## Application examples

`examples/` contains programs and projects with an independent purpose, not
syntax demonstrations. Start with the
[examples gallery](https://github.com/ColinHouse/Sprig/blob/main/examples/README.md):

- [mini_web](https://github.com/ColinHouse/Sprig/tree/main/examples/mini_web) —
  typed routes, JSON, OpenAPI.
- [sqlite](https://github.com/ColinHouse/Sprig/tree/main/examples/sqlite) —
  pinned Maven JDBC driver and persisted prepared SQL.
- [ledger](https://github.com/ColinHouse/Sprig/tree/main/examples/ledger) —
  reduced accounts/transactions HTTP backend with restart persistence.
- [sqlite_migrations](https://github.com/ColinHouse/Sprig/tree/main/examples/sqlite_migrations) —
  ordered, transactional SQLite migrations.
- [json_select](https://github.com/ColinHouse/Sprig/tree/main/examples/json_select) —
  multi-file JSON CLI.
- [agent_tools](https://github.com/ColinHouse/Sprig/tree/main/examples/agent_tools) —
  Sprig-written compiler API and diagnostic tools.
- [test_runner](https://github.com/ColinHouse/Sprig/tree/main/examples/test_runner) —
  five ordinary project tests: runtime, table, temporary file, child process
  and expected compiler diagnostic (current source checkout).
- [showcases](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases) —
  repository auditor, Maven-backed utility and source analyzer.

## Real JVM ecosystem (Fabric dogfood)

A real Fabric/Loom singleplayer mod dogfood validated the framework path:
Gradle/Loom owns the Minecraft/Fabric classpath, Sprig `conform`s directly to
lifecycle/tick/player callbacks, one narrow Java adapter covers the Brigadier
wildcard builder, and the singleplayer create/command/autosave/quit/re-enter
restore path plus the final build all passed.

| Metric | Value | Note |
|---|---:|---|
| Handwritten Sprig feature code | 440 lines | model, persistence, entrypoint |
| Handwritten Java glue | 52 lines | one Brigadier builder + one action interface |
| Directly conformed callbacks | lifecycle, tick, join/disconnect | — |
| Narrow wildcard adapters | 1 | Brigadier `then` |

These are **one project's local measurements, not a general ratio**; larger
third-party APIs will raise the Java share. The reusable wiring, verified
versions and packaging checklist are in
[Fabric / JVM framework integration](/en/guide/fabric).

## Larger programs in the test suite

- `tests/runtime/` — nineteen end-to-end programs with golden stdout:
  arithmetic, functions, control flow, classes, variants, enums, nullability,
  errors, collections, lambdas, strings, modules, assertions, formatting and
  JVM interop.
- `tests/visitor/ast_visitor.spr` — a small AST interpreter with four visitor
  classes (printer, evaluator, simplifier, size counter) written entirely in
  Sprig; adding a variant case breaks visitors that miss it.
- `tests/visitor/mini_pipeline.spr` — a bootstrap-slice experiment.
- `tests/numeric/` — checked arithmetic, conversion and precision tests with
  an independent Python oracle.
