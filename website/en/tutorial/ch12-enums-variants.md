# 12. Enums, variants and match

In this chapter you will learn:

- how to use an `enum` for values that can only be one of a fixed set of names, such as a direction or a season;
- how to use a `variant` when each case carries data, such as "a circle has a radius";
- how to handle every case at once with `match`; miss one and the compiler stops you;
- how the expression form and the statement form of `match` differ;
- a trap beginners hit: `var v = Shape.Dot` has type `Shape.Dot`, not `Shape`;
- recursive variants: a variant whose fields are the variant itself, which is how you build a tree.

## 12.1 enum: a fixed set of names

A value that can only be one of a few things is everywhere in programs: a direction, a weekday, a state, a kind of command. Strings would allow typos, inconsistent capitalization, and nobody can list the possible values. An `enum` lists them:

<<< @/snippets/book/ch07_enum_match.spr

```text
East
North
```

- `enum Direction:` declares an enumeration type; the indented names below it are its members: `North`, `East`, `South`, `West`. You use them as `Direction.North`, and you never worry about typos — a misspelled name is `SPR-NAME-UNRESOLVED`.
- `turn_right(d: Direction)` takes a direction and returns the direction after a right turn.
- `return match d:` is a **match expression**: `match` sits where a value is needed (after `return`), each `case` holds a single expression, and the whole `match` evaluates to that branch's value.
- The four `case Direction.North:` branches cover every possibility. There is no `else` and no default branch.
- The first line, `East`, is `Direction.North` after a right turn. The second, `North`, is `Direction.West` turned right, back to north.

A `case` can also hold several statements; then the `match` is a **statement**, and each branch has to `return` its own value. The `area` function in 12.2 is written that way.

## 12.2 variant: cases that carry data

Sometimes each case also carries data: a shape is either a circle (with a radius), a rectangle (with a width and height), or a dot. A `variant`'s cases can declare fields:

<<< @/snippets/book/ch07_variant.spr

```text
3.14159
6.0
0.0
true
```

- `variant Shape:` declares the type `Shape` with three cases: `Circle(radius: Float)`, `Rect(width: Float, height: Float)` and `Dot`. A case without fields (`Dot`) is written like an enum member.
- Constructing a case with fields writes the field names: `Shape.Circle(radius=1.0)`, just like creating a class object.
- `case Shape.Circle as c:` binds the matched `Shape` value to `c`, so the branch can use `c.radius`. When you do not need the fields, leave out `as`, as in `case Shape.Dot:`.
- The name bound by `as` is read-only: writing `c = ...` inside gives `SPR-NAME-LET-ASSIGN`, whose message says to declare it with `var` (you rarely need to; the binding only names the value).
- `match shape:` at the top of a function is the **statement** form; each branch uses `return`. The three branches compute `3.14159`, `6.0` and `0.0`.
- The last line, `true`: a variant compares by **content**. `Shape.Rect(width=2.0, height=3.0)` equals another `Rect` with the same fields. That is the opposite of the classes in the previous chapter, which compare by identity.
- Because all cases share the type `Shape`, they fit in one `List[Shape]` and can be passed as arguments.

::: tip Coming from another language?
- `enum` is like an enum elsewhere; `variant` is Rust's enum, Swift's enum with associated values, Kotlin's sealed class, or a TypeScript discriminated union.
- Construction writes field names, as with classes; Python dataclasses and Java records express part of this, but their type is not "one of a fixed set".
- There is no fall-through as in `switch`, and no `default`; the next section shows what happens when a case is missing.
:::

## 12.3 match must be exhaustive

`match` has no `default`, no `else` and no `_` fallback: every case is written out. Miss one and the compiler names it:

<<< @/snippets/book/ch07_nonexhaustive.spr

```text
SPR-MATCH-NONEXHAUSTIVE [FLOW] main.spr:7:5: Missing case: Shape.Dot
  hint: Add 'case Shape.Dot:' (there is no default case)
```

- the code is `SPR-MATCH-NONEXHAUSTIVE`, at the line of the `match` statement;
- the message names the missing `Shape.Dot`;
- the hint is the fix: add `case Shape.Dot:`.

The rule pays off as the program grows: imagine adding a `Triangle` to `Shape` a month from now. Every `match` that forgot it is pointed out at compile time, one by one, instead of failing at run time.

For the same reason, `match` is only for enums and variants — types whose cases you can list. It cannot test whether an object is some class: Sprig has no type tests.

## 12.4 Deliberate mistake: the singleton case type

An easy trap: the value `Shape.Dot` has the type **`Shape.Dot`** (that one case), not the whole `Shape`. The compiler infers a narrower type than you expected.

<<< @/snippets/book/ch12_case_trap.spr

```text
SPR-TYPE-ASSIGN [TYPE] main.spr:6:5: Type mismatch in assignment (expected Shape.Dot, actual Shape.Circle)
SPR-FLOW-UNREACHABLE [FLOW] main.spr:12:10: Case 'Shape.Dot' is impossible for statically known Shape.Circle
```

Read both errors together:

