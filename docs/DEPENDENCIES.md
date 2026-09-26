# Dependency contract — compiler v0.3.0-alpha.1 / language v0.8-dev

`sprig resolve` is the only command that writes `sprig.lock` or performs Maven
network requests. `check/build/run/api/doctor` consume one verified project classpath.
A source file explicitly outside the discovered project source root remains standalone.
The public v0.2 release did not implement Maven; these rules describe the v0.3 source.

## Sprig packages

```toml
[[dependency]]
name = "math"
path = "../math"

[[dependency]]
name = "remote"
git = "https://example.invalid/math.git"
branch = "main"
```

Aliases are package-local. `import "@math/vector.spr" as vector` sees only direct
aliases and exported modules. Lock edge IDs such as `root/@a/@util` distinguish
same-named dependencies in diamond graphs. Alias components use UTF-8 form encoding.
Local locks store canonical absolute paths: they are development dependencies and
must be resolved again after relocation. Source edits do not stale the lock;
manifest edits do. Absolute package imports, `..` and symlink escapes are rejected.
Relative file imports are not a general filesystem sandbox.

Only resolve follows Git branch intent. Builds consume exact SHA and verified
clean detached checkouts under `~/.sprig/git`; tracked bytes/POSIX owner-execute modes, ignored and
untracked contents and cache marker are verified. Index flags do not bypass checks.
Checkout disables automatic newline conversion and enables real symlinks; platforms
without symlink capability fail explicitly for packages requiring them. POSIX mode
checks apply where that attribute view exists; Windows ACL execute rights are not
a Git executable bit. OS file locks serialize installation. Offline mode never fetches or follows a
branch; Git must still be available to verify cached content. Credentialed URLs,
submodules, authentication and dependency build hooks are unsupported.

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
