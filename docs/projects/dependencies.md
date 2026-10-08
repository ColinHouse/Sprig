# Dependency contract (main, after v0.7.1-beta.1)

`sprig resolve`, `sprig add` and `sprig remove` are explicit dependency
resolution commands: they may write `sprig.lock` and follow branches and tags.
`check/build/run/api/doctor` consume one verified project classpath and never
change the lock; when the Git cache lacks a locked commit, they clone exactly
that commit (`--offline` makes that an error instead).
A source file explicitly outside the discovered project source root remains standalone.
The public v0.2 release did not implement Maven. These rules describe the
Maven/JVM dependency support first shipped in v0.4.0-alpha.1 and remains part of
the v0.7.1-beta.1 SDK.

## Sprig packages

```toml
[[dependency]]
name = "math"
path = "../math"

[[dependency]]
name = "remote"
git = "https://example.invalid/math.git"
branch = "main"

[[dependency]]
name = "json-codec"
git = "https://github.com/ColinHouse/Sprig.git"
tag = "REPLACE_WITH_A_PUBLISHED_TAG"
subdir = "libraries/sprig-json-codec"
```

Aliases are package-local. `import "@math/vector.spr" as vector` sees only direct
aliases and exported modules. Lock edge IDs such as `root/@a/@util` distinguish
same-named dependencies in diamond graphs. Alias components use UTF-8 form encoding.
Local locks store portable identity: a relative manifest declaration is recorded as a
normalized owner-relative locator (`portable = true`), so the lock survives moving the
whole workspace unchanged. An absolute declaration keeps a canonical absolute path
(`portable = false`) and needs `resolve` after the target moves. Canonical filesystem
paths are runtime facts only; the lock never pins the source tree or a symlink target.
Schema 5 is current. Schemas 1–4 are rejected with `SPR-PROJECT-LOCK-SCHEMA`;
run `sprig resolve` to write a new lock (there is no automatic migration).
The lock records the compiler version and consumers reject a mismatch, so a
compiler upgrade requires explicit resolution. Source edits do not stale the
lock; manifest edits and locator changes do. Manifest digests read CRLF line
endings as LF, so a checkout that converts newlines (Git `core.autocrlf` on
Windows) keeps a lock written on another platform current. Absolute package
imports, `..` and symlink escapes are rejected.

Relative file imports are not a general filesystem sandbox.

Git dependencies may declare one ref intent: `branch`, `tag`, or `rev`. Branch
defaults to `main` for existing manifests. A `rev` is a full 40-character
commit SHA. Branch and tag names are resolved by `resolve`, `add` or `remove`;
`rev` selects its exact SHA. Every lock stores the resulting full commit SHA.
`subdir` is optional and relative to the repository root; it selects the
package directory containing `sprig.toml`. It is normalized to forward
slashes, rejects absolute paths and `..`, and cannot traverse symlink
components. Omitted `subdir` and `subdir = "."` both select the repository
root. Different package directories in one repository are separate dependency
edges and each lock entry records its selected subdirectory.

The optional Git `subdir` field is omitted for the repository root; an absent
field means `.`. Schema 5 retains that representation. Schema-4 locks are
rejected along with older schemas and must be regenerated explicitly. Consumers
verify the selected package manifest and exact locked revision.

Explicit resolution (`resolve`, `add` and `remove`) follows mutable Git ref intent.
Builds consume exact SHA and verified
clean detached checkouts under `~/.sprig/git`; tracked bytes/POSIX owner-execute modes, ignored and
untracked contents and cache marker are verified. Index flags do not bypass checks.
Checkout disables automatic newline conversion and enables real symlinks; platforms
without symlink capability fail explicitly for packages requiring them. POSIX mode
checks apply where that attribute view exists; Windows ACL execute rights are not
a Git executable bit. OS file locks serialize installation. Offline mode never fetches or follows a
branch or tag; Git must still be available to verify cached content. Credentialed URLs,
URLs with query/fragment data, submodules, authentication and dependency build hooks are unsupported.

## Editing dependencies

`sprig add` and `sprig remove` update one dependency declaration and resolve a
candidate project through the same resolver used by `sprig resolve`. A
successful operation writes a matching lock. If resolution or lock publication
fails, the original manifest is restored. The editor preserves unrelated
manifest lines, comments and table order; it does not rewrite the file through
a formatter.

```sh
sprig add math --path ../math
sprig add json-codec --git https://github.com/ColinHouse/Sprig.git \
  --tag REPLACE_WITH_A_PUBLISHED_TAG --subdir libraries/sprig-json-codec
sprig add --jvm org.apache.commons:commons-text:1.12.0
sprig remove math
sprig remove --jvm org.apache.commons:commons-text
```

The Git tag above is a placeholder; replace it with a real tag that contains
the package. `add` and `remove` accept `--offline` and `--json`. Offline edits
reuse exact Git revisions from the existing lock when their URL, ref intent
and package subdirectory still match, and fail when a new dependency is not
already cached. `remove --jvm GROUP:ARTIFACT` removes direct declarations for
that group and artifact, including every manually declared version;
versions are exact declarations, not ranges. Repeating an existing add is an
error so dependency changes remain explicit.

