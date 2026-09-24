# Examples

Every file runs with the stage-0 compiler:

```bash
../bin/sprig run hello.spr
```

| File | Shows |
|---|---|
| `hello.spr` | typed functions, string concatenation |
| `fizzbuzz.spr` | `if`, `%`, `range`, loops |
| `shapes.spr` | classes with defaults, variants, enums, exhaustive match, nullables |
| `word_count.spr` | `MutableMap`, `split`/`trim`/`toLowerCase`, snapshot semantics |
| `numeric_science.spr` | explicit exact conversion for a mean, Decimal addition, approximate Float comparison |

Larger programs written in Sprig live in `../tests/visitor/`:

* `ast_visitor.spr` — printer, interpreter, simplifier and counter visitors
  over an `Expr`/`Stmt` AST.
* `mini_pipeline.spr` — lexer + recursive-descent parser + evaluator,
  i.e. a bootstrap-feasibility slice.
* `nonexhaustive.spr` — the variant-evolution regression: adding a case
  without updating a match is a compile error.
