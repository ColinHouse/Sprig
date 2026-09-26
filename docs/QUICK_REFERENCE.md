# Implemented quick reference (v0.2.0-alpha.1 development)

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
`List[T]`/`Map[K,V]` are read-only; mutable counterparts are separate.
`Int` is checked signed 64-bit, `Int32` checked signed 32-bit, `Float` is IEEE
binary64, and `Float32` binary32. No implicit lossy numeric conversion occurs.
See `sprig help numerics` for syntax and `NUMERIC_SEMANTICS.md` for details.

The v0.7 design kit in `spec/` includes unimplemented targets. Check
`FEATURE_STATUS_IMPLEMENTED.md` and `KNOWN_LIMITATIONS.md` before relying on
an advanced feature.
