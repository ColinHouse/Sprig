# Examples

Every file under `examples/` is compiled and executed by `scripts/test.sh` on
each test run. The listings below are the real files, not retyped copies.

## Hello world

<<< @/../examples/hello.spr

```text
Hello, Ada!
```

## FizzBuzz

<<< @/../examples/fizzbuzz.spr

```text
1
2
Fizz
4
Buzz
Fizz
7
8
Fizz
Buzz
11
Fizz
13
14
FizzBuzz
```

<details>
<summary>Show all 15 lines</summary>

The program iterates `range(1, 16)` and prints one value per line, replacing
multiples of 3 with `Fizz`, of 5 with `Buzz` and of 15 with `FizzBuzz`.

</details>

## Shapes: classes, variants and match

<<< @/../examples/shapes.spr

```text
circle r=2.0cm
area=12.56636
rect 3.0x4.0cm
area=12.0
radius=2.0
drawn=2
```

This example combines a class with defaults, a sealed `variant`, an `enum`,
two exhaustive `match` statements, a nullable return type and immutable
collections.

## Word counting

<<< @/../examples/word_count.spr

```text
the: 3
quick: 1
brown: 1
fox: 2
jumps: 1
over: 1
lazy: 1
dog: 1
sorted keys: [brown, dog, fox, jumps, lazy, over, quick, the]
```

## Numerical precision policy

<<< @/../examples/numeric_science.spr

```text
2.0
0.3
true
```

The mean uses binary64 `Float`; the money-like value uses `Decimal`, so
`0.1 + 0.2` is exactly `0.3` instead of a binary approximation. The last line
makes the floating-point error explicit instead of hiding it.

## Larger programs in the test suite

- `tests/runtime/` — eighteen end-to-end programs with golden stdout:
  arithmetic, functions, control flow, classes, variants, enums, nullability,
  errors, collections, lambdas, strings, modules, assertions, formatting and
  JVM interop.
- `tests/visitor/ast_visitor.spr` — a small AST interpreter with four visitor
  classes (printer, evaluator, simplifier, size counter) written entirely in
  Sprig; adding a variant case breaks visitors that miss it.
- `tests/visitor/mini_pipeline.spr` — a bootstrap-slice experiment.
- `tests/numeric/` — checked arithmetic, conversion and precision tests with
  an independent Python oracle.
