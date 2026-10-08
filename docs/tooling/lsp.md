# Language server

`sprig lsp` runs a [Language Server Protocol](https://microsoft.github.io/language-server-protocol/)
server on standard input and output, so any LSP client can use it. It was added
in v0.6.0-beta.1.

```text
sprig lsp [--stdio] [--classpath JAR_OR_DIR]... [--classpath-file FILE]
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
separate CLI commands for compilers without it. Its lightbulb shows the
server's quick fixes next to its own Explain and help actions; the separate
CLI commands offer no quick fixes. Its Extension Host tests run the server
through VS Code's own client. See `editors/vscode/README.md`.

## Features

Every answer comes from the same parser, name resolver and type checker as
`sprig check`. There is no second implementation of the language in the
server.

| LSP request | Behavior |
|---|---|
| Diagnostics | The errors `sprig check` reports for the open file, with their stable codes. They are refreshed 300 ms after you stop typing, right away on open and save, and for open files that import a changed file. An error inside an imported file is shown on the `import` that leads to it, with a link to the real location. Each diagnostic's `data.relatedHelp` names the `sprig help` topic about it, the `relatedHelp` of `sprig check --json`. |
| Hover | The declaration in Sprig syntax with its type, plus comment lines written directly above it. A local that a null check narrowed also shows its type at that point. Built-in methods show their result type. |
| Go to definition | Functions, methods, classes, enums, variants and their cases, fields, parameters and locals, across modules. On an `import` it opens the imported file. |
| References | Uses of a declaration. Locals and parameters are searched in their file. Other declarations are also searched in the open files and in the files of the same project that mention the name, up to 200 additional analyses. If a matching project file is omitted by this limit, a warning says the results are incomplete. |
| Signature help | Parameter names and types for Sprig declarations and public Java overloads, with the active parameter. Triggered by `(` and `,`, including a call with no closing parenthesis. See below. |
| Semantic tokens | Resolved types, functions, methods, fields, parameters and variables, with declaration and readonly modifiers. Colors follow the editor's theme. |
| Document symbols | Classes with fields and methods, enums and variants with their cases, functions and top-level variables. |
| Completion | After `.`: members of the value's type, a module's declarations, enum and variant cases, built-in methods and public Java members. Elsewhere: names in scope, module declarations, imports, built-ins and keywords. In a type position, it offers types and the module names that qualify them. |
| Formatting | The output of `sprig fmt`, as one edit. A file that does not parse is left unchanged. A CRLF document (common on Windows) keeps CRLF line endings, and needs no edit when only its line endings differ from `sprig fmt`. |
| Rename | Local variables and parameters only. See below. |
| Code actions | Quick fixes: the `suggestedEdits` of the diagnostics under the cursor or selection. See below. |

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
  The outline keeps the last version of the file that parsed. An `if`
  expression whose `else` is still missing is the exception: it is reported,
  and the file keeps every feature while the `else` is being written.
- With unresolved names, names still lead to their declarations. Member
  information that needs types, such as `value.field`, waits until the names
  resolve.
- Member completion after `.` checks the file with a placeholder at the
  cursor. If the rest of the program does not resolve, it offers members only
  for receivers whose type is written down: parameters, fields, annotated
  variables, modules and type names.
- Signature help completes only the call around the cursor in a temporary
  analysis. Missing closing parentheses on that call and surrounding calls
  are allowed; unrelated syntax errors can still prevent an answer. The
  original text and its diagnostics are not replaced by the temporary text.
- Semantic tokens use the current symbol index. When parsing or name
  resolution fails, they return an empty list rather than stale positions;
  the editor can keep its lexical highlighting.

## Signature help

`textDocument/signatureHelp` displays written parameter names and types for
Sprig functions and methods, including imported declarations, and the fields
of named class and variant constructors. A function value uses its static
callable type and placeholder parameter names. Nested calls, list and map
literals, strings containing commas and escaped quotes do not advance the
outer call's active parameter. Named constructor arguments select their field
even when written out of declaration order. Positions use UTF-16, including
text after an emoji.

Public Java methods and constructors show every supported overload as a
candidate, using the compiler's JVM type mapping. When bytecode does not
retain a parameter's name, it is displayed as `arg0`, `arg1`, and so on.
Varargs keep the final parameter active for additional arguments. Candidates
are not ranked by argument types or complex generic inference. Sprig generic
declarations display their written type parameters. Built-in functions and
native collection/string methods do not yet provide parameter signatures.

## Semantic highlighting

`textDocument/semanticTokens/full` returns resolved identifier tokens, not a
capitalization heuristic. For example, a lowercase class and an uppercase
local variable still receive their correct categories. The legend includes
`namespace`, `class`, `enum`, `enumMember`, `function`, `method`, `parameter`,
`variable`, `property`, `type` and `typeParameter`, with `declaration` and
`readonly` modifiers. Built-in types and members and Java types and members
are included. Range and delta requests are not implemented.

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

## Quick fixes

Some diagnostics carry `suggestedEdits`: the mechanical rewrite their hint
describes (see [diagnostic codes](diagnostic-codes.md)). Today that is a missing
`@std` import, an undeclared `throws`, positional constructor arguments,
`else if`, and the removed conversions `Int.toFloat()` and `Float.toInt()`
(rewritten to `toFloatExact()` and `toIntExact()`).

For a `textDocument/codeAction` request, the server checks the current text
and takes every diagnostic of the document whose range overlaps the requested
range; a cursor at either end of a diagnostic counts. Each suggested edit of
such a diagnostic becomes one code action:

- `kind` is `quickfix`, and `title` is the edit's `description`, such as
  `replace 'else if' with 'elif'`.
- `diagnostics` holds that diagnostic, exactly as it was published.
- `edit` is a `WorkspaceEdit` that changes only this document. Its range is
  converted to UTF-16, and a line it inserts into a CRLF document ends in CRLF.
- `isPreferred` is true when it is the diagnostic's only edit, so an editor's
  auto fix can apply it.

Applying a fix removes the diagnostic it came from. Whether the rewrite is
what you meant (adding `throws Error` to a function, say) is still yours to
judge, as with `sprig check --json`.

The diagnostics a client sends in the request's context are ignored: the
actions come from the server's own check, so a stale or invented diagnostic
gets no fix. An error inside an imported file, shown on the `import`, has no
fix here; open that file to fix it. A request whose `only` asks for other
kinds, such as `refactor` or `source`, gets none.

Every fix is an edit, which a bare `Command` cannot carry. The server
therefore advertises `codeActionProvider` (with the `quickfix` kind) only to
clients that accept code action literals,
`textDocument.codeAction.codeActionLiteralSupport`; current versions of VS
Code, Neovim and Helix do.

## Positions

The compiler counts columns in Unicode code points. LSP counts UTF-16 code
units, so the server converts positions at the boundary, and text after an
emoji lines up in the editor.

## Not supported yet

Incremental document sync, workspace symbols, code actions other than these
quick fixes (refactorings, organize imports), semantic-token range/delta
requests and renaming names that other files can use.
