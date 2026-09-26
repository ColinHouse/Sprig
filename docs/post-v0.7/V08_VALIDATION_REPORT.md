# Sprig v0.8-dev — current cleanup validation

**Verdict: READY FOR INDEPENDENT CLEANUP AUDIT.**

This is the sole current development validation report. Catalog plus executable
behavior define capabilities. REVIEW_REPORT.md is historical evidence with its
original SHA-specific findings preserved. No tag or release is created.

## Candidate and public state

Baseline: main `3058bd0f47d8146641af509a1da89090bc56b3d0`, verified through
GitHub API; its tree matches local merged candidate 18c2ed3. Native Git fetch
was attempted and failed due TCP connectivity; the signed GitHub commit object
was reconstructed and its SHA independently verified locally. PRs #9 and #10
are merged. The latest public prerelease remains v0.1.0-alpha.1 (2026-09-24).
Compiler metadata is 0.2.0-alpha.1, language is 0.8-dev.

Final implementation candidate: `25640c7af417bed21edca0ce01330dab3330c0c4`.
Subsequent commits update validation evidence only. The exact final repository
HEAD and its checks are available on [draft PR #11](https://github.com/ColinHouse/Sprig/pull/11)
and [its current checks](https://github.com/ColinHouse/Sprig/pull/11/checks).
The report cannot embed its own containing commit SHA; the handoff supplies that SHA.
Implementation hosted CI [36250877825](https://github.com/ColinHouse/Sprig/actions/runs/36250877825)
is success for JDK 17, JDK 26 and Documentation site on the exact implementation SHA.
The earlier main baseline CI [36248014975](https://github.com/ColinHouse/Sprig/actions/runs/36248014975)
was also success. GitHub job metadata and completed required steps were checked;
a job-log download failed due connectivity to the results receiver, so hosted
per-case counts are not claimed from that unavailable download.

## Resolver correctness

Lock schema is **2**. Each edge has `id`, `owner` and package-local `name`;
`root/@a/@util` and `root/@b/@util` are different entries. Resolve and load use
identical IDs. Missing, duplicated or inconsistent IDs/owners/aliases, invalid
SHA/digests and old schema are structured errors. Edge identity uses UTF-8
percent-encoded alias components; dotted and Unicode aliases retain their
existing behavior without restricting manifest names. Load checks edge kind, local
path, project name, source and manifest digest, with no fallback lookup by alias.
Diamonds keep separate edge records for the same physical package. Local source
edits remain live; local manifest edits stale the lock. Local paths are canonical
absolute paths and therefore nonportable, an explicitly documented alpha limit.

Exports retain normalized logical paths. Package imports reject absolute and
parent traversal and compare real target against real source root. Exported
symlinks to internal files are allowed; file and directory escapes are rejected.
Relative imports keep the language's existing behavior and are not a filesystem
sandbox.

Git reuse checks exact HEAD, marker and clean tracked, untracked and ignored
contents. File bytes and modes are also checked against locked Git blob hashes,
so assume-unchanged cannot hide tampering. Tampering fails with SPR-DEP-GIT,
including offline mode. Bare clone
and detached checkout use unique temporary directories, verify before install,
and rename atomically where supported. A per-repository FileChannel lock
serializes cross-process cache mutation; safe same-filesystem rename is the
fallback. Failure cleans temporary directories. `resolve --offline` validates
the existing graph and cache instead of merely comparing root manifest digest.

## Capability boundaries

Multi-parameter generics, Practical Strict nullable generics, leading Equatable,
local/Git dependencies, exports, exact revision lock, offline build, project
selection, JSON diagnostics and Agent tooling remain implemented. Maven is false:
no artifacts or transitive Maven graph are resolved; project-aware JVM classpath
is false. Third-party JARs still use explicit --classpath. Comparable, interfaces,
traits, variance, inference, registry, LSP and IDE integration remain unsupported.
Stage-1 is a single-file frontend probe with a separate generic data structure
experiment; full project migration and self-hosting remain future work.

## Verification

Baseline full suite: 120 top-level checks passed. The new normal-CI cleanup
suite covers duplicate scoped aliases, different targets, diamond, deterministic
edge IDs, old/corrupt locks, local source edits, ordinary exports, file/directory
symlink escapes, internal symlink, tracked/marker/untracked tampering, HEAD
mismatch and two processes materializing the same SHA from an empty cache.
Targeted cleanup suite: 36 checks passed, including index-flag bypass rejection
and dotted/Unicode alias preservation.
Final verification used separate source snapshots for JDK 17.0.19 and 26.0.1;
compiler and test files were compared with the committed candidate. Both full
commands completed with exit 0. No generic/numeric oracle or existing golden
was changed. One intermediate run used a newly added JSON-field assertion with
an older JAR; that build/test staging mismatch is excluded from final evidence.

| Gate | JDK 17 | JDK 26 |
|---|---|---|
| ANTLR generation + compiler/runtime javac | pass | pass |
| Complete scripts/test.sh | 121/121 | 121/121 |
| Semantics | 54/54 | 54/54 |
| Numeric / correctness | 66/66, 22/22 | same |
| Recovery | 467/467, including 435 prefixes | same |
| Acceptance / JSON / consistency | 52/52, 13/13, 10/10 | same |
| Agent tooling / CLI | 89/89; 19 rejected-option cases and E2E contract | same |
| Project / original dependencies | 18/18, 21/21 | same |
| New cleanup regressions | 36/36 | same |
| Generic adversarial | 61 programs, 209 prefixes, no failures | same |
| Project adversarial | 31 cases, no failures | same |
| Grammar harness | 24/24, syntax only | same |
| SDK archive smoke | pass | pass |

Documentation: 18 runnable snippets, upgraded capability gate and VitePress
production build pass. Three deliberate stale-document probes (single-parameter
only, no resolve, Maven complete) were independently rejected. Counts above are
suite-specific; 121 is a top-level aggregation and must not be added to subcounts.

```bash
./scripts/build.sh
./scripts/test.sh
python3 tests/project_deps/check_cleanup.py
python3 tests/project_deps/check_deps.py
ANTLR_JAR="$PWD/tools/antlr-4.13.2-complete.jar" ./tools/test-grammar.sh
./scripts/check-docs.sh
./scripts/package-alpha.sh
python3 tools/check-sdk-archive.py
```

Select the relevant JDK via JAVA_HOME and PATH. Build/test/package sequentially
within each checkout; use separate checkouts for parallel JDK runs. The clean
local development archive records source 25640c7; ZIP SHA-256 is
`54b5c0f2f346de04740d8792a08d1b0ac24d90a117f337e70027d366a2aade2b`.
It was smoke-tested on both JDKs and was not uploaded as a release asset.
Timestamped archives are not claimed to be byte-for-byte reproducible.

## Repaired findings

| Severity | Before | Current behavior / source |
|---|---|---|
| P1 | Global alias lookup could choose another package's lock entry | Schema-2 owner/edge identity in Lockfile and DependencyResolver.build/findLockEntry; scoped aliases and diamonds are tested |
| P1 | Lexical path confinement allowed exported symlink escapes | DependencyResolver.Result.resolve checks real root/target while retaining logical exports |
| P1 | Revision marker alone allowed modified Git source under a locked SHA | GitCache.validCheckout/matchesTree checks HEAD, marker, status, bytes and modes; dirty tracked/untracked and index-flag bypass fail |
| P1/P2 | Concurrent clone/checkout could expose or delete partial state | GitCache.materialize holds a per-repository OS lock; unique verified temporary trees are installed atomically |
| P1 | Historical public claims contradicted executable capabilities | Current document inventory and Catalog-driven consistency gate; old evidence explicitly superseded |
| P2 | Project declarations could print Git credentials | Project.toJsonMap redacts URL userinfo; regression asserts no secret in output |

No known unresolved P0/P1 has been confirmed within this cleanup scope. This
verdict requests independent cleanup audit, not release publication or a claim
that Maven and stage-1 completion are finished.

## Documentation gate and remaining limits

The consistency checker maintains an explicit inventory of current documents;
historical reports are excluded. Catalog flags drive forbidden contradictory
claims for generics, local/Git/lock and Maven. Current generic contracts are
mirrored and compared byte-for-byte. Exact counts are confined to this report;
other current documents describe passing gates without duplicating counts.

Known P2: local locks are nonportable, metadata errors can point to line 1,
project/doctor summaries are metadata rather than full cache-integrity proof
(use deps or check for graph validation), dependency diagnostics may lack an
exact source span, Git availability is required even offline, locking
has no timeout, and concurrent hostile mutation after validation is outside
the cooperative local cache model. Full Git content verification costs I/O;
Git submodules are unsupported and are rejected rather than silently ignored.
No new silent miscompile or nondeterminism
has been observed in the tested cases; this is not a proof of all programs.
