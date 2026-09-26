# Source outline analyzer

Sprig building developer tooling: an exhaustive recursive Expr/Stmt walker
adds function/binding outlines and unique references to the stage-1 probe.
This is a **frontend subset demonstration**, not a full Sprig compiler API.
From this directory:

```sh
sprig resolve
sprig check
sprig run
sprig run -- fixtures/function.spr
```

Default output:

```text
Sprig subset source outline
statements=4 expressions=4 functions=2 bindings=0
function identity(1 params) -> Int
function combine(2 params) -> Int
references=[value, left, right]
diagnostics=0
```

The copied `frontend.spr` preserves the existing probe logic; only its original
fixture driver is omitted. `outline.spr` traverses every closed Expr/Stmt case.
Try a malformed fixture and examine the source spans and diagnostics; output
is deterministic and contains no machine-specific absolute paths.
