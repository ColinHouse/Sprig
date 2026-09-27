# Release validation — Sprig v0.3.0-alpha.1

This is the sole current validation authority. Published experimental Alpha:
compiler **0.3.0-alpha.1**, language **0.8-dev**, JDK17+, Apache-2.0.
Supported: **Linux/macOS**. **Windows is experimental and non-blocking**.

## Published artifact and evidence

- [Release](https://github.com/ColinHouse/Sprig/releases/tag/v0.3.0-alpha.1). Annotated tag targets `0d56ef63b4b473d33f5a5478bb367c31e37cdc74`.
- ZIP: `sprig-v0.3.0-alpha.1-jdk.zip`.
- ZIP SHA256: `8c90b39c431ddf1f002f0190e8fa9f300e7eee5f545586e377c882f60acbb114`.
- [Exact main CI](https://github.com/ColinHouse/Sprig/actions/runs/36304029516):
  Linux/macOS × JDK17/26 and Docs passed.
- [Release workflow](https://github.com/ColinHouse/Sprig/actions/runs/36304568192):
  clean exact-tag build/test/package, four supported-platform jobs downloading
  and testing the same ZIP, then publication passed.
- Published assets were downloaded again, their SHA256 and BUILD_INFO source
  identity verified, and `tools/check-sdk-archive.py --archive ...` passed locally.
- Implementation [PR #27](https://github.com/ColinHouse/Sprig/pull/27) and scoped
  release-hardening [PR #28](https://github.com/ColinHouse/Sprig/pull/28) are merged.

## Actual commands and results

| Command / evidence level | Result |
|---|---|
| `python3 scripts/verify.py` | Build, 72 top-level gates/cases, grammar and docs passed, zero failures |
| Semantic / numeric / correctness child suites | 54 / 66 / 22 passed |
| Recovery / independent acceptance | 467 (435 prefixes) / 52 passed |
| JSON / consistency / Agent tooling | 13 / 10 / 89 passed |
| CLI / project / dependencies / resolver cleanup | 19 rejected-option contracts / 18 / 21 / 37 passed |
| Maven fixture | 34 command checks plus independent graph/model/cache assertions passed |
| `python3 tools/test-grammar.py` | 24 syntax cases passed; syntax acceptance is not runtime proof |
| `python3 scripts/check-docs.py` | 18 executed snippets, VitePress production and link checks passed |
| `python3 tests/release_hardening/check_hardening.py` | Installed @std, traversal/shadow rejection, independent SHA256 reference, stale lock and Java-only static/option gates passed |
| `python3 tools/check-sdk-archive.py` | Spaced extraction, standalone @std run, Java-only/no class assertion, hello/probe/init/resolve/api/doctor and all three showcase projects passed |
| `python3 scripts/record-demo.py` | Actual queries, intentional Int/String failure, repair and JVM/JSON output passed |

Baseline main `64b1345`: original shell test driver reported 121 cases, zero
failures. The portable driver counts child suites as top-level gates, so 72 is
not comparable to 121 without the child counts. Tests distinguish parser,
static analysis, javac and actual JVM behavior. No golden/numeric assertion was
weakened. A deliberate untagged development release-package attempt was rejected.

## Implemented scope and repairs

Apache Resolver 1.9.24 / effective-model provider 3.9.11, 23 SHA-pinned libraries,
parents/BOMs/transitives/scopes/exclusions/mediation, schema3 identities/graph/hash/
classpath order, per-origin caches and offline reuse feed check/build/run/api/
doctor. No hand-written POM semantics, Maven CLI/plugins or build hooks.
Independent fixtures reproduced and reconfirmed staging JAR/POM tamper rejection,
repository-switch provenance and direct relocation rejection before lock overwrite.
Compiler dependency JARs do not leak into the application classpath.

`@std/module.spr` reuses existing explicit package syntax, works outside the
checkout and cannot be overridden by a dependency alias. Locks record bundled
std version and exact byte digest; old/mismatched identities require resolve.
Source checkouts normalize std bytes to LF; no implicit imports are added.
`build --emit-java-only` checks types and writes Java before javac, with structured
artifact paths. Existing syntax/numeric/nullability/generic/effect rules and spec/
were not redesigned. SDK IO/text/time/process and recursive JSON remain ordinary
Sprig; numbers preserve exact text without Any. Three useful showcases run.

A real clean-build ANTLR404 was fixed by using the correct Maven artifact filename
and matching the original pinned SHA256. Quick/JVM version titles and the
corrupted DESIGN_PRESSURE notice content were corrected and guarded. One current
report replaces the superseded implementation checklist. Main requires PR,
strict four supported OS/JDK checks plus Docs, resolved conversations, admins
included, no force push/deletion; required approval count is zero.
Untagged builds report development/based-on metadata, exact clean tagged artifacts
report prerelease. Current landing/install links name the published SDK.

## Limits and incomplete evaluation

Windows source CI on prior main `c434d1f` actually failed on both JDKs: CRLF probe,
native output encoding and fixture/path/cache failures. Preview tests are retained
in a separate non-blocking workflow; no Windows SDK correctness pass is claimed.
Fourteen real public contribution contracts (#13–26) remain available. One fresh
docs Agent produced an isolated recipe and reported focused source/SDK checks,
but its final turn hit quota before a complete final review record. Extra fresh
Agents hit thread limits; isolated CLI sessions hit sampling/account-model errors.
These are incomplete trials, not human feedback or measured contribution success.
Their patches are preserved locally and are not merged. External human/project
adoption remains unmeasured.

Direct Maven relocation, non-JAR runtime artifacts, authentication/package registry/
package publishing, complete Java generic/array/varargs/SAM adapters, formatter, LSP/IDE
and self-hosting remain absent. Cache locks are cooperative and lack a timeout;
local locks contain absolute paths. Effective POM activation can depend on the
resolving JVM/OS, then is frozen by the lock. Types do not prove algorithmic
correctness or numerical stability. The next adoption project is a small VS Code
extension; mixed Java/Sprig Web pressure testing and stage-1 follow later, without
language redesign by unrelated PR. See DESIGN_PRESSURE and known limitations.
