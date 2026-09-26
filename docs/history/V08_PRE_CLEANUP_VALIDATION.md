> Historical validation at pre-cleanup tree. Superseded by [current validation](../post-v0.7/V08_VALIDATION_REPORT.md).

# Sprig v0.8 completion — dependency system validation report

**Tree:** branch `v0.8-dependency-system` from `cb2b4e7` (merged PR #8).
**Target:** language v0.8, compiler `0.2.0-alpha.1` (not released; draft notes
only). **Status: READY FOR INDEPENDENT RELEASE AUDIT** — no tag or Release was
created by this round.

Baseline frozen before changes: `cb2b4e7`, 117/117 tests, numeric 66/66,
grammar SHA-256 `acff6111…` (lexer) / `c63aaaa7…` (parser). Current tree:
**118/118** (`scripts/test.sh`), dependency resolver **21/21**, project model
**18/18**, numeric 66/66, grammar harness 21/21, docs gate 18/18 + VitePress
build, SDK archive offline smoke passed.

## 1. Is a local path dependency actually usable?

Yes. `[[dependency]] name = … path = "../lib"` is resolved recursively:
the dependency's own `sprig.toml`, source root, exports and dependencies are
loaded, paths are canonicalized, and the dependency source modules compile
through the normal pipeline. Dependency `name` is the package-local import
alias; the dependency's `[project] name` is separate identity metadata and both
are recorded in `sprig.lock`. Evidence: `tests/project_deps/check_deps.py`
(local, transitive, cycle, scope).

## 2. Does `@package/...` import work?

Yes. `import "@math/vector.spr" as vector` resolves through the importing
package's declared aliases only. Transitive aliases are not visible: the root
cannot import `@b/…` unless it declares `b` itself (`alias-scope-is-package-
local` check), while an intermediate package can import its own direct
dependency. Relative `./…` and Java imports are unchanged.

## 3. Are exports enforced?

Yes. `exports = [...]` (inside `[project]` or top level) controls which modules
external consumers may import. Non-exported modules report
`SPR-PROJECT-NOT-EXPORTED` with the alias, module and export list in the JSON
data. Import paths are normalized and canonicalized; `..`, absolute paths and
symlink escapes out of the dependency source root report `SPR-DEP-NOT-FOUND`.
Cycles report `SPR-DEP-CYCLE` with the chain in the diagnostic data, and
duplicate aliases are rejected at manifest parse time.

## 4. Is `sprig.lock` deterministic?

Yes. `sprig.lock` is generated only by `sprig resolve`, uses sorted entries and
a stable renderer; two consecutive resolves produce byte-identical files
(checked in both suites). The schema records lock version, language, compiler,
root manifest SHA-256, and per dependency: alias, kind, canonical path or
Git URL, requested ref, resolved revision, dependency project name, source
root and dependency manifest SHA-256. Unknown `lock-version` values are
rejected with `SPR-PROJECT-LOCK-SCHEMA`.

## 5. Is a stale lock rejected?

Yes. `check`, `build`, `run` and `deps` require a current lock:
- no lock → `SPR-PROJECT-LOCK-MISSING` (hint: run `sprig resolve`);
- root manifest changed → `SPR-PROJECT-LOCK-STALE`;
- dependency manifest changed → `SPR-PROJECT-LOCK-STALE` for that alias;
- Git URL/ref changed in the manifest → stale entry.

Nothing auto-resolves, and no command rewrites the lock. Single-file mode is
unaffected: `sprig run hello.spr` outside a project needs no lock.

## 6. Is a Git branch locked to a SHA?

Yes. `sprig resolve` runs `git ls-remote <url> refs/heads/<branch>`, stores the
exact commit in `sprig.lock`, and materializes that revision into
`~/.sprig/git/checkouts/…` (bare cache in `~/.sprig/git/repos/…`, verified
`HEAD == revision`). Build/check/run never read the branch; the URL is stored
without credentials and displayed redacted.

## 7. Does a locked build survive a branch move?

Yes, verified with a real local bare remote: after the remote branch advanced
from commit A to C, `sprig run` still produced A's result; `sprig resolve`
updated the lock to C and then `run` produced C's result. Offline runs from
cache behaved identically.

## 8. Do Maven direct/transitive dependencies work?

**No — this is the release blocker.** `[[jvm]]` coordinates are parsed and
validated (exact versions only), but artifact resolution is not implemented;
`sprig resolve` fails loudly with `SPR-DEP-MAVEN` naming the coordinate, and
`capabilities` reports `mavenDependencies: false`.

Recorded blocker (per the “do not stop silently” rule):

- **Exact failing case:** `sprig resolve` with
  `[[jvm]] group = "com.fasterxml.jackson.core", artifact =
  "jackson-databind", version = "2.18.4"` → cannot produce the transitive
  artifact graph or a JVM classpath.
- **Why the current architecture cannot safely implement it in this round:**
  the instruction forbids a hand-written POM/mediation resolver, so Apache
  Maven Resolver must be used. Resolver needs a pinned runtime closure
  (resolver api/spi/impl/util/connectors/transport plus maven-model,
  maven-model-builder, repository-metadata, plexus-utils/interpolation and
  slf4j — roughly a dozen jars). `scripts/build.sh` is a curl + `javac`
  pipeline with SHA-256 pinning and no dependency mechanism; `package-alpha.sh`
  must bundle every runtime jar for the SDK archive. Wiring and validating that
  closure is a build-system change, not a small code patch.
- **Attempted/considered and rejected:** shelling out to `mvn` (adds a Maven
  requirement the plan forbids), and re-implementing POM semantics (explicitly
  forbidden).
- **Minimal decision required:** approve a pinned Resolver bundle
  (exact versions listed in the notices and verified by `build.sh`) so the
  resolver runs on JDK + SDK only, or approve an SDK layout that vendors those
  jars. Once approved, the remaining work is mechanical: collect the artifact
  graph, SHA-256 each file into `sprig.lock`, cache under `~/.sprig/maven`, and
  feed the same resolved classpath into check/build/run/api/doctor.

## 9. Do check/build/run/api share one classpath?

For Sprig dependencies, yes: a single `DependencyResolver.Result` feeds every
command (the compiler receives it as its import resolver; deps/project/doctor
read the same lock-derived model). JVM classpath sharing is **not** complete:
there are no resolved JVM dependencies to share yet, so `--classpath` remains
explicit and `projectAwareClasspath` reports `false`.

## 10. Is offline mode really zero-network?

For local and Git dependencies, yes: `--offline` gates every network-capable
path (`ls-remote`, `clone`, `fetch`), uses only `~/.sprig/git`, and fails with
`SPR-DEP-OFFLINE` naming the missing revision when the cache is incomplete.
Verified: warm-cache `run --offline` succeeded, a current lock resolved
offline without network, and an empty cache offline failed with the structured
diagnostic. Maven offline is not applicable until the blocker above is
resolved.

## 11. Is checksum corruption detected?

For the implemented surface there are no downloaded artifacts to checksum:
Git checkouts are verified by `HEAD == locked revision` plus a revision marker,
and the lock records the revision itself. Maven artifact SHA-256 recording is
part of the blocked work (question 8). Recorded as a P2 limitation: Git object
integrity relies on Git's own hashing, not an extra lockfile checksum.

## 12. Is stage-1 migrated to the project system?

**No.** The Sprig-written generic slice (`tests/visitor/generic_stack.spr`)
still exercises `Stack[T]`/`Table[K,V]`, and a small local dependency fixture
is tested, but `examples/stage1_frontend_probe/frontend.spr` has not been
split into a multi-module project that consumes `@util`-style dependencies.
This is recorded remaining work, not claimed.

## 13. Was the blind Agent test run?

**No.** It requires a frozen release-candidate SDK and an isolated agent; it is
left to the independent release audit. `help projects`/`help dependencies`
text and `project --json`/`deps --json`/`doctor --json` now expose the
information such a test needs.

## 14. Any silent miscompile?

None known. The full suite (118 checks) covers v0.7 compatibility, generics,
numerics, recovery, correctness, acceptance, agent tooling, CLI contracts,
project model and the new dependency resolver. One process-level bug was found
and fixed during development (TOML scoping polluted root lock scalars);
locking, exports and traversal behavior are covered by regression checks.

## 15. Any dependency nondeterminism?

None observed in the implemented surface: lock rendering is sorted and
byte-identical across resolves; Git builds use only the locked revision; local
dependencies are explicitly marked `portable = false` and their manifest
changes are detected as stale; no command silently upgrades anything. The
unimplemented Maven resolver is the remaining unknown and is exactly why an
independent audit is required before release.

## 16. Remaining real limitations (P2)

- Maven/JVM dependency resolution and project-aware JVM classpath (blocker).
- Git dependencies expose only `branch =` in the manifest (no tag/rev syntax),
  and credentialed URLs are not supported (credentials are stripped).
- No extra checksum pinning for Git objects beyond the locked revision.
- `sprig api`/`doctor` see project metadata but not a resolved JVM classpath.
- `frontend.spr` stage-1 migration and the blind Agent test are outstanding.
- `Comparable` and user-defined capabilities remain unimplemented
  (`SPR-GENERIC-CONSTRAINT`, `genericCapabilities: ["Equatable"]`).

## Capability state after this round

```
userGenerics=true  multipleGenericParameters=true  genericConstraints=true
genericCapabilities=["Equatable"]
packageManifest=true  lockfile=true  localDependencies=true  gitDependencies=true
packageExports=true   offlineResolution=true
mavenDependencies=false  projectAwareClasspath=false  centralSprigRegistry=false
```
