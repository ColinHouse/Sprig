# Projects

For a single file, `sprig run hello.spr` is all you need. Once the code grows, or you want to use someone else's package or a Java library, it's time for a project: a directory with a `sprig.toml` in it.

## What a project looks like

`sprig init my-tool` creates two files:

```text
my-tool/
├── sprig.toml        # project settings
└── src/
    └── main.spr      # entry point
```

After `sprig resolve` you'll also have a lock file, `sprig.lock`. `sprig build` puts its output in `sprig-build/`, and tests go under `tests/`.

`sprig.toml` starts out like this:

```toml
[project]
name = "my-tool"
version = "0.1.0"
language = "0.8"
```

- The source directory defaults to `src` and the entry point to `src/main.spr`. You can change them with `source` and `entry` in `[project]`.
- The package's identity comes from `sprig.toml`; `.spr` files don't declare a `package`.
- Misspelled fields, repeated fields and values of the wrong kind are all rejected.

## Everyday commands

You can run these from any subdirectory of the project; Sprig walks up until it finds `sprig.toml`:

```bash
sprig check                  # checks the entry point and every file it imports
sprig run                    # checks and runs
sprig build                  # generates Java and class files
sprig test                   # runs the tests under tests/
sprig project                # shows project information
sprig run path/to/file.spr   # a file you name explicitly always wins
```

`sprig project --json` reports the project's root, name, version, entry point, lock status and dependencies, so scripts and AI assistants don't have to parse TOML themselves.

Each `.spr` file under `tests/` is an ordinary program, and `sprig test` runs each one in its own JVM. Files under `tests/compile_fail/` are supposed to fail to compile, and a `.expect.toml` file next to each one lists the expected error codes. See [testing projects](/en/reference/tooling/testing).

## More than one entry point

A project can have several runnable entry points, declared with `[[bin]]`:

```toml
[[bin]]
name = "server"
entry = "src/server.spr"
```

Run it with `sprig run --bin server`; `sprig check --bin server` and `sprig build --bin server` work on that one bin too. If you declare several bins and the project has no `entry` of its own, a plain `sprig check` checks every bin, while `build` and `run` handle one program and need `--bin`. You can also name a file in the project directly (`sprig check src/server.spr`); it still compiles against the project's dependencies.

## Adding dependencies

The easiest way is `sprig add`. It edits `sprig.toml` and updates the lock file right away:

```bash
sprig add math --path ../math                                     # a Sprig package in a local directory
sprig add math --git https://example.com/math.git --branch main   # a Sprig package in a Git repository
sprig add --jvm org.apache.commons:commons-text:1.12.0            # a Java library from Maven
sprig remove math
```

`add` and `remove` only touch the dependency block in question and leave the rest of `sprig.toml` as it was.

You can also edit `sprig.toml` by hand and run `sprig resolve` afterwards. The three kinds of dependency look like this:

```toml
# A local directory
[[dependency]]
name = "math"
path = "../math"
```

```toml
# A Git repository: pick one of branch, tag or rev; subdir is optional and
# names the package's directory inside the repository
[[dependency]]
name = "math"
git = "https://example.com/math.git"
branch = "main"
```

```toml
# Maven: exact versions only
[[jvm]]
group = "org.apache.commons"
artifact = "commons-text"
version = "1.12.0"
```

`name` is the name you import the package by in your own code. It has nothing to do with the `name` in the dependency's own `[project]`. Different packages can use the same name for a dependency, but one package can't use a name twice.

## Using modules from a dependency

A dependency lists, in its own `sprig.toml`, which modules other packages may import:

```toml
[project]
name = "math"
version = "0.1.0"
language = "0.8"
exports = ["vector.spr"]
```

In your project, import them with `@` and the dependency's name:

```sprig
import "@math/vector.spr" as vector

print(vector.length_squared(3, 4))
```

Only modules listed in `exports` can be imported. Paths are normalized first, so `..` can't take you outside the dependency's source directory.

### Adding from a registry

Without `--path`, `--git` or `--jvm`, `sprig add` looks the name up in a package registry:

```bash
sprig search json            # what the registries list, with the latest version
sprig add json-codec         # written as an ordinary Git dependency (git, tag, subdir); the lock pins the commit as usual
sprig add json-codec --version 0.7.1-beta.1
```

