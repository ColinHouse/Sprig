# Sprig grammar (authoritative)

`SprigLexer.g4` and `SprigParser.g4` are the only grammar files in this
repository. They describe the syntax of the Sprig v0.7 language as implemented
by the stage-0 compiler in `compiler/`.

- `SprigLexer.g4` is a lexer grammar with the `INDENT`/`DEDENT` token types.
- `SprigParser.g4` recognizes syntax only. Static types, nullability,
  exhaustiveness, collection mutability, constructor categories and JVM member
  validity are enforced after parsing by `compiler/.../sem/`.
- Indentation is turned into `NEWLINE`/`INDENT`/`DEDENT` by
  `compiler/.../front/LayoutTokenSource.java`. The grammar itself is not
  layout-sensitive.

The build regenerates the parser into `build/gen` with the pinned ANTLR 4.13.2
JAR (`scripts/build.sh`). The independent grammar smoke harness lives in
`tools/grammar-harness/` and is run by `tools/test-grammar.sh`.

Grammar audit status (2026-09-25): the grammar covers every syntax form used by
the compiler and the executable test corpus. No grammar correction was required;
the earlier duplicate under `output/grammar/` was removed during public-repo
cleanup. The only defect found was in the separate reference harness adapter
(synthetic layout tokens without a `TokenSource`), which is fixed in
`tools/grammar-harness/LayoutTokenSource.java`.
