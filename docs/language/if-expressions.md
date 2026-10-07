# If expressions

The `if` statement remains the control-flow form. An `if` expression chooses
one of several values, with exactly one expression per branch:

```sprig
let label = if count == 1:
    "item"
elif count < 10:
    "a few items"
else:
    "many items"
```

`if cond:` is followed by its value on an indented line, then any number of
`elif cond:` branches and a mandatory `else:` branch, each with one expression
on its own indented line. `elif` and `else` line up with the line the
expression starts on. A missing `else` is `SPR-SYNTAX-ERROR` ("An if expression
needs an else branch"); without a value to produce, write an `if` statement.

At the start of a statement, `if` is always the `if` statement, whose `else`
stays optional. Everywhere an expression `match` is accepted, `if` is an
expression: binding and assignment initializers (including `+=` and class
field defaults), `return`, `throw`, an expression lambda's body, and a branch
of another `if` or `match` expression. Indentation blocks inside grouping
delimiters (calls, lists, maps, parentheses) are not supported by the layout
adapter, so an `if` expression cannot be written there, and it is not an
operand of an operator. Bind it to a `let` first, then use the name; for a
call argument that depends on a lambda's parameter, bind the lambda. The
compiler reports each of these shapes with a targeted `SPR-SYNTAX-ERROR`, as
it does a one-line `if c: a else: b`, a branch with several lines, Python's
`a if c else b` and C's `c ? a : b`.

Conditions must be `Bool` (`SPR-TYPE-CONDITION`); there is no truthiness.
Narrowing is exactly the `if` statement's, so rewriting one form as the other
never changes what type-checks. A branch sees its own condition true. Without
an `elif`, the `else` branch also sees the `if` condition false: after
`if value == null:`, the `else` branch uses `value` as non-null. An `elif` sees
only its own condition, never the earlier ones false, and neither does an
`else` that follows an `elif`; check the binding again in that condition, as
in `elif value != null and value > 100:`. Only immutable bindings narrow.

Result typing is the expression-match typing. An expected type (an annotation,
an assignment target, a return type or a lambda's expected result) checks every
branch using ordinary Sprig assignability, including contextual numeric
literals. A `String` target's `+=` joins its value instead, so an `if` or
`match` expression there has its own type, as `label += 2` joins an `Int`.
Without context, the first non-null branch establishes the type;
other non-null branches must be assignable to it (`SPR-TYPE-MISMATCH`, or the
specific null, numeric and callable codes). Two cases of one variant therefore
need the variant on the target: `let shape: Shape = if ...`. Null branches make
that type nullable under ordinary `T?` rules. All-null results require an
annotation (`SPR-TYPE-INFER`). There is no common-supertype/union inference and
no new numeric promotion. A branch without a value (`Unit`) is rejected
(`SPR-TYPE-UNIT`): use an `if` statement for side-effect-only branching.

Conditions are evaluated in source order, each at most once, until one is
true; only the chosen branch is evaluated, after its condition. Branches that
are not chosen never run, so they cannot fail. Throws propagate from every
condition and branch conservatively, as for a call written in that position.

Java generation emits a Java conditional expression, nested for each `elif`.
Every branch is first converted to the Java type of the Sprig result, so both
operands of each `?:` have that one type: Java's rules for mixed operands,
which promote numbers and unbox an `Integer` or `Long` (throwing on `null`),
never apply. No closures or temporaries are introduced.
