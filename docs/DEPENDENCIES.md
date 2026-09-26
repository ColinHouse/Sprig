# Dependency boundary (not implemented)

The manifest records these declarations, but does not resolve them:

```toml
[[dependency]]
name = "math"
path = "../math"

[[dependency]]
name = "remote"
git = "https://example.invalid/math.git"
branch = "main"

[[jvm]]
group = "org.apache.commons"
artifact = "commons-lang3"
version = "3.18.0"
```

A Sprig dependency requires a unique name and exactly one nonempty `path` or
`git`; `branch` is only valid with `git`. JVM coordinates require nonempty
strings and an exact version, not ranges, wildcard versions, LATEST or RELEASE.
`deps --json` returns `resolved: false`, `SPR-PROJECT-UNSUPPORTED`, exit 2.
Project-based `check/build/run` refuse any unresolved declarations (exit 1).
Explicit-file compilation bypasses the manifest, and JVM libraries can still
be supplied manually using the same `--classpath` to `api/check/build/run`.

There is no `resolve` command, `@package/...` import namespace, lockfile digest
or checksum validation, Git pinning, transitive Maven graph, cache, strict
offline resolver, export enforcement or dependency conflict policy. A physical
`sprig.lock` file is merely reported as present, never validated. No command
updates a dependency graph or silently follows a Git branch. SDK smoke tests
that need no network do not establish offline dependency resolution.
