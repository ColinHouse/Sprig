# Dependency contract — compiler v0.4.0-alpha.1

`sprig resolve`, `sprig add` and `sprig remove` are explicit dependency
resolution commands: they may write `sprig.lock` and perform Git/Maven requests.
`check/build/run/api/doctor` consume one verified project classpath.
A source file explicitly outside the discovered project source root remains standalone.
The public v0.2 release did not implement Maven. These rules describe the
Maven/JVM dependency behavior implemented since v0.3 and shipped in v0.4.0-alpha.1.

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
Schema 4 is current; schema-3 locks are rejected with `SPR-PROJECT-LOCK-SCHEMA` and
require `sprig resolve` (no automatic migration). Source edits do not stale the lock;
manifest edits and locator changes do. Absolute package imports, `..` and symlink
escapes are rejected.

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

Schema 4 remains current. Its additive Git `subdir` lock field is omitted for
the repository root; an older schema-4 entry without the field means `.`.
This preserves existing root-package locks without rewriting or silently
migrating them. Consumers still verify the selected package manifest and
exact locked revision.

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

These commands install dependencies from path, Git and Maven sources. They do
not provide a package registry, package search, publishing or authentication.

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

Schema **3** records selected coordinates (extension/classifier), direct roots,
resolved graph edges, classpath order, repository provenance and SHA-256 of JARs
and effective-model POM inputs. Schema 1/2 are rejected: run resolve explicitly.
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

Publishing/registry, Maven plugins, dependency authentication, non-JAR runtime
artifacts, full Java generic/array/varargs adapters and an LSP are not implemented.
File locks have no timeout; hostile concurrent mutation after validation is outside
the cooperative cache model. POM profile activation can depend on the resolution
JDK/OS; the lock freezes the chosen result, not an environment-independent model.

Verification: `python3 tests/maven/check_resolver.py` creates independent local
parent/BOM/conflict/scope fixtures and exercises all classpath consumers/cache
failures. `examples/showcases/maven_slug` supplies a separate real Central example.

## Bundled standard package

`import "@std/files.spr" as files` uses the installed SDK without a manifest
dependency. `std` is a reserved alias. Resolve records the compiler-coupled std
version and exact module-byte digest; consumers reject missing/different metadata
with `SPR-PROJECT-LOCK-STALE`. Re-run resolve explicitly after upgrading an SDK
or migrating an older schema-3 lock. See [standard library](STANDARD_LIBRARY.md).
