# Changelog

## Unreleased

- Windows: checks, Run and Run in Terminal start the JVM with the classpath of
  a source build or of an extracted release SDK (`lib\*`). The release SDK
  was rejected before, and a source build was missing ANTLR.
- Windows: cancelling Run stops the compiler and the program it started.

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
