# VS Code extension

The local preview extension provides `.spr` highlighting, saved-file diagnostics,
**Sprig: Check**, **Run**, **Build**, **Show Generated Java** and diagnostic explanations.
It uses the existing Sprig CLI; it does not implement a second type system.

## Installation

Build `sprig-language-0.1.0.vsix` in the repository's `editors/vscode/` directory:

```sh
npm ci
npm run package
```

In VS Code, use Extensions → **Install from VSIX…**. This preview is not on
Marketplace and is not bundled with the v0.3 SDK ZIP.

Highlighting works without a compiler. For checks and execution, install the
[SDK](https://github.com/ColinHouse/Sprig/releases/tag/v0.3.0-alpha.1) and JDK17+,
then set `sprig.compilerPath` to the SDK's `bin/sprig`. A built source checkout
is also detected through ancestor `bin/` directories after PATH lookup.

## Use

Save a `.spr` file and select **Sprig: Check** from the Command Palette.
Errors appear in Problems with file/range/code and expected/actual types.
Saving checks by default. Projects check the entry graph and current file;
missing or stale dependency locks require an explicit CLI resolve.

**Sprig: Run** compiles and runs the active saved file. Output appears when the
program finishes. Runs are non-interactive, bounded by 120 seconds and 8 MB by
default. **Show Generated Java** opens emitted Java beside Sprig without javac.

Syntax highlighting remains available in Restricted Mode; compiler commands
require workspace trust. Linux/macOS are supported targets; Windows is preview.
The extension runs on the workspace host in Remote SSH/containers and needs a
compiler there. Browser-only VS Code and virtual filesystems are unsupported.

[Full configuration, testing and limitations](https://github.com/ColinHouse/Sprig/blob/main/editors/vscode/README.md).
