# Agent-friendly revisions: rationale and trade-offs

> Historical Sprig v0.7 design kit. Current implementation: [feature status](../../docs/FEATURE_STATUS_IMPLEMENTED.md).

| v0.7 decision | Agent / human benefit | Cost / deliberate limit |
|---|---|---|
| `variant` sealed payload cases | Express AST/token-like data with exact fields instead of nullable bags and casts; compiler can type-check branches | New semantic construct, Java lowering and type metadata required; not claimed novel versus ADTs elsewhere |
| Exhaustive `match` statement | Compiler can flag missing handlers after new variant cases are added | A case addition can cause many deliberate diagnostics; no catch-all means explicit migrations |
| `case Type.Case as item` | Simple, stable binder; no nested destructuring or name-vs-field ambiguity | More verbose than nested patterns; binder's type and scope need semantic rules |
| Positional **or** named arguments per call | Parser catches mixed calls early; standardizes generation | Syntax alone cannot tell `Hero(5)` from `add(x=5)`; resolve by symbol category |
| Typed named funcs and methods | Call sites, IDE and agent can query signatures without analyzing bodies | Repetitive `-> Unit`; lambda expression retains limited result inference |
| No implicit coercion/truthiness | Reduces cross-language Python/Java guessing | Some extra conversions and diagnostics needed |
| Named-only generated class/variant constructors | Self-documenting initialization, independent of field ordering | Java constructors need positional JVM bridge; one consistent exception by target category |
| Canonical formatter + AST-aware tools (FUTURE) | Agents can format/check/fix with stable machine-readable diagnostics | Not magically provided by ANTLR4; must implement and benchmark |

Avoid inventing AI-only syntax. Measure whether these rules improve *first attempt parse/type-check*, *final functional correctness*, *tool calls*, *time* and *tokens* against baseline languages/versions. Without a working compiler and controlled experiment, agent-friendliness remains a **design hypothesis**.
