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

## Query before guessing

After building, use `bin/sprig` (`bin\sprig.cmd` on Windows):
`capabilities --json`, `help <topic> --json`, `api <Java.Class> --json`,
`doctor --json`, and `explain <SPR-CODE> --json`. Query project/dependency
state with `project --json` and `deps --json` inside a project.

## Canonical contributor gate

```bash
./scripts/verify.sh
# Windows: py -3 scripts/verify.py
```

Requires JDK 17+, Python 3.12+, Node.js 20+/npm and Git. It runs portable
build, full compiler/JVM tests, independent grammar tests, executed docs and
the VitePress production build, and editor tokenization/CLI/package checks. First use downloads pinned tools/libraries.
Archive smoke, all-OS/JDK CI, checksum and publication gates remain release work.

| Focus | Fast command after build | Evidence |
|---|---|---|
| Lexer/layout/parser | `python3 tools/test-grammar.py` | parser only |
| Types/flow/diagnostics | `python3 scripts/check_cases.py .` | static checking |
| Generation/runtime | `python3 tests/correctness/check_correctness.py` | Java/JVM |
| CLI/JVM query tools | `python3 tests/agent_tooling/check_tooling.py` | subprocess/API fixtures |
| Projects/dependencies | `python3 tests/project_deps/check_deps.py` | lock/cache/project behavior |
| Maven graph/cache | `python3 tests/maven/check_resolver.py` | offline effective-model fixtures |
| Standard modules/showcases | `python3 scripts/test-stdlib.py` / `python3 scripts/test-showcases.py` | real programs on JVM |
| VS Code editor | `python3 scripts/check-editor.py` | actual TextMate/CLI/JVM and VSIX; host tests run separately |
| Docs | `python3 scripts/check-docs.py` | snippet JVM + website |

Generated `build/`, `bin/`, `website/.vitepress/dist/` and
`website/generated/` are not committed. See `CONTRIBUTING.md` for scope and PR rules.

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
- `sprig api`, `capabilities`, `doctor`, and topic help are available; query
  `capabilities --json` for the checkout's dependency and feature support.
  Publishing/registry, LSP remains future work.
- This milestone does not authorize grammar, type, numeric, nullability,
  generic or effect redesign. Open a `design-required` issue with a motivating
  program before changing those contracts.
- Apache-2.0 and the public repository are established. Do not create a tag,
  release, or deployment claim without a verified release build and owner
  publication decision.
- AI-assisted changes follow `AI_DISCLOSURE.md`: describe significant AI
  assistance and what you verified yourself.

## Release support

Linux/macOS × JDK17/26 and Docs are required. Windows is an experimental
non-blocking preview. `@std` is a reserved bundled package; never add a manifest
dependency named std. `build --emit-java-only` performs the static pipeline and
writes Java without javac. Do not claim a development catalog is a published SDK.

## VS Code adapter

`editors/vscode/` is a TypeScript desktop extension. Highlighting uses the real
lexer contract; compiler semantics remain in Java. Keep editor dependencies and
lockfile separate from the website. Run `npm test` and `npm run package` there;
`npm run test:host` additionally runs a real isolated VS Code Extension Host.
Never execute compiler commands in an untrusted workspace. Package locally; do
not publish Marketplace or change compiler version for an editor-only change.
