# Project manifests (implemented contract)

`sprig init` creates a manifest and `src/main.spr` without overwriting files.
`project --json` searches upward for the nearest `sprig.toml`. `check/build/run`
without a source use its entry; an explicit file outside the source root bypasses the project; files within
the source root retain its dependency graph.

```toml
[project]
name = "example"
version = "0.1.0"
language = "0.8"
source = "src"
entry = "src/main.spr"
exports = ["public.spr"]

[[bin]]
name = "tool"
entry = "src/tool.spr"
```

Only `[project]`, `[[bin]]`, `[[dependency]]`, `[[jvm]]` and `[[registry]]`
are accepted. `[project]` takes `name`, `version`, `language`, `source`,
`entry`, `exports` and `license` (the SPDX id `sprig publish` records); see
[dependencies](dependencies.md) for `[[registry]]`. All fields shown above are
strings except `exports`, a string array. Write `exports` inside `[project]`,
as the packages in `libraries/` do; a root-level `exports = [...]` before the
first table is accepted too. `name` is required; defaults are
version `0.1.0`, language `0.8`, source `src`, entry `<source>/main.spr`.
Unknown keys/tables, duplicate fields/project tables, duplicate bin/dependency
names and wrong value kinds are manifest errors. This intentionally small TOML
subset supports one-line quoted strings/arrays, comments, trailing array
commas and escapes for quote, backslash, newline, carriage return and tab.
Other TOML constructs/escapes are rejected. Metadata semantic errors currently
point to line 1; parse errors point to the offending line.

`--bin tool` selects a bin for `check`, `build` and `run`; a declared project
entry supplies the default. With several bins and no declared entry, `check`
without a file checks every bin (diagnostics from a module the bins share are
reported once, and `--json` lists the bins in `bins`), while `build` and `run`
require `--bin`, and the `SPR-PROJECT-ENTRY` hint names the choices. An
explicit `.spr` file always wins over the entry; inside the source root it still
compiles against the locked dependencies. A file and `--bin` together are an
option error (exit 2). `source` is
metadata, not a sandbox; local file imports keep their v0.7 relative-path
semantics. External package imports use `@alias/module.spr`; aliases are
package-local and logical paths must be listed in the dependency exports.
Project check/build/run requires a current generated `sprig.lock`; run
`sprig resolve` after manual manifest changes, or use `sprig add`/`sprig remove`
to edit a dependency and resolve the lock immediately. Local source edits do
not stale a lock.
See [dependencies](dependencies.md) before declaring a dependency, and
[bundles](bundle.md) for `sprig build --bundle`, which packages a program with
its own Java runtime for a machine that has no JDK.
