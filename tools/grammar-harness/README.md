# Grammar smoke harness (syntax only)

This directory contains an independent, minimal layout adapter and parser
driver used by `tools/test-grammar.sh` to check the `.g4` files without the
compiler front end. It is **not** the compiler implementation and must not be
presented as one.

- `LayoutTokenSource.java` — reference adapter that converts physical newlines
  and columns into `NEWLINE`/`INDENT`/`DEDENT`.
- `ParseSmoke.java` — parses one `.spr` file and exits non-zero on lexer,
  layout or syntax errors.

The compiler has its own, diagnostic-reporting layout adapter in
`compiler/src/main/java/sprig/compiler/front/LayoutTokenSource.java`; the two
are intentionally separate so grammar tests do not depend on compiler classes.

`tools/test-grammar.sh` requires `ANTLR_JAR` and a JDK. It proves parser
acceptance/rejection only; type checking and runtime behavior are covered by
`scripts/test.sh`.
