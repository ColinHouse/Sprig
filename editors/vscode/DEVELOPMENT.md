# Sprig VS Code extension plan and verification

Approved scope: installable desktop extension with syntax highlighting, saved-file
checking, explicit Run and generated Java viewing. Compiler 0.3.0-alpha.1+ remains
the semantic authority. No compiler syntax/type redesign or Marketplace release.

Architecture: TextMate highlighting and language configuration work without a
compiler or trust. TypeScript extension invokes a configured/PATH/source SDK CLI
in the nearest project directory. JSON diagnostics are adapted to VS Code.

- [x] Tokenization tests first: all lexer keywords, boundaries, comment/string
  isolation, numeric exponents, declaration names, generics, variants and match.
- [x] CLI adapter tests first: spaced/non-ASCII/metacharacter paths, project roots,
  missing compiler, static errors, actual JVM Run and Java-only generated paths.
- [x] Extension integration: trust gates, per-project diagnostics ownership,
  canceled/stale checks, dirty-file handling, observable process errors, commands.
- [x] Real Extension Host test: activation, diagnostics repair, Run and Java view.
- [x] Package VSIX, inspect contents, install in an isolated VS Code profile.
- [x] Document commands/configuration/limitations and run canonical repo gates.

Production files: package.json and language-configuration.json register the
language/editor behavior; syntaxes/sprig.tmLanguage.json scopes source;
src/compiler.ts isolates subprocess/protocol/project discovery;
src/diagnostics.ts maps compiler coordinates; src/extension.ts owns VS Code
lifecycle/commands. Tests use actual TextMate/Oniguruma, compiler and Extension Host.