These commands install dependencies from path, Git and Maven sources, and
`sprig add NAME` without a source looks the name up in a package registry
(below). There is no authentication.

## Package registries

A registry is an index of where packages live, not a new way to fetch code. It
is a directory with an optional `registry.toml` and one `packages/NAME.toml` per
package:

```toml
[package]
name = "json-codec"
description = "Path-aware JSON decoding and encoding over @std/json"
git = "https://github.com/ColinHouse/Sprig.git"
license = "Apache-2.0"
owners = ["ColinHouse"]
subdir = "libraries/sprig-json-codec"

[[release]]
version = "0.7.1-beta.1"
tag = "v0.7.1-beta.1"
rev = "b7fe68eb1cc98cb018f1d07e472d69d73acf4328"
```

Each release is a SemVer version (`MAJOR.MINOR.PATCH`, optional `-pre` and
`+build`) with one Git ref: a `tag`, usually with the full commit `rev` it
pointed at when it was published, a `branch`, or a bare `rev`. The newest
release is chosen by SemVer order, not by list position. A release may carry
`yanked = "true"` and a `reason`: it is never chosen for a new dependency, and a
lock that already pins it still resolves. A project declares the registries it uses with
`[[registry]]` tables, each with a `name` (what `--registry NAME` refers to)
and a local `path` or a Git `url` (with an optional `branch`, default `main`,
and `subdir`):

```toml
[[registry]]
name = "team"
path = "../registry"

[[registry]]
name = "sprig"
url = "https://github.com/ColinHouse/Sprig.git"
subdir = "registry"
```

Without any `[[registry]]`, the default registry is the `registry/` directory
of the Sprig repository, which lists the first-party libraries. An index whose
`registry.toml` says `moved_to = "URL"` (with an optional `moved_to_subdir`) is
followed once, so an index can move without breaking older SDKs.

### The default registry's rules

The repository's `registry/registry.toml` declares `strict = "true"`; a strict
index enforces, both in the compiler and in the `Registry` workflow that runs on
every pull request to `registry/`:

1. **Names.** Lowercase letters, digits and hyphens, starting with a letter.
   `std`, `sprig` and the repository's own names are reserved.
2. **Releases are immutable.** `sprig publish --tag T` records the commit the
   tag points at. A published version never changes its ref or `rev` and is
   never deleted; a broken release gets `yanked = "true"` with a reason
   (`sprig publish --registry DIR --yank V --reason TEXT`). Publishing a version that is
   already listed is an error.
3. **Tags only.** A `branch` is mutable, so the default registry rejects it.
   Path and private registries still accept it.
