# Release validation — Sprig v0.3.0-alpha.1

This is the validation authority for the published `v0.3.0-alpha.1` snapshot;
it does not describe later unreleased `main`, which the next release record
replaces. Published experimental Alpha: compiler **0.3.0-alpha.1**, language
**0.8-dev**, JDK17+, Apache-2.0. Supported: **Linux/macOS**. **Windows is
experimental and non-blocking**.

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
package publishing, complete Java generic/array/varargs/SAM adapters, full LSP/IDE services
and self-hosting remain absent. Cache locks are cooperative and lack a timeout;
local locks contain absolute paths. Effective POM activation can depend on the
resolving JVM/OS, then is frozen by the lock. Types do not prove algorithmic
correctness or numerical stability. A local VS Code extension preview was published separately (see below); mixed
Java/Sprig Web pressure testing and stage-1 remain later projects, without language
redesign by unrelated PR. See DESIGN_PRESSURE and known limitations.


## VS Code extension — local preview 0.1.0 (2026-09-27)

`editors/vscode/` contains a separate TypeScript desktop extension; compiler and
language versions are unchanged. The v0.3 SDK ZIP is unchanged and does not include
this extension. No Marketplace publication or publisher ownership is claimed.

Delivered scope: lexer-based TextMate highlighting and four-space editing defaults,
saved-file/entry-graph checks with Problems and expected/actual types, error-code
explanation, explicit finite JVM Run, Build and side-by-side Java-only viewing.
Compiler processes use argument arrays without a shell; workspace trust gates
execution, stale checks are canceled, and outputs use extension storage.

Actual local evidence: macOS, Node24.16.0, TypeScript5.9.3, JDK26.0.1, VS Code1.139.0.

| Check | Actual result |
|---|---|
| `npm test` in `editors/vscode/` | 11 passed, zero failures: actual TextMate/Oniguruma + real CLI/JVM |
| `VSCODE_EXECUTABLE_PATH=... npm run test:host` | Real host activation, .spr registration, static error/repair, on-save error/repair, JVM Run, Java view, imported-file diagnostics, entry failure aggregation and project isolation passed |
| `SPRIG_TEST_RESTRICTED=1 ... npm run test:host` | Real Restricted Mode keeps language/highlighting and blocks compiler commands |
| `npm run package` + VSIX ZIP inspection | Packaged manifest, compiled JS, grammar/config, README and Apache license; no node_modules/source/tests/maps |
| VS Code CLI `--install-extension` with isolated profile | Installation succeeded; `--list-extensions --show-versions` returned `colinhouse.sprig-language@0.1.0` |
| Host with `SPRIG_EXTENSION_PATH` pointing to installed VSIX files | The same actual check/run/Java-view integration passed using packaged code |
| `python3 scripts/verify.py` | 72 original gates, 24 grammar cases, 18 snippets, docs/site checks and editor test/package gates passed |
| Highlighting preview program | Recursive variant/match Visitor ran and printed 42 |

Tests first exposed missing implementations, a wrong test assumption about allowed
String concatenation, project-entry failures overwritten by a clean unused-file
result, uncaught JSON-null responses, and selecting a standard-library Java file when the
entry class name was package-qualified. Hosted clean-checkout packaging also
exposed a missing dist-directory creation, which was reproduced locally with the
previous artifacts moved aside and fixed in the package command. Cross-platform
host fixtures now explicitly resolve their project lock before testing types; the
original CI correctly reported a missing lock. Host startup also handles the newer
macOS executable rename from Electron to Code. Expectations were corrected only for the
incorrect language assumption; actual adapter defects were fixed. The official
host test runner always disables workspace trust, so the Restricted Mode test uses
an explicit separate launch rather than pretending the normal host test covers it.

Linux/macOS × VS Code1.95.3/current stable Extension Host CI passed all four
jobs on implementation `78e9e8d868d38814a6bf4c09def93e8b301e19d8`: [CI run](https://github.com/ColinHouse/Sprig/actions/runs/36308328496). Each job ran
real normal and Restricted Mode hosts, CLI/JVM checks and VSIX packaging. This
platform evidence is distinct from the local host evidence above. Windows is unverified
preview. No interactive terminal/debugger, formatter, completion, navigation,
semantic tokens or LSP is provided. Run is bounded (120s / 8 MB by default), shows
output at completion and uses the active saved file. Save all dirty project files
first. Windows preview cannot guarantee child-process cancellation. Compiler
Unicode starts map to UTF-16; ends inside astral-character tokens may remain
approximate because the compiler mixes start/length units, explicitly documented
without changing the compiler in this editor milestone.
