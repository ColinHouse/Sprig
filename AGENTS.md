# AGENTS.md — working in the Sprig repository

Sprig is an indentation-based, statically typed JVM language implemented by a
Java stage-0 compiler. This file is the operating guide for agents and
contributors who change the repository. It is not a language tutorial.

## Source of truth

| Area | Authoritative file(s) |
|---|---|
| Syntax | `grammar/SprigLexer.g4`, `grammar/SprigParser.g4` |
| Layout (INDENT/DEDENT) | `compiler/src/main/java/sprig/compiler/front/LayoutTokenSource.java` |
| Static semantics | `compiler/src/main/java/sprig/compiler/sem/` |
| Code generation | `compiler/src/main/java/sprig/compiler/gen/` |
| Runtime | `runtime/src/main/java/sprig/runtime/` |
| Numeric contract | `docs/NUMERIC_SEMANTICS.md` |
| Implemented features | `docs/FEATURE_STATUS_IMPLEMENTED.md` |
| Diagnostic codes | `docs/DIAGNOSTIC_CODES.md` |
| Language design kit | `spec/docs/LANGUAGE_SPEC.md` (target semantics; not fully implemented) |

If the design kit and the compiler disagree, do not guess: preserve the
disagreement as an explicit issue or document the implemented behavior under
`docs/`.

## Commands that must pass

```bash
./scripts/build.sh
./scripts/test.sh
ANTLR_JAR="$PWD/tools/antlr-4.13.2-complete.jar" ./tools/test-grammar.sh
./scripts/check-docs.sh          # snippet execution + VitePress production build
```

`scripts/build.sh` downloads the pinned ANTLR 4.13.2 JAR when it is missing and
verifies its SHA-256. Generated `build/`, `bin/`, `website/.vitepress/dist/`
and `website/generated/` are not committed.

## Rules for language changes

A language feature is not implemented by grammar acceptance alone. A complete
change touches, as applicable:

1. `grammar/*.g4` only if the syntax changes (many features need no grammar
   change; numeric ranges and nullability are examples).
2. Name resolution / type checking / flow analysis in `compiler/.../sem/`.
3. Java generation and, when needed, the runtime in `runtime/`.
4. A stable diagnostic code (`compiler/.../diag/Codes.java` and
   `docs/DIAGNOSTIC_CODES.md`).
5. Regression tests: `tests/semantics/cases.json` for static errors,
   `tests/runtime/` with golden `.out` for behavior, and `acceptance/` cases
   for independently written checks.
6. Documentation: update `docs/` and, if user-visible, the VitePress pages
   and snippets in `website/`.

## Testing rules

- Do not update a golden `.out` file or weaken an assertion to make a failure
  disappear. First determine whether the source, the expectation or the
  implementation is wrong.
- Distinguish evidence levels when reporting results: parser acceptance,
  static checking, `javac` success, JVM runtime behavior.
- Documented examples are executed by `tools/verify-doc-snippets.py`; keep
  snippets compiling and their `.out` files current.
- Only claim tests you actually ran.

## Boundaries

- Do not edit `spec/` design documents unless the change explicitly concerns
  the design kit.
- Do not commit generated code, class files, the ANTLR JAR, local paths,
  credentials or personal configuration.
- Do not present proposed tooling (`sprig api`, LSP, `sprig fmt`, package
  manager) as implemented.
- Do not create tags, releases or deployment claims before the project owner
  confirms the license and repository destination.
- AI-assisted changes follow `AI_DISCLOSURE.md`: describe significant AI
  assistance and what you verified yourself.
