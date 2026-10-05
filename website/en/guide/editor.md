# VS Code extension

The extension gives you `.spr` syntax highlighting, checks on save, one-command runs, a view of the generated Java and explanations for error codes. Checking and running go through the `sprig` command you installed; the extension doesn't do any type checking of its own.

The extension isn't on the VS Code Marketplace yet and isn't part of the SDK archive, so you package and install it yourself from source.

## Installing

In the Sprig repository, in `editors/vscode/`, run:

```sh
npm ci
npm run package
```

That creates `dist/sprig-language-0.1.0.vsix`. In VS Code's Extensions view, choose `…` → **Install from VSIX…** and pick it, or run:

```sh
code --install-extension dist/sprig-language-0.1.0.vsix
```

Highlighting works right away, without Java or the compiler.

To check and run code, you also need JDK 17+ and the [Sprig SDK](/en/guide/getting-started). Then set **Sprig: Compiler Path** (`sprig.compilerPath`) to the SDK's `bin/sprig`. If you don't set it, the extension looks for `sprig` on your `PATH` first, then searches parent directories for a `bin/sprig` built from source.

## What it does

Open a `.spr` file and use these commands from the Command Palette or the editor's context menu:

| Command | What it does |
|---|---|
| **Sprig: Check** | Checks the file and lists errors in the Problems panel |
| **Sprig: Run** | Compiles and runs the file and shows the output when it finishes |
| **Sprig: Run in Terminal** | Runs it in a terminal, for servers and programs that read input |
| **Sprig: Build** | Generates Java and compiles it with javac |
| **Sprig: Show Generated Java** | Checks, generates Java and opens it alongside, without javac |
| **Sprig: Show Capabilities** | Shows the installed compiler's feature list |
| **Sprig: Explain Diagnostic** | Explains an error code; also available from the lightbulb next to an error |

- **Checks on save.** This is on by default; `sprig.checkOnSave` turns it off. In a project with a `sprig.toml`, a check covers every file the entry point imports plus the current file, so a module nobody imports still gets checked.
- **Saved files only.** Commands work on the saved version of the current file, with the nearest project root as the working directory. Save your changed project files before you run.
- **No automatic downloads.** If the lock file is missing or out of date, the extension tells you, and you run `sprig resolve` on the command line.
- **Limits on Run.** **Sprig: Run** is for programs that finish quickly and don't read input. It provides no standard input and shows the output once the program ends, with a default limit of 120 seconds and 8 MB of output. You can change the time limit with `sprig.commandTimeoutSeconds`. For servers and programs that read input, use **Sprig: Run in Terminal**, which has none of these limits; stop it with Ctrl+C or by closing the terminal.

## Limitations

- No completion, rename, go-to-definition, formatting or debugging, and no language server (LSP).
- Highlighting is purely lexical: capitalized names are colored as types, without real name resolution.
- In Restricted Mode (an untrusted workspace) you get highlighting only; checking and running require a trusted workspace.
- Linux and macOS are supported. Windows is a preview, and cancelling a run there may not stop the child process.
- With Remote SSH or containers, the SDK and JDK have to be installed on the remote side. Browser-based VS Code and virtual file systems aren't supported.

The [extension README](https://github.com/ColinHouse/Sprig/blob/main/editors/vscode/README.md) covers configuration, development and limitations in full.
