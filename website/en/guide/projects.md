# Projects

A project is a directory with a `sprig.toml` manifest. Single-file mode is
unchanged and permanent: `sprig run hello.spr` never needs a manifest.

## Layout and defaults

```text
project/
├── sprig.toml
├── src/
│   └── main.spr
└── build/
```

```toml
[project]
name = "hello"
version = "0.1.0"
language = "0.8"
```

`source` defaults to `src` and `entry` to `src/main.spr`; both can be
overridden in `[project]`. Package identity comes from the manifest — Sprig
source files do not declare `package`.

## Creating and inspecting

```bash
sprig init          # creates sprig.toml and src/main.spr, never overwriting
sprig project       # human-readable summary
sprig project --json
```

`project --json` reports root, name, version, language, source root, entry,
binaries, exports, manifest and lockfile paths, lock status and declared
dependencies, so agents do not have to parse TOML themselves.

## Running a project

```bash
sprig check         # checks the project entry
sprig build
sprig run
sprig run --bin server
sprig run path/to/file.spr   # explicit file always wins over discovery
```

Discovery walks upward from the current directory. `[[bin]]` declares named
entries:

```toml
[[bin]]
name = "server"
entry = "src/server.spr"
```

## Dependencies

Local and Git Sprig dependencies resolve for real. Edit `sprig.toml`, then run
`sprig resolve`:

```toml
[[dependency]]
name = "math"
path = "../math"
```

```toml
[[dependency]]
name = "math"
git = "https://example.com/math.git"
branch = "main"
```

- `name` is the package-local import alias; the dependency's own
  `[project] name` is separate identity metadata.
- `sprig resolve` writes a deterministic `sprig.lock` (commit it). `check`,
  `build` and `run` refuse a missing or stale lock and never move a Git branch
  themselves — only `resolve` does.
- A Git dependency is locked to an exact commit SHA; a later branch move does
  not change a locked build.
- Import exported modules with `import "@math/vector.spr" as vector`. Only
  modules listed in the dependency's `exports` are importable; paths are
  canonicalized and cannot escape the dependency source root.
- `--offline` uses the Git cache only (`~/.sprig/git`); a missing cached
  revision fails with `SPR-DEP-OFFLINE`.
- Cycles and duplicate aliases are rejected with structured diagnostics.

**Maven/JVM dependencies are not implemented**: declaring `[[jvm]]` fails with
`SPR-DEP-MAVEN`, and third-party jars still need explicit `--classpath`. See
[Known limitations](/en/reference/KNOWN_LIMITATIONS).
