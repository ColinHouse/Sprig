# Sprig for VS Code

Language support for [Sprig](https://colinhouse.github.io/Sprig/en/), an
indentation-based, statically typed language for the JVM. Diagnostics as you
type, quick fixes, hover, completion, navigation, references, rename and
formatting come from the Sprig language server, `sprig lsp`, which runs the
same parser and type checker as `sprig check`. The extension does not
implement a second type checker. It also runs programs and tests, shows the
generated Java, and highlights Sprig without any compiler.

Extension version **0.3.0** is independent of the compiler version.

## Install / 安装

Install **Sprig** (`ColinHouse.sprig-language`) from the Extensions view. The
Visual Studio Marketplace serves VS Code; Open VSX serves VSCodium and other
compatible editors. To build the extension yourself, see
[Development](#development--开发).

Highlighting, the outline and snippets work immediately, without Java or the
compiler. Everything else needs JDK 17+ and the
[Sprig SDK](https://colinhouse.github.io/Sprig/en/guide/getting-started). Set
**Sprig: Compiler Path** to the SDK's `bin/sprig` launcher, or put the SDK's
`bin` on PATH. A built Sprig source checkout is found by searching parent
directories for `bin/sprig` after PATH.

The extension asks the compiler (`sprig capabilities --json`) whether it has a
language server, so there is nothing to configure:

| Compiler | What you get |
|---|---|
| v0.6.0-beta.1 or newer, with `sprig lsp` | The language server, as described below |
| v0.5.0-beta.1 | Checks on save, and hover, completion and navigation from separate compiler commands on saved files |
| 0.4.0-alpha.1 | As v0.5.0-beta.1, without the Testing view |

```json
{
  "sprig.compilerPath": "/absolute/path/to/Sprig/bin/sprig",
  "sprig.languageServer.enabled": true,
  "sprig.checkOnSave": true,
  "sprig.commandTimeoutSeconds": 120,
  "sprig.testTimeoutSeconds": 600
}
```

`sprig.trace.server` (`off`, `messages` or `verbose`) logs the protocol in the
**Sprig Language Server** output.

Windows support (`bin/sprig.cmd` from a source build or an extracted release
ZIP) is a preview. The extension starts the same JVM entry point and classpath
directly, so paths never pass through `cmd.exe`; custom batch wrappers are
unsupported. `java.exe` reads its command line in the Windows ANSI code page,
so commands such as Check and Run cannot take a file whose path uses
characters outside it. The language server is not affected: document paths
travel inside the protocol. Remote SSH and containers need the SDK and JDK on
the remote host. Browser-only VS Code and virtual file systems are unsupported.

## Editing / 编辑

| Feature | With the language server | Without it |
|---|---|---|
| Diagnostics | As you type, for open files. An error inside an imported file shows on the `import` that reaches it | When you save, for the file and its project's entry graph |
| Quick fixes | When an error's hint is one mechanical rewrite, such as `else if` to `elif` or a missing `@std` import, the lightbulb offers the compiler's edit and applies it in one click | Not available |
| Hover | Declarations in Sprig syntax with their types, including locals, parameters and narrowed nullable values, plus the comments above them | Java members, module members and your top-level declarations, from the saved file |
| Completion | After `.`, the members of any value, including locals and parameters; elsewhere, names in scope, imports and keywords | Keywords, declarations and imports; members after `JavaClass.`, `module.`, an enum or variant, or a top-level variable |
| Go to Definition | Functions, types, cases, fields, parameters and locals, across modules; on an `import`, the imported file | Import paths, `module.member` and declarations in the same file |
| Find References | Every use of a declaration | Not available |
| Rename | Local variables and parameters, checked before the edit is applied | Not available |
| Formatting | **Format Document** applies `sprig fmt`; combine with `editor.formatOnSave` if you like | The same, through a temporary copy of the editor text |
| Outline | Classes with fields and methods, enums and variants with their cases, functions and top-level variables | The same, found lexically |

In both modes, a keyword hover shows its `sprig help` topic, **Go to Symbol in
Workspace** searches every `.spr` file, each diagnostic code in Problems links
to the diagnostic reference, and the lightbulb offers to explain a code or open
its help topic. Snippets: `func`, `funct`, `class`, `variant`, `enum`, `match`,
`matchr`, `if`, `ifnn`, `for`, `while`, `try`, `import`, `imports`, `importj`,
`generic`.

The language server reads `sprig.toml` and `sprig.lock` like the command line
and never resolves or downloads anything. See the
[language server reference](https://colinhouse.github.io/Sprig/en/reference/tooling/lsp)
for what it does while code does not compile yet.

## Commands / 命令

Open a `.spr` file. Use the Command Palette, the editor context menu or the
**Sprig** language status item:

| Command | Behavior |
|---|---|
| **Sprig: Check** | Static check of the saved file and its project's entry graph; errors show in Problems |
| **Sprig: Run** | Compile and run a finite, non-interactive program; output shows in the Sprig output |
| **Sprig: Run in Terminal** | Run the saved file in an integrated terminal, for servers and interactive programs |
| **Sprig: Run Tests** | Run the current project's tests and show the results in the Testing view |
| **Sprig: Build** | Generate Java and compile it with javac |
| **Sprig: Show Generated Java** | Check, emit Java without javac, and open it beside the Sprig file |
| **Sprig: Resolve Dependencies** | Run `sprig resolve` for the current project |
| **Sprig: New Project** | Choose a folder and a name; runs `sprig init` and writes the new project's lock |
| **Sprig: Explain Diagnostic** | Rendered explanation of an error code; also in the error's lightbulb |
| **Sprig: Show Help Topic** | Rendered `sprig help <topic>`; errors with a related topic offer it in the lightbulb |
| **Sprig: Show Capabilities** | Display the installed compiler's capability JSON |
| **Sprig: Restart Language Server** | Start `sprig lsp` again, for example after rebuilding the compiler |
| **Sprig: Open Documentation** | Open the Sprig website (Chinese or English, following VS Code's display language) |
| **Sprig: Show Actions** | Pick any of the commands above |

The **Sprig** language status item shows the compiler version, whether the
language server runs and, in a project, whether `sprig.lock` is current,
stale or missing.

Without the language server, saving checks the file by default. In a
`sprig.toml` project, Check validates the configured entry graph plus the
current file, so an unused module also gets feedback. With the server, open
files are checked as they change, and **Sprig: Check** adds the files of the
entry graph that are not open. The extension never resolves or downloads
packages on its own: only **Resolve Dependencies** and **New Project** run
`sprig resolve`, and only when you choose them.

Commands run the **active saved file**, with the nearest project root as
working directory. Save all dirty Sprig files in that project first.
Java/build artifacts are kept in the extension's global storage, outside the
source tree; old artifacts are not automatically deleted. Generated files can
be inspected, but edits are not fed back into Sprig.

Run supports finite **non-interactive** programs: stdin is closed, output is
displayed on completion, and the default limits are 120 seconds and 8 MB.
Cancel the progress notification to stop the compiler and the program it
started: the process group on Linux/macOS, the process tree on Windows. For
persistent servers or interactive programs, choose **Sprig: Run in Terminal**.
It starts `sprig run <saved-file>` with the nearest project root as working
directory; output and stdin belong to the terminal, with no time or output
limit. Use Ctrl+C or close the terminal to stop the program.

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
keyword completion and navigation to file imports. The language server,
compiler queries, checks, runs, formatting, hover details, member completion
and tests require a trusted workspace; the server starts when you trust the
workspace. Compiler processes receive argument arrays without a shell.
Old/canceled checks cannot overwrite a newer result. Compiler response
failures appear in the Sprig output; manual commands also show an actionable
error notification. If the language server stops repeatedly, the extension
falls back to separate compiler commands until **Sprig: Restart Language
Server**; its own log is the **Sprig Language Server** output.

### Limitations

- The language server re-checks the whole program on each change, renames only
  locals and parameters, and has no workspace symbols, signature help, code
  actions other than quick fixes, or semantic highlighting yet.
- In a multi-root workspace, one language server serves the window, with the
  compiler configured for the Sprig file or folder it started from.
- Without the language server, the outline, workspace symbols and Go to
  Definition are lexical, member completion does not cover locals and
  parameters, and hover and completion reflect saved files.
- Formatting needs code that parses; otherwise the document is left unchanged.
- No debugger.

## Development / 开发

From `editors/vscode/`:

```sh
npm ci
npm test
npm run package
```

Build the compiler at the repository root first (`python3 scripts/build.py`).
Open this extension directory in VS Code and press **F5** to launch a separate
Extension Development Host; the launch task compiles TypeScript and bundles
`out/main.js` with esbuild.

```sh
npm run test:host
# Reuse an installed VS Code rather than downloading one:
VSCODE_EXECUTABLE_PATH="/Applications/Visual Studio Code.app/Contents/MacOS/Code" npm run test:host
# Real Restricted Mode test (custom launch: official runner disables trust):
SPRIG_TEST_RESTRICTED=1 VSCODE_EXECUTABLE_PATH="/Applications/Visual Studio Code.app/Contents/MacOS/Code" npm run test:host
```

On Windows, point `VSCODE_EXECUTABLE_PATH` at `Code.exe`.

Unit tests use the actual compiler for the CLI adapter, the language server
command and protocol, Markdown rendering, queries, formatting, snippets and
`sprig test` results, plus TextMate/Oniguruma tokenization and terminal
command checks. Host tests run a real VS Code Extension Host: diagnostics, Run,
generated Java, the integrated terminal, symbols, hover, completion,
definition, formatting, the Testing view and project commands without the
language server; then, with it, diagnostics for unsaved edits, types of
locals, member completion, cross-module definition, references, rename,
formatting, restart and the fall back; and a separate Restricted Mode run. The
host tests use isolated temporary profiles; they do not install into or change
your usual VS Code settings. `npm run package` writes
`dist/sprig-language-0.3.0.vsix`, and `python3 scripts/check-editor.py` at the
repository root also checks its contents. Publishing is described in
[DEVELOPMENT.md](https://github.com/ColinHouse/Sprig/blob/main/editors/vscode/DEVELOPMENT.md).

## License

Apache-2.0. The extension bundles the language client libraries listed, with
their licenses, in `ThirdPartyNotices.txt`.