4. **Versions** are SemVer; the newest is chosen by SemVer order.
5. **License.** `license = "SPDX-ID"` is required (`--license`, or `[project]
   license` in the package's `sprig.toml`).
6. **Owners.** `owners = ["github-handle", ...]` is recorded at the first
   publish (`--owner`). A later change to the entry comes from an owner; another
   author's pull request is flagged and needs the maintainer approval that
   `CODEOWNERS` already requires for `registry/`.

Publishing a package means opening a pull request against `ColinHouse/Sprig`
that changes only `registry/packages/NAME.toml`
(`?template=registry_submission.md` on the new pull request URL loads the
checklist). The workflow parses every entry, diffs the changed entries against
the base branch for rules 2 and 6, clones each new release at its tag, checks
that the tag still points at the recorded commit, and runs `sprig resolve`,
`sprig check` and, when the package has tests, `sprig test` with the current
SDK. A pull request that does not touch `registry/` passes the workflow at
once, and a registry-only pull request passes the compiler matrix at once, so
every required check reports. `python3 scripts/internal/check-registry-index.py
[--base REF] [--fetch]` runs the same validation locally.

- `sprig search [TEXT] [--registry R] [--offline] [--json]` lists the packages
  the registries know (name, latest version, source, description), filtered by
  a text found in the name or description.
- `sprig add NAME [--version V] [--registry R]` looks the name up and writes
  the ordinary Git dependency the index names: `git`, the release's `tag`,
  `branch` or `rev`, and `subdir`. The manifest then carries the full source,
  the lock pins the commit, and `check`/`build`/`run` never consult the
  registry again. `--version` picks a listed release; `--registry` chooses when
  several registries list the name. A yanked release is skipped as the latest
  and refused by version; a tag that no longer points at the recorded `rev` is
  refused (`SPR-DEP-REGISTRY`), since a published release is immutable.
- `sprig publish --registry DIR (--tag T [--rev SHA] | --branch B | --rev SHA)
  [--git URL] [--subdir DIR] [--version V] [--description TEXT] [--license SPDX]
  [--owner HANDLE]...` writes the current package's `packages/NAME.toml` in a
  local registry directory (a declared `path` registry by name, or any
  directory) and records the commit a `--tag` points at (from the package's own
  checkout, or from the remote). The command stops at the entry: it prints the
  pull request step, and a Git registry is published through a local clone of
  it. A version that is already listed is an error; `--yank VERSION --reason
  TEXT` withdraws a release instead.

A Git registry is read at `add`/`search` time (its branch tip is pinned under
`~/.sprig/registry` so `--offline` reuses it); an unlisted package or version,
an unreadable index or an ambiguous name is `SPR-DEP-REGISTRY`. There is no
central hosted registry, no authentication and no upload.

## Maven / JVM

```toml
[[jvm]]
group = "org.apache.commons"
artifact = "commons-text"
version = "1.12.0"
```

Apache Maven Resolver 1.9.24 and Maven model provider 3.9.11 build effective POM
models (parents, properties, imported BOMs), apply exclusions/optional/scope and
nearest-version conflict mediation. Compile/runtime JARs form the classpath;
test/provided and optional transitive dependencies do not. Missing/invalid POMs and failed Maven repository checksum verification
fail; a directly downloadable JAR is insufficient evidence of a complete graph.
No Maven CLI, plugins, build hooks or annotation processors execute. Exact direct
release versions are mandatory; snapshots, LATEST/RELEASE and direct ranges fail.
Transitive Maven version mediation belongs to Resolver; selected versions are pinned.
Conflicting direct versions across Sprig packages fail rather than silently override.
Direct POM relocation is rejected during resolve before writing a lock: declare
the new exact coordinate explicitly. Transitive relocations belong to Resolver.
Dependency-declared extra repositories are ignored; Central is the default.

`SPRIG_MAVEN_REPOSITORY` selects one HTTPS or file repository without credentials,
query or fragment (useful for private mirrors and deterministic fixture tests).
`SPRIG_MAVEN_CACHE` overrides `~/.sprig/maven`. A cooperative process lock protects
Resolver/model storage and atomic artifact publication. Repository URL hashes
isolate origins in the staging cache. Previously verified staging JAR/POM bytes
are checked against a per-origin SHA-256 marker before re-locking; initial staging
reuse also requires a valid repository checksum sidecar. A damaged staging cache
cannot be silently blessed as a new lock. There is no authentication
or repository-list configuration in this first pass.

Schema **5** records selected coordinates (extension/classifier), direct roots,
resolved graph edges, classpath order, repository provenance and SHA-256 of JARs
and effective-model POM inputs. These Maven lock fields were introduced in schema
3 and remain part of schema 5. Schemas 1–4 are rejected: run resolve explicitly.
An immutable-by-contract content cache stores files by digest. Every consumer
verifies the locked hashes; missing bytes fail `SPR-DEP-OFFLINE`, changed bytes
fail `SPR-DEP-CHECKSUM`. Consumers do not re-resolve graphs or silently fetch.
After relocating an SDK/project with only Maven dependencies, warm the global
cache with resolve. Lock files are not signatures or a remote trust guarantee.

Order: JDK/runtime, locked JARs in recorded order, then explicit repeated
`--classpath` values (platform path separator). Compiler implementation libraries
are isolated from application imports. First application entry wins duplicate
class names consistently in reflection/javac/JVM; conflicting duplicate classes
are not independently scanned. Use `sprig doctor --json` and `sprig deps --json`
to inspect the actual classpath and graph. Maven libraries retain their own Java
numeric, nullability and exception contracts.

## Limits

A hosted central registry, Maven plugins, dependency authentication, non-JAR
runtime artifacts and full Java generic/array/varargs adapters are not
implemented; package registries are Git or local directory indexes (above).
Git cache materialization waits up to five seconds for its cooperative process
lock, then reports `SPR-DEP-GIT` with retry guidance; it never steals the lock.
The Maven cache lock still uses a blocking cooperative wait. Hostile concurrent
mutation after validation is outside the cache model. POM profile activation can depend on the resolution
JDK/OS; the lock freezes the chosen result, not an environment-independent model.

Verification: `python3 tests/maven/check_resolver.py` creates independent local
parent/BOM/conflict/scope fixtures and exercises all classpath consumers/cache
failures. `examples/showcases/maven_slug` supplies a separate real Central example.

## Bundled standard package

`import "@std/files.spr" as files` uses the installed SDK without a manifest
dependency. `std` is a reserved alias. `@std` is SDK/compiler identity, not a
project-selected dependency: it cannot be independently resolved or pinned and
is not recorded in `sprig.lock`. The installed SDK supplies its contents.
Changing bundled std bytes alone does not stale or rewrite a project lock.
Consumers do require the lock's compiler version to match, so upgrading to a
different compiler version requires `sprig resolve`. Published SDK archive
checksums and extracted-archive smoke tests cover distribution integrity; the
project lock no longer pins the exact installed std bytes. See
[standard library](../projects/standard-library.md) and the
[published release validation record](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/validation.md).