A registry is only an index: a directory with one `packages/NAME.toml` per package naming its Git repository, subdirectory and the tag of each release. It is not a new way to fetch code; after `add` the manifest holds the full Git dependency and `check`/`run` never consult the registry again. A project declares its registries with `[[registry]]` tables (a local `path` or a Git `url`); without any, the default is the `registry/` directory of the Sprig repository, which lists the first-party libraries.

Each release is a SemVer version with one Git ref, usually a tag plus the commit (`rev`) it pointed at when it was published. The newest release is chosen by SemVer order, not list position. A yanked release is never chosen for a new dependency, while a project that already locked it still resolves; a tag that moved away from the recorded commit is refused, because a published release is immutable.

### Publishing a package

The default registry is the `registry/` directory of the Sprig repository, so publishing a package means opening a pull request against `ColinHouse/Sprig` that changes only `registry/packages/NAME.toml`, validated by the Registry workflow:

```bash
cd my-package
sprig publish --registry ../Sprig/registry --tag v1.0.0 --license Apache-2.0 --owner your-github-handle
```

`publish` writes the entry into the local index directory and records the commit the tag points at; committing that file and opening the pull request is the next step. The default registry is strict: names are lowercase letters, digits and hyphens; every release is a tag pinned to a commit (no branches); `license` (an SPDX identifier) and `owners` (GitHub handles) are required; a published version is never changed or deleted, only withdrawn with `sprig publish --yank 1.0.0 --reason "why"`. A change to an existing entry comes from one of its owners or needs maintainer approval. The workflow clones each new release at its tag, checks the commit, and runs `sprig resolve`, `sprig check` and `sprig test` with the current SDK.

An unlisted package or version is `SPR-DEP-REGISTRY`; `sprig search` shows what is there. The [dependency contract](/en/reference/projects/dependencies) has the details.

## The lock file

`sprig resolve` (and `add` and `remove`) writes `sprig.lock`, which records the exact version and checksum each dependency resolved to.

- **Commit it.** That way everyone who builds the project gets exactly what you got.
- **Only three commands change it.** `check`, `build` and `run` just read the lock file. If it's missing or doesn't match `sprig.toml`, they stop with an error instead of resolving dependencies on their own. Only `resolve`, `add` and `remove` update it.
- **Git dependencies are locked to a commit.** Wherever the branch moves later, a locked build stays the same. To pick up changes, run `sprig resolve` again.
- **Resolve again after switching SDKs.** The lock file records the compiler version. With a different one, `check` reports `SPR-PROJECT-LOCK-STALE`; running `sprig resolve` once fixes it.
- **The lock format is version 5.** Local dependencies declared with relative paths are stored as relative locations (`portable = true`), so you can move the whole workspace; absolute paths are stored as `portable = false`. The bundled `@std` library comes from the SDK you installed and isn't written to the lock file.
- **v0.5.0-beta.1 used version 4.** That version also recorded the `@std` version in the lock file. After upgrading, run `sprig resolve` once to rewrite an older lock file as version 5.

## Working offline

- With `--offline`, Sprig uses only its local caches. The Git cache lives in `~/.sprig/git`; if the version you need isn't cached, you get `SPR-DEP-OFFLINE`.
- Resolving Maven dependencies the first time needs a network connection. After that, a complete cache is enough to build offline.
- The Git cache is verified each time it's used (HEAD, a marker file, and tracked and untracked files); if anything was tampered with, you get an error.
- Symbolic links are checked by their real path, so they can't lead outside the dependency's directory. Links that stay inside the dependency and are exported are allowed.

## More about Maven dependencies

Maven dependencies are resolved with Apache Maven Resolver, which handles parent POMs, BOMs and transitive dependencies. The lock file records the SHA-256 of every JAR and POM, the dependency graph and the classpath order. After that, `check`, `build`, `run`, `api` and `doctor` use those JARs automatically, so you never have to locate files by hand, and none of them resolve dependencies again.

Dependency cycles and duplicate names are rejected with structured errors. The [dependency notes](https://github.com/ColinHouse/Sprig/blob/main/docs/projects/dependencies.md) have the complete rules.

## You don't always need a project

A `.spr` file that isn't inside any project runs as-is, with no `sprig.toml`. If the file sits inside a project's source directory, though, Sprig treats it as part of that project: it uses the project's dependencies and needs an up-to-date lock file.
