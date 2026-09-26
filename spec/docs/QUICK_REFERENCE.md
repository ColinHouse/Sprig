# Sprig v0.7 quick reference (target behavior)

> Historical Sprig v0.7 design kit. Current implementation: [feature status](../../docs/FEATURE_STATUS_IMPLEMENTED.md).

> This is a design-kit sample of **target** v0.7 semantics. The stage-0
> compiler implements a subset; see `docs/FEATURE_STATUS_IMPLEMENTED.md` for
> what is actually runnable and `docs/KNOWN_LIMITATIONS.md` for the gaps.
> In particular, unannotated Java reference results are nullable in the
> implementation, so `Date.now().getYear()` requires a null check.

```sprig
import java.time.LocalDate as Date

class Hero:
    let name: String
    var health: Int = 100

    func heal(amount: Int) -> Unit:
        health += amount

let hero = Hero(name="Ada")
hero.heal(5)
let year = Date.now().getYear()

let words: List[String] = ["a", "b"]
let buffer: MutableList[String] = ["a", "b"]
buffer.append("c")

variant Expr:
    Literal(value: Int)
    Add(left: Expr, right: Expr)

func eval(expr: Expr) -> Int:
    match expr:
        case Expr.Literal as node:
            return node.value
        case Expr.Add as node:
            return eval(node.left) + eval(node.right)

let tree = Expr.Add(
    left=Expr.Literal(value=1),
    right=Expr.Literal(value=2)
)
print(eval(tree))
```

Rules: named functions and methods require parameter and return types. Class and variant cases use named constructors only. A `match` must cover every case; compilation must reject missing cases and duplicate case branches. The parser only checks syntax, not exhaustiveness or JVM method validity. `Int` is a signed 64-bit integer in the current draft; non-null `String` cannot hold `null`. See `LANGUAGE_SPEC.md`.
