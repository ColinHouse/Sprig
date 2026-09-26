# Project manifests (implemented development contract)

`sprig init` creates a manifest and `src/main.spr` without overwriting files.
`project --json` searches upward for the nearest `sprig.toml`. `check/build/run`
without a source use its entry; an explicit file always bypasses discovery.

```toml
exports = ["public.spr"]
[project]
name = "example"
version = "0.1.0"
language = "0.8"
source = "src"
entry = "src/main.spr"

[[bin]]
name = "tool"
entry = "src/tool.spr"
```

Only `[project]`, `[[bin]]`, `[[dependency]]` and `[[jvm]]` are accepted.
All fields shown above are strings; `exports` is a root-level string array
and therefore appears before any table. `name` is required; defaults are
version `0.1.0`, language `0.8`, source `src`, entry `<source>/main.spr`.
Unknown keys/tables, duplicate fields/project tables, duplicate bin/dependency
names and wrong value kinds are manifest errors. This intentionally small TOML
subset supports one-line quoted strings/arrays, comments, trailing array
commas and escapes for quote, backslash, newline, carriage return and tab.
Other TOML constructs/escapes are rejected. Metadata semantic errors currently
point to line 1; parse errors point to the offending line.

`run --bin tool` selects a bin. Multiple bins without a declared project entry
require `--bin`; a declared entry supplies an explicit default. `source` is
metadata, not a sandbox; local file imports keep their v0.7 relative-path
semantics. There is no dependency export boundary until a resolver exists.
See `DEPENDENCIES.md` before declaring a dependency.
