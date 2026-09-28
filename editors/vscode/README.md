# Sprig for VS Code

Small desktop extension for Sprig compiler **0.4.0-alpha.1+**.
Extension version **0.1.0** is independent of the compiler version.

## Install / 安装

Install `sprig-language-0.1.0.vsix` using VS Code → Extensions → `…` →
**Install from VSIX…**. Or:

```sh
code --install-extension sprig-language-0.1.0.vsix
```

This package is locally installable; it has not been published to Marketplace.
Syntax highlighting works immediately, without Java or the compiler.
For checks and execution, install the [Sprig SDK](https://github.com/ColinHouse/Sprig/releases/tag/v0.4.0-alpha.1)
and JDK17+, then set **Sprig: Compiler Path** to the SDK's `bin/sprig` launcher.
On macOS/Linux you can also put the SDK's `bin` on PATH. A built Sprig source
checkout is detected by searching ancestors for `bin/sprig` after PATH.

```json
{
  "sprig.compilerPath": "/absolute/path/to/Sprig/bin/sprig",
  "sprig.checkOnSave": true,
  "sprig.commandTimeoutSeconds": 120
}
```

Windows SDK `bin/sprig.cmd` is experimental. The extension invokes its JVM
entrypoint directly; custom batch wrappers are unsupported. Remote SSH/containers
need the SDK and JDK installed on the remote workspace host. Browser-only VS Code
and virtual filesystems are unsupported.

## Features / 使用

Open a `.spr` file. Use the Command Palette or the editor context menu:

| Command | Behavior |
|---|---|
| **Sprig: Check** | Static check; display file-specific errors in Problems |
| **Sprig: Run** | Compile finite non-interactive programs; show output in Sprig Output |
| **Sprig: Run in Terminal** | Execute the saved file in an integrated terminal for servers or interactive programs |
| **Sprig: Build** | Generate Java and compile with javac |
| **Sprig: Show Generated Java** | Static check, emit Java without javac, open Java beside Sprig |
| **Sprig: Show Capabilities** | Display the installed compiler's capability JSON |
| **Sprig: Explain Diagnostic** | Query an error code; also available through the error's lightbulb |

Saving triggers checks by default. In a `sprig.toml` project, Check validates the
configured entry graph plus the current file, so an unused module also gets
feedback. Diagnostics for other projects remain available; diagnostics within
this project reflect the latest checked entry graph/current file. Missing/stale
locks are reported; the extension never automatically resolves/downloads packages.

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

### Highlighting

TextMate scopes cover implemented lexer keywords, functions/declarations,
capitalized type names, built-in and generic/nullable types, source `fn(A) -> R` types and
`fn(x: A) => expr` lambdas, numeric exponents,
double-quoted strings and escapes, `#` comments, variant/match, and operators.
Single quotes, Python keywords and Java syntax are not added to Sprig.
Colors follow the user's theme. This is lexical highlighting, not semantic name
resolution: capitalized identifiers are a type-name heuristic.
Four spaces and spaces instead of tabs are default editor settings for Sprig.
No formatter, completion, rename, navigation, debugger or LSP is included.

### Trust and diagnostics

Restricted Mode retains highlighting; compiler queries/checks/runs require a
trusted workspace. Compiler processes receive argument arrays without a shell.
Old/canceled checks cannot overwrite a newer result. Diagnostic starts convert
ANTLR code-point columns to VS Code UTF-16 positions. The current compiler mixes
UTF-16 token lengths with code-point start columns, so a diagnostic ending inside
an astral-character token can have an approximate end; ASCII operands after emoji
prefixes are covered by a real regression. Compiler response failures appear in
Sprig Output; manual commands also show an actionable error notification.

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

Tests use actual TextMate/Oniguruma, Sprig CLI/JVM, terminal command unit checks,
and VS Code Extension Host. Host coverage includes the real integrated-terminal
process path; unit checks cover trust, dirty files and argument boundaries.
The host tests use isolated temporary profiles; they do not install into or change
your usual VS Code settings. `npm run package` writes `dist/sprig-language-0.1.0.vsix`.
See the repository validation report for actual tested versions and limitations.
