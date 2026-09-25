# Stage-1 frontend probe: observed language gaps

This is an evidence log, not a syntax proposal. Alpha.2 leaves the v0.7
grammar unchanged. `examples/stage1_frontend_probe/frontend.spr` (about 490
Sprig lines) reads UTF-8 input through the Java host, lexes identifiers,
numbers, strings and punctuation, emits layout tokens, parses bindings,
printing, `if` blocks and arithmetic precedence, builds variant ASTs,
records source ranges, checks a basic symbol table, and pretty-prints via
exhaustive visitors. Four fixtures include malformed source; `tests/bootstrap`
checks its golden output, javac/JVM execution, and added-case exhaustiveness.
This is still a **subset probe**, far smaller than a full stage-1 compiler.

| Need | Current workaround | Cost / evidence | Syntax proposal |
|---|---|---|---|
| UTF-8 source IO | `HostFiles.readUtf8` through explicit JVM interop | The probe catches `IOException` and narrows the conservative nullable result; functional, with extra code | None; a host service is sufficient |
| Efficient text assembly | The probe uses Sprig string concatenation | Fine for four small fixtures; large-input allocation cost has not been measured | None yet |
| Generic token/symbol utilities | Use concrete `List[Token]`, `Map[String, Symbol]` | User-defined generic abstractions remain unavailable; a frontend probe must measure repetition | Defer until measured |
| Multiline visitors | Named recursive functions with exhaustive match | Expression lambdas cannot contain statements | Defer until measured |
| Conditional expression | `var` followed by an `if` assignment | One attempted `let prefix = if ... then ... else ...` was rejected; a three-line `if` worked in the printer | No proposal: workaround cost is small |
| Lexer recovery | `Expr.Invalid` and `Stmt.Invalid` variants plus a Reporter | The probe stays alive on missing names, bad indentation, and strings, but recovery logic is manual | No proposal: compiler library helpers may be enough |

Every future syntax proposal must include real Sprig code, workaround size,
parser/typechecker/codegen/IDE cost, and its impact on agent predictability.
No proposal is justified solely by familiarity with another language.
