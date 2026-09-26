# Sprig v0.2.0-alpha.1 — validation record

Compiler `0.2.0-alpha.1` · language `0.8-dev` · lock schema **2** · JDK **17+**.
This is the sole current release validation record; capability scope is defined
by [implemented features](../FEATURE_STATUS_IMPLEMENTED.md) and executable Catalog.

## Exact source and release gate

Release source is the immutable annotated [v0.2.0-alpha.1 tag](https://github.com/ColinHouse/Sprig/tree/v0.2.0-alpha.1).
The dereferenced tag SHA identifies the tested release commit; the SDK's
BUILD_INFO.txt records its full SHA. The [main CI](https://github.com/ColinHouse/Sprig/actions/workflows/ci.yml?query=branch%3Amain)
checks JDK 17, JDK 26 and documentation on that exact main commit before tagging.
The [release workflow](https://github.com/ColinHouse/Sprig/actions/workflows/release.yml)
rebuilds/tests the tag, enforces compiler/tag version equality, packages and
smoke-tests before creating the [prerelease](https://github.com/ColinHouse/Sprig/releases/tag/v0.2.0-alpha.1).

## Scope

Explicit multi-parameter invariant generics, generic variants, Practical Strict
nullable arguments and Equatable; projects, local/Git Sprig packages, package-local
aliases, schema-2 edge IDs, exports and offline cached builds; Agent help/API/JSON
diagnostics, explicit JVM classpaths; checked integers and explicit conversions.
Resolver regressions cover scoped aliases/diamonds, symlinks, lock corruption,
Git byte/mode tampering (including index flags) and concurrent cache installation.
No parser/checker/codegen/resolver/runtime semantics or existing test oracle changed
in this documentation/metadata release preparation.

## Verification summary

Commands: scripts/build.sh, scripts/test.sh, ANTLR_JAR=tools/antlr-4.13.2-complete.jar
 tools/test-grammar.sh, scripts/check-docs.sh, scripts/package-alpha.sh and
python3 tools/check-sdk-archive.py. Run from clean source on JDK 17 and 26.

| Evidence | Required result |
|---|---|
| Full suite | 121 top-level checks, no failures |
| Semantic / numeric / correctness | 54 / 66 / 22 |
| Recovery | 467 checks, including 435 truncated prefixes |
| Acceptance / JSON / consistency | 52 / 13 / 10 |
| Agent tooling / CLI | 89 / 19 rejected cases plus E2E |
| Projects / original dependencies / resolver cleanup | 18 / 21 / 36 |
| Generics / adversarial projects | 61 programs + 209 prefixes / 31 |
| Reference grammar | 24 parser-only checks |
| Docs | 18 executed snippets, catalog consistency, local-link gate, production website |
| Archive | offline help/api/doctor/check/run/probe, matching metadata, docs links |

Parser-only evidence is not semantic/runtime evidence. JVM tests run generated
Java through javac and execution. Release completion additionally requires
redownloading the published ZIP/checksum, a fresh extract and version/capabilities/
hello check/run. Exact workflow URLs, final SHA and asset checksum are recorded
on the GitHub Release and maintenance PR after successful runs.

## Repository authority and cleanup

Tracked Markdown: **65 → 57**. Deleted root REVIEW_REPORT, pre-cleanup validation,
ALPHA2_VALIDATION, unpublished alpha.2 notes, two old blind-Agent reports, duplicate
spec/docs/GENERICS and the superseded bootstrap gap log. Its still-current probe
boundaries now live in STAGE1_ROADMAP. Generic contract: docs/GENERICS only;
website/SDK consume it. Kept published alpha.1 notes, historical v0.7 kit and unique
numeric design rationale; Git history preserves intermediate audits.

## Known limitations

Maven/project-aware Maven classpaths, publishing/registry, Comparable, inference,
variance, interfaces/traits and LSP/IDE are unsupported. Stage-1 is a probe, not
self-hosting. Local locks use nonportable absolute paths; metadata/runtime source
spans are limited; checkout verification adds IO and locks have no timeout;
submodules are unsupported. Offline Git builds need Git and a complete cache.
Float/JVM interop and algorithms still require numerical judgment. See
[KNOWN_LIMITATIONS](../KNOWN_LIMITATIONS.md). These declared Alpha limitations
are not claims of implementation and are not release blockers.

Release acceptance: all claimed features must pass the exact-SHA CI/archive gates;
unsupported capabilities must fail explicitly. No full-v0.8 or production guarantee.
