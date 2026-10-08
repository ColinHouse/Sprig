# Sprig VS Code extension plan and verification

Approved scope (0.3.0): the 0.2.0 extension, now a client of the Sprig language
server. When the compiler reports `"languageServer": true` in
`sprig capabilities --json`, `sprig lsp` answers diagnostics as you type,
hover, completion, definition, references, rename, formatting and the outline;
updated compilers also advertise signature help and full semantic tokens,
registered automatically by the same client;
otherwise the 0.2.0 CLI queries do. Compiler 0.4.0-alpha.1+ remains the
semantic authority (0.5.0-beta.1+ for the Testing view). Publication to the
Visual Studio Marketplace and Open VSX is an owner action, described below. No
compiler syntax/type redesign.

Architecture: TextMate highlighting, language configuration, snippets and the
lexical outline (`src/symbols.ts`) work without a compiler or trust. Everything
semantic comes from the compiler. `src/compiler.ts` isolates
subprocess/protocol/project discovery and the Windows JVM command plan;
`src/server.ts` starts, restarts and stops one `sprig lsp` per window through
`vscode-languageclient`; `src/queries.ts` caches read-only `api`/`help`/`doctor`
queries; `src/format.ts` runs `sprig fmt` on a temporary copy;
`src/testResults.ts` maps `sprig test --json`; `src/markdown.ts` renders help,
explanations and API declarations. `src/language.ts` registers the CLI-backed
editor features while the server is not running, and the workspace symbols and
keyword help that the server does not provide. `src/testing.ts` and
`src/status.ts` adapt the rest to VS Code, and `src/extension.ts` owns
lifecycle, diagnostics ownership and commands. esbuild bundles everything into
`out/main.js`, the only code in the VSIX; `tsc` output in `out/` serves the
unit tests.

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
- [x] 0.3.0: capability detection, and the server command the extension starts
  (the direct JVM plan on Windows) speaking LSP to a real `sprig lsp`, including
  a document path outside the Windows ANSI code page.
- [x] 0.3.0: Extension Host coverage with the server: diagnostics for unsaved
  edits with linked codes and help actions, local types, member completion of a
  local, cross-module definition, references, rename, formatting, one outline
  provider at a time, no duplicate diagnostics from a manual check, restart and
  the fall back; Restricted Mode starts no server.
- [x] 0.3.0: `scripts/check-editor.py` inspects the VSIX: the bundled entry
  point and assets, without sources, maps or `node_modules`, and
  `ThirdPartyNotices.txt` matching the production dependencies in
  `package-lock.json`. After changing them, run
  `python3 scripts/check-editor.py --write-notices`.
- [x] Compiler integration: Extension Host checks parameter hints for an
  imported function while its closing parenthesis is missing, resolved semantic
  tokens, and absence of both compiler-backed providers in Restricted Mode.
  The JSON-RPC suite also checks nested calls, strings, Java overloads, UTF-16
  positions and the exact-fit/overflow reference-analysis boundary.

Production files: package.json and language-configuration.json register the
language/editor behavior; syntaxes/sprig.tmLanguage.json scopes source;
snippets/sprig.json holds snippets; images/icon.png is the project logo. Tests
use actual TextMate/Oniguruma, compiler, language server and Extension Host.

## Publishing

The extension ID is `ColinHouse.sprig-language`. Only the repository owner
publishes, with the **Publish VS Code extension** workflow
(`.github/workflows/vscode-extension.yml`). It runs from `main`, tests the
extension against a fresh compiler build, uploads the VSIX as an artifact and
publishes that same file. Agents package locally and never publish.

One-time setup:

1. Visual Studio Marketplace: create the publisher `ColinHouse` at
   <https://marketplace.visualstudio.com/manage>, then an Azure DevOps personal
   access token with the **Marketplace > Manage** scope for all accessible
   organizations. Store it as the repository secret `VSCE_PAT`.
2. Open VSX: sign in at <https://open-vsx.org> with GitHub, accept the publisher
   agreement and create the namespace `ColinHouse`
   (`npx ovsx create-namespace ColinHouse -p <token>`). Then either register
   this repository's `vscode-extension.yml` under **Trusted publishers**, which
   needs no stored secret, or store an access token as the secret `OVSX_PAT`.

Each release: raise `version` in `package.json`, add its `CHANGELOG.md` entry
and update the version in `README.md` (the Marketplace page), merge to `main`,
then run the workflow from the Actions tab and choose the registries. A version
can be published once per registry. The extension version is independent of
the compiler version; the language server features need an SDK that includes
`sprig lsp`.
