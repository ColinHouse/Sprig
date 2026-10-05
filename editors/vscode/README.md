# Sprig for VS Code

Desktop extension for the Sprig language: highlighting, compiler diagnostics,
formatting, outline, hover, completion, navigation, a Testing view, Run and
generated Java. Extension version **0.2.0** is independent of the compiler
version. Types, signatures and errors all come from the installed `sprig` CLI;
the extension does not implement a second type checker.

## Install / 安装

Build the VSIX in this directory (`npm ci`, then `npm run package`) and install
`dist/sprig-language-0.2.0.vsix` using VS Code → Extensions → `…` →
**Install from VSIX…**. Or:

```sh
code --install-extension dist/sprig-language-0.2.0.vsix
```

This package is locally installable; it has not been published to Marketplace.
Syntax highlighting, the outline and snippets work immediately, without Java or the compiler.
For everything else, install the [Sprig SDK](https://github.com/ColinHouse/Sprig/releases/tag/v0.5.0-beta.1)
(see [Getting started](https://colinhouse.github.io/Sprig/en/guide/getting-started)) and JDK17+,
then set **Sprig: Compiler Path** to the SDK's `bin/sprig` launcher.
On macOS/Linux you can also put the SDK's `bin` on PATH. A built Sprig source
checkout is detected by searching ancestors for `bin/sprig` after PATH.
Compiler 0.4.0-alpha.1 or newer supports checking, running, formatting, hover and
completion; the Testing view needs 0.5.0-beta.1 or newer.

```json
{
  "sprig.compilerPath": "/absolute/path/to/Sprig/bin/sprig",
  "sprig.checkOnSave": true,
  "sprig.commandTimeoutSeconds": 120,
  "sprig.testTimeoutSeconds": 600
}
```

Windows SDK `bin/sprig.cmd` is experimental. The extension invokes its JVM
entrypoint directly; custom batch wrappers are unsupported. Remote SSH/containers
need the SDK and JDK installed on the remote workspace host. Browser-only VS Code
and virtual filesystems are unsupported.

## Editing / 编辑

| Feature | Behavior |
|---|---|
| Diagnostics | Saved files are checked with `sprig check --json`; each code in Problems links to the diagnostic reference |
| Formatting | **Format Document** runs `sprig fmt` on a temporary copy of the editor text; combine with `editor.formatOnSave` if you like |
| Outline | Outline view, breadcrumbs and **Go to Symbol in Workspace** list functions, classes, variants, enums, fields, methods, cases and top-level bindings |
| Hover | Keywords show `sprig help`; `JavaClass.member` shows the Sprig signatures from `sprig api`; `module.member` and your own declarations show the compiler's view |
| Completion | Keywords, snippets, your declarations and import aliases; after `JavaClass.`, `module.`, an enum or variant, or a top-level variable, its members |
| Go to Definition | Import paths (including `@std` and dependencies), `module.member` and declarations in the current file |
| Snippets | `func`, `funct`, `class`, `variant`, `enum`, `match`, `matchr`, `if`, `ifnn`, `for`, `while`, `try`, `import`, `imports`, `importj`, `generic` |

Hover details and member completion use `sprig api` on saved files and are
cached until a file changes, so edits appear after you save. Lookups that fail
(for example a stale dependency lock) simply show nothing.

## Commands / 命令

Open a `.spr` file. Use the Command Palette, the editor context menu or the
**Sprig** language status item:

| Command | Behavior |
|---|---|
| **Sprig: Check** | Static check; display file-specific errors in Problems |
| **Sprig: Run** | Compile finite non-interactive programs; show output in Sprig Output |
| **Sprig: Run in Terminal** | Execute the saved file in an integrated terminal for servers or interactive programs |
| **Sprig: Run Tests** | Run the current project's tests and show the results in the Testing view |
| **Sprig: Build** | Generate Java and compile with javac |
| **Sprig: Show Generated Java** | Static check, emit Java without javac, open Java beside Sprig |
| **Sprig: Resolve Dependencies** | Run `sprig resolve` for the current project |
| **Sprig: New Project** | Choose a folder and a name; runs `sprig init` and writes the new project's lock |
| **Sprig: Explain Diagnostic** | Rendered explanation of an error code; also available through the error's lightbulb |
| **Sprig: Show Help Topic** | Rendered `sprig help <topic>`; errors with a related topic offer it in the lightbulb |
| **Sprig: Show Capabilities** | Display the installed compiler's capability JSON |
| **Sprig: Open Documentation** | Open the Sprig website (Chinese or English, following VS Code's display language) |
| **Sprig: Show Actions** | Pick any of the commands above |

The **Sprig** language status item shows the compiler version and, in a
project, whether `sprig.lock` is current, stale or missing.

Saving triggers checks by default. In a `sprig.toml` project, Check validates the
configured entry graph plus the current file, so an unused module also gets
feedback. Diagnostics for other projects remain available; diagnostics within
this project reflect the latest checked entry graph/current file. Missing/stale
locks are reported. The extension never resolves or downloads packages on its
own: only **Resolve Dependencies** and **New Project** run `sprig resolve`, and
only when you choose them.

Commands run the **active saved file**, with the nearest project root as working
directory. Save all dirty Sprig files in that project first. Java/build artifacts
are kept in the extension's global storage, outside the source tree; old artifacts
are not automatically deleted. Generated files can be inspected but edits are
not fed back into Sprig. Java output is opened using compiler-provided paths.

Run currently supports finite **non-interactive** programs. stdin is closed,
output is displayed on completion, the default limit is 120 seconds and 8 MB.
Cancel the progress notification to stop the process group on Linux/macOS;
Windows preview cancellation may not stop child JVM processes. For persistent servers or interactive programs, select **Sprig: Run in Terminal**.
It starts normal `sprig run <saved-file>` with the nearest project root as cwd;
output and stdin belong to the integrated terminal. There is no JSON buffering,
120-second command limit or 8 MB adapter cap. Use Ctrl+C or close the terminal
to stop the program. This command checks workspace trust and saved files before
launching, and never resolves dependencies automatically. Each invocation opens
one terminal. Linux/macOS launch the SDK directly; Windows preview uses its JVM
entrypoint, with the same custom-batch restriction as finite Run.

## Testing view / 测试

Every `sprig.toml` project with a `tests/` directory appears in the Testing view,
one entry per `tests/**/*.spr` file. Running a file uses `sprig test <file> --json`;
running a project uses `sprig test --json`, both in the project root. Failures
show the reason, the compiler diagnostics at their source locations and the
program output. Files under `tests/compile_fail/` must fail compilation with the
codes listed in their sibling `.expect.toml`. Each run is bounded by
`sprig.testTimeoutSeconds`. VS Code saves dirty files before running tests from
the Testing view (`testing.saveBeforeTest`); **Sprig: Run Tests** saves the
project's Sprig files itself.

### Highlighting

TextMate scopes cover implemented lexer keywords, functions/declarations,
capitalized type names, built-in and generic/nullable types, source `fn(A) -> R` types and
`fn(x: A) => expr` lambdas, numeric exponents,
double-quoted strings and escapes, `#` comments, variant/match, and operators.
Single quotes, Python keywords and Java syntax are not added to Sprig.
Colors follow the user's theme. This is lexical highlighting, not semantic name
resolution: capitalized identifiers are a type-name heuristic.
Four spaces and spaces instead of tabs are default editor settings for Sprig.

### Trust and diagnostics

Restricted Mode keeps highlighting, the outline, workspace symbols, snippets,
keyword completion and navigation to file imports. Compiler queries, checks,
runs, formatting, hover details, member completion and tests require a trusted
workspace. Compiler processes receive argument arrays without a shell.
Old/canceled checks cannot overwrite a newer result. Diagnostic starts convert
ANTLR code-point columns to VS Code UTF-16 positions. The current compiler mixes
UTF-16 token lengths with code-point start columns, so a diagnostic ending inside
an astral-character token can have an approximate end; ASCII operands after emoji
prefixes are covered by a real regression. Compiler response failures appear in
Sprig Output; manual commands also show an actionable error notification.

### Limitations

- Outline, workspace symbols and Go to Definition are lexical: they find
  declarations by name and do not follow local shadowing or types.
- Member completion covers import aliases, enum and variant cases in the current
  file, and top-level variables whose type the compiler reports for the saved
  file. Members of local variables and parameters are not offered.
- Formatting needs code that parses; otherwise the document is left unchanged.
- No rename, find references, semantic highlighting, debugger or LSP.

## Development / 开发

From `editors/vscode/`:

```sh
npm ci
npm test
npm run package
```

Build the compiler at the repository root first (`python3 scripts/build.py`).
Open this extension directory in VS Code and press **F5** to launch a separate
Extension Development Host. The shared launch task compiles TypeScript.

```sh
npm run test:host
# Reuse an installed VS Code rather than downloading one:
VSCODE_EXECUTABLE_PATH="/Applications/Visual Studio Code.app/Contents/MacOS/Code" npm run test:host
# Real Restricted Mode test (custom launch: official runner disables trust):
SPRIG_TEST_RESTRICTED=1 VSCODE_EXECUTABLE_PATH="/Applications/Visual Studio Code.app/Contents/MacOS/Code" npm run test:host
```

Unit tests use the actual compiler for the CLI adapter, Markdown rendering,
queries, formatting, snippets and `sprig test` results, plus TextMate/Oniguruma
tokenization and terminal command checks. Host tests run a real VS Code Extension
Host: diagnostics, Run, generated Java, the integrated terminal, symbols, hover,
completion, definition, formatting, the Testing view and project commands, and a
separate Restricted Mode run.
The host tests use isolated temporary profiles; they do not install into or change
your usual VS Code settings. `npm run package` writes `dist/sprig-language-0.2.0.vsix`.
See the repository validation report for actual tested versions and limitations.
