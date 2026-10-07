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
That one error does not stop name resolution and type checking: the branches
that exist give the expression its type, the rest of the program is still
checked in the same round, and the language server keeps hover, navigation and
completion while the `else` is being written. The program does not compile
until it is there.

At the start of a statement, `if` is always the `if` statement, whose `else`
stays optional. Everywhere an expression `match` is accepted, `if` is an
expression: binding and assignment initializers (including `+=` and class
field defaults), `return`, `throw`, an expression lambda's body, and a branch
of another `if` or `match` expression. Indentation blocks inside grouping
delimiters (calls, lists, maps, parentheses) are not supported by the layout
adapter, so an `if` expression cannot be written there, and it is not an
operand of an operator. Bind it to a `let` first, then use the name; for a
call argument that depends on a lambda's parameter, pass a named function, or
bind the lambda and pass its name:

```sprig
func size_label(n: Int) -> String:
    return if n > 1:
        "big"
    else:
        "small"

let parity = fn(n: Int) => if n % 2 == 0:
    "even"
else:
    "odd"

print([1, 2, 3].map(size_label))   # [small, big, big]
print([1, 2, 3].map(parity))       # [odd, even, odd]
```

This is a deliberate rule for the 0.8 language: inside a call the layout
adapter ignores line breaks, and an `if` expression's branches each need a
line of their own. The compiler reports each of these shapes with a targeted
`SPR-SYNTAX-ERROR`, as it does a one-line `if c: a else: b`, a branch with
several lines, Python's `a if c else b` and C's `c ? a : b`.

Conditions must be `Bool` (`SPR-TYPE-CONDITION`); there is no truthiness.
Narrowing is exactly the `if` statement's, so rewriting one form as the other
never changes what type-checks. A branch sees its own condition true and every
earlier condition false, its own condition included; the `else` sees every
condition false. After `if value == null:`, an `elif value > 100:` branch and
the `else` use `value` as non-null. A name declared `T?` may still be compared
with `null` while it is narrowed (`elif value != null and value > 100:`); the
check is redundant and accepted. Only immutable bindings narrow.

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
never apply. No closures or temporaries are introduced. A chain of more than
64 conditions is emitted as a Java switch expression over one block, where each
condition is an `if` that yields its branch's value: javac parses a nested
conditional recursively and overflows at about 1,500 levels, while the block
form has no depth, the same evaluation order and the same conversions.
