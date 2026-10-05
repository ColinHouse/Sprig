# Sprig VS Code extension plan and verification

Approved scope (0.2.0): installable desktop extension with syntax highlighting,
saved-file checking, explicit Run and generated Java viewing, plus formatting,
outline and workspace symbols, snippets, hover, completion, go-to-definition,
a Testing view, a language status item and project commands. Compiler
0.4.0-alpha.1+ remains the semantic authority (0.5.0-beta.1+ for the Testing
view). No compiler syntax/type redesign, LSP or Marketplace release.

Architecture: TextMate highlighting, language configuration, snippets and the
lexical outline (`src/symbols.ts`) work without a compiler or trust. Everything
semantic comes from the CLI's JSON. `src/compiler.ts` isolates
subprocess/protocol/project discovery; `src/queries.ts` caches read-only
`api`/`help`/`doctor` queries; `src/format.ts` runs `sprig fmt` on a temporary
copy; `src/testResults.ts` maps `sprig test --json`; `src/markdown.ts` renders
help, explanations and API declarations. `src/language.ts`, `src/testing.ts`
and `src/status.ts` adapt them to VS Code, and `src/extension.ts` owns
lifecycle and commands.

- [x] Tokenization tests first: all lexer keywords, boundaries, comment/string
  isolation, numeric exponents, declaration names, generics, variants and match.
- [x] CLI adapter tests first: spaced/non-ASCII/metacharacter paths, project roots,
  missing compiler, static errors, actual JVM Run and Java-only generated paths.
- [x] Extension integration: trust gates, per-project diagnostics ownership,
  canceled/stale checks, dirty-file handling, observable process errors, commands.
- [x] Real Extension Host test: activation, diagnostics repair, Run and Java view.
- [x] Package VSIX, inspect contents, install in an isolated VS Code profile.
- [x] Document commands/configuration/limitations and run canonical repo gates.
- [x] 0.2.0: query commands without `exitCode` (`api`, `help`, `doctor`) and plain
  `init` through the shared process runner.
- [x] 0.2.0: lexical outline scanner tests (imports, declarations, members,
  positions, references inside strings/comments).
- [x] 0.2.0: Markdown rendering, cached queries and formatting against the real CLI.
- [x] 0.2.0: every snippet expands to code that passes `sprig check --syntax-only`.
- [x] 0.2.0: test outcomes from a real `sprig test --json` run (pass, runtime
  failure, compile-fail fixture).
- [x] 0.2.0: Extension Host coverage of symbols, hover, completion, definition,
  formatting, linked diagnostics, rendered explain/help, the Testing view and
  project commands; Restricted Mode keeps the outline and blocks compiler-backed
  features.
- [x] 0.2.0: package `dist/sprig-language-0.2.0.vsix` and install it in an
  isolated VS Code profile.

Production files: package.json and language-configuration.json register the
language/editor behavior; syntaxes/sprig.tmLanguage.json scopes source;
snippets/sprig.json holds snippets. Tests use actual TextMate/Oniguruma,
compiler and Extension Host.
