# Changelog

## 0.3.0

- The extension starts the Sprig language server, `sprig lsp`, when the
  compiler provides it: diagnostics as you type, hover with the types of
  locals and parameters, member completion for any value, Go to Definition
  across modules, Find References, rename of locals and parameters, and
  formatting. With a compiler without it, such as v0.5.0-beta.1, or with
  `sprig.languageServer.enabled` off, the extension uses separate compiler
  commands as in 0.2.0.
- New command **Sprig: Restart Language Server** and new settings
  `sprig.languageServer.enabled` and `sprig.trace.server`. The language status
  item shows whether the server runs.
- Language server diagnostics link their codes to the diagnostic reference, and
  the lightbulb offers their `sprig help` topic, as for the command-line checks.
- Windows: checks, Run and Run in Terminal start the JVM with the classpath of
  a source build or of an extracted release SDK (`lib\*`). The release SDK
  was rejected before, and a source build was missing ANTLR.
- Windows: cancelling Run stops the compiler and the program it started.
- The extension is bundled into a single file, and the package carries an icon
  and Marketplace details.

## 0.2.0

- Format Document runs `sprig fmt`; it works with `editor.formatOnSave`.
- Outline, breadcrumbs and workspace symbols for functions, classes, variants,
  enums, fields, methods, cases and top-level bindings.
- Snippets for common Sprig structures, each checked by the real parser.
- Hover: `sprig help` for keywords, Sprig signatures for Java members, and the
  compiler's view of module members and your own declarations.
- Completion: keywords, declarations and import aliases; members after
  `JavaClass.`, `module.`, an enum or variant, or a top-level variable.
- Go to Definition for import paths, `module.member` and declarations in the
  current file.
- Testing view backed by `sprig test --json`, with diagnostics at their source
  locations and program output; new setting `sprig.testTimeoutSeconds`.
- Sprig language status item with the compiler version and lock status.
- New commands: Run Tests, Resolve Dependencies, New Project, Show Help Topic,
  Open Documentation and Show Actions.
- Diagnostic codes in Problems link to the diagnostic reference; Explain
  Diagnostic and related help open as rendered pages instead of raw JSON.
- The extension also activates in workspaces that contain `sprig.toml`.
- The README points to the current SDK release.

## 0.1.0

- Initial local preview: highlighting, saved-file diagnostics, Run, Run in
  Terminal, Build, Show Generated Java, Show Capabilities and Explain Diagnostic.
