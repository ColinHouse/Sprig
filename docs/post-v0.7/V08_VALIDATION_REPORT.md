# Sprig v0.8-dev — current cleanup validation

**Verdict: NOT READY — CLEANUP BLOCKERS REMAIN (validation in progress).**

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

## Resolver correctness

Lock schema is **2**. Each edge has `id`, `owner` and package-local `name`;
`root/@a/@util` and `root/@b/@util` are different entries. Resolve and load use
identical IDs. Missing, duplicated or inconsistent IDs/owners/aliases, invalid
SHA identity uses UTF-8 percent-encoded alias components; dotted and Unicode
aliases retain their existing behavior without restricting manifest names.
SHA/digests and old schema are structured errors. Load checks edge kind, local
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
Final JDK17/JDK26, docs, grammar, SDK and exact-SHA hosted results are pending.

## Documentation gate and remaining limits

The consistency checker maintains an explicit inventory of current documents;
historical reports are excluded. Catalog flags drive forbidden contradictory
claims for generics, local/Git/lock and Maven. Current generic contracts are
mirrored and compared byte-for-byte. Exact counts are confined to this report;
other current documents describe passing gates without duplicating counts.

Known P2: local locks are nonportable, metadata errors can point to line 1,
Git availability is required to validate a Git checkout even offline, locking
has no timeout, and concurrent hostile mutation after validation is outside
the cooperative local cache model. Full Git content verification costs I/O;
Git submodules are unsupported and are rejected rather than silently ignored.
No new silent miscompile or nondeterminism
has been observed in the tested cases; this is not a proof of all programs.
