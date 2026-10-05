# VS Code extension

The extension helps you write Sprig in VS Code with:

- syntax highlighting
- checks on save
- formatting
- an outline
- hover information
- completion
- go-to-definition
- snippets
- a Testing view
- one-command runs and a view of the generated Java

Types, signatures and errors all come from the `sprig` command you installed; the extension doesn't do any type checking of its own.

The extension isn't on the VS Code Marketplace yet and isn't part of the SDK archive, so you package and install it yourself from source.

## Installing

In the Sprig repository, in `editors/vscode/`, run:

```sh
npm ci
npm run package
```

That creates `dist/sprig-language-0.2.0.vsix`. In VS Code's Extensions view, choose `…` → **Install from VSIX…** and pick it, or run:

```sh
code --install-extension dist/sprig-language-0.2.0.vsix
```

Highlighting, the outline and snippets work right away, without Java or the compiler.

Everything else needs JDK 17+ and the [Sprig SDK](/en/guide/getting-started). Then set **Sprig: Compiler Path** (`sprig.compilerPath`) to the SDK's `bin/sprig`. If you don't set it, the extension looks for `sprig` on your `PATH` first, then searches parent directories for a `bin/sprig` built from source. The Testing view needs compiler v0.5.0-beta.1 or newer.

## While you write

| Feature | How to use it |
|---|---|
| Checks | Files are checked when you save, and errors show up in the Problems panel. Click an error code to open the diagnostic code reference |
| Formatting | **Format Document** (Shift+Alt+F) runs `sprig fmt`. To format on every save, turn on VS Code's `editor.formatOnSave` |
| Outline | The Outline view and the breadcrumbs list functions, classes, variants, enums, fields, methods and top-level variables; **Go to Symbol in Workspace** searches all your files |
| Hover | On a keyword, the `sprig help` text; on `JavaClass.method`, its signature as Sprig sees it; on `module.function` or your own declarations, the signature the compiler reports |
| Completion | Keywords, snippets, the file's declarations and imported names; after `JavaClass.`, `module.`, an enum name or a top-level variable followed by a dot, its members |
| Go to Definition | Press F12 on an import path, on `module.member` or on a declaration in the same file |
| Snippets | Type a prefix such as `func`, `class`, `variant`, `match`, `ifnn`, `try` or `importj` and press Tab |

Hover information and member completion use the saved file, so they catch up when you save.

## Commands

Open a `.spr` file and use these from the Command Palette, the editor's context menu or the **Sprig** status entry:

| Command | What it does |
|---|---|
| **Sprig: Check** | Checks the current file |
| **Sprig: Run** | Compiles and runs the file and shows the output when it finishes |
| **Sprig: Run in Terminal** | Runs it in a terminal, for servers and programs that read input |
| **Sprig: Run Tests** | Runs the current project's tests and shows the results in the Testing view |
| **Sprig: Build** | Generates Java and compiles it with javac |
| **Sprig: Show Generated Java** | Checks, generates Java and opens it alongside, without javac |
| **Sprig: Resolve Dependencies** | Runs `sprig resolve` for the current project |
| **Sprig: New Project** | Pick a folder and a name; creates the project and its lock file |
| **Sprig: Explain Diagnostic** | Explains an error code on a formatted page; also available from the lightbulb next to an error |
| **Sprig: Show Help Topic** | Shows a `sprig help` topic on a formatted page |
| **Sprig: Show Capabilities** | Shows the installed compiler's feature list |
| **Sprig: Open Documentation** | Opens the Sprig website |
| **Sprig: Show Actions** | Lets you pick any of the commands above |

The **Sprig** status entry lives in the status bar's language status area (the `{}` icon). It shows the compiler version and, inside a project, whether the lock file is current, stale or missing.

- **Saved files only.** Commands work on the saved version of the current file, with the nearest project root as the working directory. Save your changed project files before you run.
- **No automatic downloads.** If the lock file is missing or out of date, the extension tells you. `sprig resolve` only runs when you choose **Resolve Dependencies** or **New Project**.
- **Limits on Run.** **Sprig: Run** is for programs that finish quickly and don't read input. It provides no standard input and shows the output once the program ends, with a default limit of 120 seconds and 8 MB of output. You can change the time limit with `sprig.commandTimeoutSeconds`. For servers and programs that read input, use **Sprig: Run in Terminal**, which has none of these limits; stop it with Ctrl+C or by closing the terminal.

## The Testing view

In a project with a `sprig.toml`, every `.spr` file under `tests/` shows up in VS Code's Testing view. You can run one file or the whole project.

- A failing test shows the reason, the source location of the error and the program's output.
- Files under `tests/compile_fail/` are supposed to fail to compile, with the expected error codes in a `.expect.toml` file next to each one.
- Each run is limited by `sprig.testTimeoutSeconds`, 600 seconds by default.

## Limitations

- The outline and Go to Definition find declarations by name; they don't follow local shadowing or look at types.
- Member completion knows imported names, enums and variants in the same file, and top-level variables whose type the compiler reports. It doesn't offer members of local variables or parameters.
- Code with syntax errors can't be formatted.
- No rename, find references or debugging, and no language server (LSP).
- In Restricted Mode (an untrusted workspace) you get highlighting, the outline, snippets and keyword completion only. Checking, running, formatting, hover information and tests require a trusted workspace.
- Linux and macOS are supported. Windows is a preview, and cancelling a run there may not stop the child process.
- With Remote SSH or containers, the SDK and JDK have to be installed on the remote side. Browser-based VS Code and virtual file systems aren't supported.

The [extension README](https://github.com/ColinHouse/Sprig/blob/main/editors/vscode/README.md) covers configuration, development and limitations in full.
