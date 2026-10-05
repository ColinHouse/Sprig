# Language server

`sprig lsp` runs a [Language Server Protocol](https://microsoft.github.io/language-server-protocol/)
server on standard input and output, so any LSP client can use it. It is part
of the development version and is not in the published v0.5.0-beta.1.

```text
sprig lsp [--stdio] [--classpath JAR_OR_DIR]...
```

`--stdio` is accepted because many clients pass it; standard input and output
are the only transport. `--classpath` adds JARs or class directories for files
that are not in a project, like the same option of `sprig check`. Inside a
project, the locked classpath comes first and these entries follow it.

The server has no settings of its own. It reads the same `sprig.toml` and
`sprig.lock` as the command line and never resolves or downloads anything.

## Editor setup

Register `sprig lsp` as a stdio server for `.spr` files. For example, in
Neovim 0.11 or later:

```lua
vim.filetype.add({ extension = { spr = "sprig" } })
vim.lsp.config("sprig", {
  cmd = { "sprig", "lsp" },
  filetypes = { "sprig" },
  root_markers = { "sprig.toml", ".git" },
})
vim.lsp.enable("sprig")
```

In Helix, in `languages.toml`:

```toml
[language-server.sprig]
command = "sprig"
args = ["lsp"]

[[language]]
name = "sprig"
scope = "source.sprig"
file-types = ["spr"]
roots = ["sprig.toml"]
comment-token = "#"
indent = { tab-width = 4, unit = "    " }
language-servers = ["sprig"]
```

These snippets follow each editor's usual way of registering a server. The
repository's tests drive the server directly over JSON-RPC, not through these
editors.

The VS Code extension (0.3.0 and later) starts `sprig lsp` itself when
`sprig capabilities --json` reports `"languageServer": true`, and keeps its
separate CLI commands for compilers without it. Its Extension Host tests run
the server through VS Code's own client. See `editors/vscode/README.md`.

## Features

Every answer comes from the same parser, name resolver and type checker as
`sprig check`. There is no second implementation of the language in the
server.

| LSP request | Behavior |
|---|---|
| Diagnostics | The errors `sprig check` reports for the open file, with their stable codes. They are refreshed 300 ms after you stop typing, right away on open and save, and for open files that import a changed file. An error inside an imported file is shown on the `import` that leads to it, with a link to the real location. Each diagnostic's `data.relatedHelp` names the `sprig help` topic about it, the `relatedHelp` of `sprig check --json`. |
| Hover | The declaration in Sprig syntax with its type, plus comment lines written directly above it. A local that a null check narrowed also shows its type at that point. Built-in methods show their result type. |
| Go to definition | Functions, methods, classes, enums, variants and their cases, fields, parameters and locals, across modules. On an `import` it opens the imported file. |
| References | Every use of a declaration. Locals and parameters are searched in their file. Other declarations are also searched in the open files and in the files of the same project that mention the name, up to 200 files. |
| Document symbols | Classes with fields and methods, enums and variants with their cases, functions and top-level variables. |
| Completion | After `.`: members of the value's type, a module's declarations, enum and variant cases, built-in methods and public Java members. Elsewhere: names in scope, module declarations, imports, built-ins and keywords. In a type position, it offers types and the module names that qualify them. |
| Formatting | The output of `sprig fmt`, as one edit. A file that does not parse is left unchanged. A CRLF document (common on Windows) keeps CRLF line endings, and needs no edit when only its line endings differ from `sprig fmt`. |
| Rename | Local variables and parameters only. See below. |

## Projects and unsaved files

A file inside a `sprig.toml` project, including its `tests/`, is checked with
that project's locked dependencies and classpath, as `sprig check` and
`sprig test` do. A missing or stale lock is reported on the file, as on the
command line: run `sprig resolve`. Other files are checked on their own.

The server reads open files from the editor, not from disk. An unsaved change
to an imported module is visible to every open file that imports it.

Only `file:` documents are analyzed.

## When the code does not compile yet

The compiler stops before name resolution when a file does not parse, and
before type checking when names do not resolve. The server follows the same
rule and does not guess:

- With a syntax error, hover, definition, references and rename return nothing.
  The outline keeps the last version of the file that parsed.
- With unresolved names, names still lead to their declarations. Member
  information that needs types, such as `value.field`, waits until the names
  resolve.
- Member completion after `.` checks the file with a placeholder at the
  cursor. If the rest of the program does not resolve, it offers members only
  for receivers whose type is written down: parameters, fields, annotated
  variables, modules and type names.

## Rename

Rename works on local variables (`let`, `var`, `for`, `catch` and `match`
bindings) and on function, method and lambda parameters. Ordinary functions
take positional arguments, so a parameter's name never appears outside its
function. Names that other files can use, such as functions, types, fields
and top-level variables, are not renamed.

Before answering, the server checks the renamed text. The rename is refused if
any use would refer to a different declaration, if another name would start
referring to the renamed one, or if a new error would appear. Keywords and
invalid identifiers are refused too.

## Positions

The compiler counts columns in Unicode code points. LSP counts UTF-16 code
units, so the server converts positions at the boundary, and text after an
emoji lines up in the editor.

## Not supported yet

Incremental document sync, workspace symbols, signature help, code actions,
semantic tokens and renaming names that other files can use.
