# AGENTS.md — coding in the Sprig v0.7 design kit

This file covers work inside `spec/`, the language design kit. Repository-wide
engineering commands and rules are in the root `AGENTS.md`; use that for
compiler, runtime, test and documentation changes.

The stage-0 compiler exists and runs (`sprig check`, `sprig build`,
`sprig run`). The design kit still describes proposed semantics that the
compiler does not fully implement, so do not present a target example as a
verified capability. Before claiming behavior, check
`docs/FEATURE_STATUS_IMPLEMENTED.md` and run the compiler.

When generating Sprig code:

- Read `docs/LANGUAGE_SPEC.md`, `docs/QUICK_REFERENCE.md` and the grammar.
  Follow the grammar as **syntax** and the spec as **target semantics**. If
  they disagree, flag the discrepancy instead of inventing a feature.
- Put imports at the top of each module. Use indentation, `func`, typed
  parameters, and an explicit `-> ReturnType` for every named function and
  method (`-> Unit` for no result).
- Use `let` for a single binding and `var` for rebinding. Class and variant
  constructors take **named arguments**: `Hero(name="Ada")`,
  `Expr.Literal(value=3)`. Functions and JVM calls take positional arguments,
  and the two forms never mix in one call.
- `List[T]` and `MutableList[T]` are distinct. An immutable outer collection
  does not recursively freeze its elements.
- Use `T?` and `null` for expected absence, and `throws` with typed `catch`
  for recoverable errors.
- `variant` cases have immutable named payloads; `match` must list **all**
  cases explicitly. There is no default, wildcard or implicit fallthrough.
- Do not use pipeline operators, chained comparisons, `def`, Java modifiers,
  implicit truthiness, auto-import or `Any`.
- A grammar file does not implement a feature. Report parser acceptance, static
  checking, `javac` success and runtime behavior separately, and only claim
  tests you actually ran.
