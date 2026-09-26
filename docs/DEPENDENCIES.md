# Local/Git dependency contract (v0.8-dev)

The executable Catalog and behavior define current capabilities. Local/Git
packages, package-local aliases, exports, `sprig resolve`, schema-2 `sprig.lock`
and offline builds are implemented. Maven/JVM resolution, project-aware Maven
classpath, registry, custom repositories and package publishing are not implemented.
Third-party JVM libraries still require explicit `--classpath`.

```toml
[[dependency]]
name = "math"
path = "../math"

[[dependency]]
name = "remote"
git = "https://example.invalid/math.git"
branch = "main"
```

Run `sprig resolve` after manifest changes. Only resolve writes the lock.
Check/build/run load exact dependency edges such as `root/@a/@util` and
`root/@b/@util`. Each entry records `id`, `owner`, local alias `name`, kind,
manifest digest and local path or exact Git commit. Diamond graphs retain
separate edge records; project names and aliases are not global identities.
Schema 1 is rejected with instructions to resolve again; no silent migration.
Local canonical absolute paths are nonportable development dependencies.
Local source edits require no resolve; manifest edits stale the lock.

`import "@math/vector.spr" as vector` sees only direct aliases of its package.
Exports use normalized logical module paths. Absolute paths and `..` are
rejected; real target and real source root enforce symlink confinement.
Internal symlinks are allowed when their logical name is exported and their
real target stays inside the source root. Relative file imports retain the
existing language contract and are not a general filesystem sandbox.

Git branch intent is read only by resolve. Builds use the locked SHA, bare
object cache and detached checkout in `~/.sprig/git`. Reuse verifies exact
HEAD, clean tracked/untracked/ignored contents and marker consistency.
Tracked file bytes and executable/symlink modes are compared to the locked
commit tree, including files hidden by Git index flags such as assume-unchanged.
Tampering raises `SPR-DEP-GIT`; remove the corrupted checkout and resolve
again. A marker alone is never trusted. Per-repository OS file locks serialize
cache installation; unique temporary directories are verified before atomic
rename (safe rename fallback where atomic move is unavailable).

`--offline` never queries a branch, clones or fetches. Existing locks still
validate manifests and checkout integrity. Cache misses fail explicitly.
Credentialed Git URLs are unsupported; credentials are removed before storage
and diagnostics. No authentication system or dependency build hooks exist.