- Line 6: `var v = Shape.Dot` makes `v` of type `Shape.Dot`, so the next line assigning `Shape.Circle(...)` reports a type mismatch: `expected Shape.Dot, actual Shape.Circle`.
- Line 12: `let s = Shape.Circle(radius=1.0)` makes `s` of type `Shape.Circle`. The compiler knows `s` can only be a circle, so `case Shape.Dot:` can never be reached: `SPR-FLOW-UNREACHABLE`.

The fix is simple: **when a variable may later hold another case, write the type as `Shape`**. The annotation tells the compiler "I want the whole variant, not this one case":

<<< @/snippets/book/ch12_case_fixed.spr

```text
Circle(radius=1.0)
2.0
```

After `var v: Shape = Shape.Dot`, `v` can take `Shape.Circle(...)` too; after `let s: Shape = Shape.Circle(radius=2.0)`, a `match` with both cases is fine and nothing is unreachable.

Inference itself is not wrong — when a value really is always that one case, the precise type is better. Just remember: **a variable you construct once and later reassign to another case needs `: Shape`**.

## 12.5 Recursive variants: a variant containing itself

A variant's field can have the variant's own type. That is how you build trees, expressions and nested structures. Here is an expression tree with integers, addition and negation:

<<< @/snippets/book/ch12_recursive.spr

```text
-1
7
```

- `Num(value: Int)` is a leaf; the fields of `Add(left: Expr, right: Expr)` and `Neg(value: Expr)` are `Expr` again, so the tree can be as deep as you like.
- `eval(expr: Expr) -> Int` turns the tree into an integer. `case Expr.Add as a: return eval(a.left) + eval(a.right)` recurses into both subtrees — the recursion from chapter 7 again.
- `let tree: Expr = ...` builds a tree by hand: 2 + (-3).
- The first `print` is `-1`; the second is `7`.

Follow `eval(tree)` through its two levels of recursion:

| step | evaluation | result |
|---|---|---|
| 1 | `eval(Add(Num 2, Neg(Num 3)))` | `eval(Num 2) + eval(Neg(Num 3))` |
| 2 | `eval(Num 2)` | `2` |
| 3 | `eval(Neg(Num 3))` | `-eval(Num 3)` = `-3` |
| 4 | `2 + (-3)` | `-1` |

## 12.6 No fallback, no guards

Two things you may expect but that Sprig deliberately leaves out:

**No `case _:`.** A wildcard would make "missed a case" possible again:

<<< @/snippets/book/ch12_wildcard.spr

```text
SPR-MATCH-UNKNOWN-CASE [TYPE] main.spr:7:14: Match case must be written as Type.Case
  hint: match is for enums and variants; there are no type tests on class or contract values. A closed set of types is a variant (declare one with a case per type and match on it); an open set is a contract (call its methods instead of testing the type).
```

The hint mentions contract classes, which are chapter 16's topic; for now keep the first half: a `case` must be written `Type.Case`.

**No `if` guards.** Some languages allow `case Shape.Circle if c.radius > 1.0:`. Sprig does not; the condition is a plain `if` inside the branch body:

<<< @/snippets/book/ch12_guard.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:7:30: A match case has no 'if' guard
  hint: Match the case, then put 'if condition:' inside the case's body.
```

There is also **no multi-case branch**: `case Shape.Circle, Shape.Dot:` is not allowed; each case gets its own line.

## Summary

- An `enum` lists names; a `variant` lists cases with fields. Construction writes field names, and the `as` binding is read-only.
- `match` handles every case, must be exhaustive, and has no fallback, no guards and no type tests.
- Used where a value is needed, `match` is an expression (one expression per branch); as a statement, branches can hold several lines and `return` themselves.
- Variants compare by content and can be recursive: a field may have the variant's own type.
- The trap: a single case's value has the singleton case type; annotate with `: Shape` when the variable must hold several cases.

## Exercises

**Exercise 1 (enum and match expression).** Define `enum Season: Spring, Summer, Autumn, Winter` and write `is_warm(s: Season) -> Bool` returning `true` for spring and summer and `false` for autumn and winter, using a match expression.

::: details Answer
<<< @/snippets/book/ch12_ex1.spr

```text
true
false
```
:::

**Exercise 2 (a variant with data).** Define a `Temperature` with cases `Celsius(degrees: Float)` and `Fahrenheit(degrees: Float)`, and write `to_celsius` to convert both. Check that 20°C stays 20.0 and 212°F becomes 100.0.

::: details Answer
<<< @/snippets/book/ch12_ex2.spr

```text
20.0
100.0
```
:::

**Exercise 3 (complete the match).** The `describe` below misses one case. Run the compiler to see what it says, then add the case so the program prints `dot`: `variant Shape` has `Circle(radius: Float)`, `Rect(width: Float, height: Float)` and `Dot`, and `describe` handles only the first two.

::: details Answer
<<< @/snippets/book/ch12_ex3.spr

```text
dot
```
:::

**Exercise 4 (recursive variant).** Define `variant Tree: Leaf(value: Int), Node(left: Tree, right: Tree)` and write `total(tree: Tree) -> Int` summing all leaves. Build a tree with three leaves; the total is 6.

::: details Answer
<<< @/snippets/book/ch12_ex4.spr

```text
6
```

`total` recurses into both subtrees for a `Node` and returns `value` for a `Leaf`; the three leaves are 1 + 2 + 3.
:::

Next chapter: [Values that may be missing](/en/tutorial/ch13-nullable).
