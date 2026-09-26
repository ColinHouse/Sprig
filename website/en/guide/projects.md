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

## Dependencies (not implemented yet)

The manifest accepts dependency declarations, and `sprig deps --json` lists
them honestly:

```toml
[[dependency]]
name = "math"
path = "../math"

[[jvm]]
group = "com.fasterxml.jackson.core"
artifact = "jackson-databind"
version = "2.18.4"
```

Dependency resolution, `sprig.lock`, offline caches and `@name/...` imports
are **not implemented**. `sprig deps` reports
`SPR-PROJECT-UNSUPPORTED` with `resolved: false`; use `--classpath` for JVM
jars today. See [Known limitations](/en/reference/KNOWN_LIMITATIONS).
