# Implemented quick reference (v0.7.1-beta.1)

`sprig help <topic> --json` is the versioned machine-readable reference.
`sprig capabilities --json` is the implemented feature inventory. This page
shows a few valid forms; it does not override those commands or the numeric
contract in `NUMERIC_SEMANTICS.md`.

```sprig
func add(a: Int, b: Int) -> Int:
    return a + b

class Hero:
    let name: String
    var health: Int = 100

let hero = Hero(name="Ada")
hero.health += 1
let label = "health " + hero.health  # + joins text with any non-null value
let mood = if hero.health > 50:  # if chooses a value; else is required
    "fine"
else:
    "hurt"

variant Expr:
    Literal(value: Int)
    Add(left: Expr, right: Expr)

func eval(expr: Expr) -> Int:
    match expr:
        case Expr.Literal as node:
            return node.value
        case Expr.Add as node:
            return eval(node.left) + eval(node.right)

print(eval(Expr.Add(left=Expr.Literal(value=1), right=Expr.Literal(value=2))))
```

Every named function/method has typed parameters and an explicit `->` result.
Methods with no result use `-> Unit`. Imports come first. `let` binds once;
`var` permits rebinding. Local types may be inferred. Constructors for Sprig
classes and variant cases use named arguments; functions and JVM methods use
positional arguments. `match` lists every case and has no wildcard.

Use `T?` for expected absence and narrow with `if value != null` before use.
The right side of a short-circuit `and`/`or` and the guarded block see the
narrowing: `if box != null and box.value > 0:` narrows `box` for `box.value`,
and `if text == null or text.length() == 0:` narrows `text` for `text.length()`.
Without an `elif`, the `else` of `if value == null:` sees `value` as non-null;
an `elif` sees only its own condition, so write `elif value != null and ...`.
After an early exit (`if value == null:` followed by `return`, `throw`, `break`
or `continue`), the statements below see `value` as non-null, in a function body
and at the top level alike. Only `let` bindings narrow; copy a `var` into a `let`
to check it.
`List[T]`/`Map[K,V]` are read-only; mutable counterparts are separate.
`==`/`!=` compare numbers, `Bool`, `String`, enums, variants, lists and maps by
value, and class objects by identity: two objects whose fields match are still
two objects, so compare the fields you mean (`a.id == b.id`). Nullable
`Int?`/`Int32?`/`Float?`/`Float32?`/`Bool?` comparisons are null-safe and widen
to the common type (`Int32?` → `Int?`, `Float32?` → `Float?`). A `T?` value is
not joined into a `String`; check it or give it a fallback with `or_else` first.
`for x in range(...)` counts without building a list; `range(...)` used as a
value is a `MutableList[Int]`. Binary operands evaluate left to right, each exactly
once, so `needle in haystack` evaluates `needle` first. Map indexing reads
return `V?`; `m[key] += x` requires an existing `key` and raises a catchable
`Error` when it is missing.
`Int` is checked signed 64-bit, `Int32` checked signed 32-bit, `Float` is IEEE
binary64, and `Float32` binary32. No implicit lossy numeric conversion occurs.
See `sprig help numerics` for syntax and `NUMERIC_SEMANTICS.md` for details.
Generic declarations sit in a `generic T:` block. A generic call, constructor
or variant case with a payload infers its type arguments from its arguments
(`Box(value=1)` is a `Box[Int]`, `lists.sort_by(orders, fn(o: Order) => o.cents)`
needs no `[Order, Int]`); written arguments (`Box[Int](value=1)`) still work,
all or none. Nothing is inferred from the expected type, so `lists.first([])`,
`Option[Int].None` and type positions write their arguments. See
[generics](../language/generics.md).

The v0.7 design kit in `docs/history/design-kit/` includes unimplemented targets. Check
`FEATURE_STATUS_IMPLEMENTED.md` and `KNOWN_LIMITATIONS.md` before relying on
an advanced feature.

## Observe generated Java

```text
sprig build src/main.spr --emit-java-only -d generated --json
```

Loads imports and performs ordinary static checking, writes `generated/java/`,
then stops before javac. JSON includes `javaSources`, `mainClass` and
`javacInvoked=false`; invalid sources still fail with normal diagnostics.
Normal `build` additionally compiles classes; `run --keep` retains temporary
Java for execution debugging and `run --stacktrace` restores the raw JVM stack
of an uncaught runtime failure. No extra transpile command is introduced.

Explicit facades: `import "./internal.spr" as internal`, then
`export internal.Widget`. Imports, exports, ordinary declarations/statements
appear in that order. See [module reexports](../language/module-reexports.md).

Expression `match` may initialize a binding or be returned directly. Every
case contains exactly one expression; results use explicit context or the
first non-null inferred type. Use statement match for multi-statement branches.
See [expression matches](../language/match-expressions.md).

An `if` expression chooses a value in the same positions: `if cond:`, any
`elif cond:` and a required `else:`, each followed by one expression on its own
indented line. Its result typing is the expression match's, and its branches
narrow exactly like the `if` statement's. An `if` at the start of a statement
is the `if` statement. Sprig has no `a if c else b` and no
`c ? a : b`. See [if expressions](../language/if-expressions.md).
