# Release validation — v0.3 developer attraction milestone

Source candidate: compiler **0.3.0-alpha.1**, language **0.8-dev**, JDK17+,
Apache-2.0. Public latest remains v0.2.0-alpha.1. No v0.3 publication or hosted
Windows pass is claimed yet. This is the sole current validation authority.

## Baseline and local gates (2026-09-27)

Baseline main `64b13452471c35e92d7fc45cd6857bdc5fa9d632`: fresh build passed;
original `scripts/test.sh`: **121 passed, 0 failed**. The portable driver now
counts child suites as gates rather than every child case; its 71 count is not
comparable to the former shell count without reading the child results.

| Actual command | Result |
|---|---|
| `python3 scripts/verify.py` | Passed build, 71 top-level gates/cases, grammar, snippets and production docs |
| Child semantic / numeric / correctness | 54 / 66 / 22 passed, zero failures |
| Recovery | 467 passed including 435 truncation prefixes |
| Independent acceptance / JSON / consistency | 52 / 13 / 10 passed |
| Agent tooling / CLI / project / dependency | 89 / 19 rejected-option contracts / 18 / 21 passed |
| Maven fixture | 34 command checks plus independent graph/model/cache assertions passed |
| Std / three showcases | Actual check/javac/JVM/JSON/API/transitive/offline behaviors passed |
| `python3 tools/test-grammar.py` | 24 grammar cases passed |
| `python3 scripts/check-docs.py` | 18 executed snippets, VitePress production build, local links passed |
| `python3 scripts/record-demo.py` | Actual query, deliberate Int/String diagnostic, repair, JVM audit/JSON output |
| `python3 scripts/package-alpha.py --skip-build`; `python3 tools/check-sdk-archive.py` | Development ZIP with 23 pinned resolver libraries: spaced extraction, init/resolve/run, all three SDK showcases passed |

Final targeted Git checkout-mode/newline changes were rebuilt: cleanup 37 checks
(POSIX owner-execute mutation included) and dependencies 21 passed. A new ZIP
from that build passed the actual extracted SDK/showcase gate. Exact clean-commit
hosted evidence remains pending; no unrun gate is treated as passed.

## Correctness findings and repair evidence

- Real Central commons-text initially resolved only its direct JAR. Default
  Maven session omitted JVM model properties and ignored invalid descriptors.
  Explicit properties + strict descriptor policy fixed commons-lang3 mediation;
  real `resolve` and `run --offline` passed. Missing/invalid POMs now fail.
- Independent review reproduced staging-cache corruption being re-locked and
  false provenance after repository switching. Per-origin SHA256 staging
  markers/initial source-checksum validation and URL-derived cache namespaces
  fix both; independent fresh temporary fixtures reconfirmed the repairs.
- Direct POM relocation previously produced an unusable successful lock. Resolve
  now rejects it before replacing any lock and asks for the new exact coordinate.
  This limitation is documented; transitive mediation still belongs to Resolver.
- Compiler library isolation prevents resolver implementation JARs from becoming
  accidental application imports. The same locked order feeds reflection,
  javac and child JVM; cached JAR/POM tampering/missing bytes fail explicitly.
- A prior Git test changed HOME without changing JVM user.home, so its missing
  cache claim was invalid. It now isolates user.home. Offline cache success is
  tested with the origin unavailable while retaining Git for integrity checks.
- First integrated verification had three failures: old version assertion,
  flawed Git no-executable fixture, and a checksum test against a stale binary.
  They were corrected and rebuilt; final portable full verification passed.
  No numeric or golden assertion was weakened.

- First public candidate CI failed in every clean build with an ANTLR download
  HTTP404: the Maven artifact filename differs from the local SDK filename.
  Corrected the upstream URL; downloaded bytes match the pinned SHA256, and
  an actual build using a fresh external ANTLR path passed. Hosted rerun pending.

## Scope and limits

Maven uses pinned Apache Resolver/model-provider libraries, no POM parser,
Maven CLI/plugins or build hooks. Schema3 freezes coordinates, models, SHA256,
graph and order. Small std is ordinary Sprig, JSON preserves exact numeric
lexemes without Any. Grammar and language/numeric/nullability/generic semantics
were not redesigned; spec/ is unchanged.

Authentication/registry/publishing, direct Maven relocation, non-JAR runtime
artifacts, complete Java generic/array/varargs adapters, LSP/IDE and self-hosting
remain absent. Cache locks have no timeout and are cooperative, not a sandbox.
POM activation may depend on the resolving JVM/OS; locks freeze that choice.
Type safety does not certify numerical stability or application correctness.

## Remaining release gates

- Hosted Linux/macOS/Windows × JDK17/26 on the exact source SHA.
- Fourteen real public issue contracts were seeded as issues #13–26, with eight
  consistent labels and scope/non-goal/test constraints. Fresh blind contributor
  trials and measured failures/iterations/patch review are pending. No human
  feedback is invented.
- Clean exact-tag package, six hosted tests of that same downloaded ZIP,
  publication and final downloaded asset checksum/source verification.

Public v0.2 tag target: `677429be905750b975d491f8a5c60e29d8ebaee2`;
ZIP SHA256: `66453a482b449969f95ef65dffea45b1f33a4abfdc7d02a163c83e23891b3433`.
Historical scope stays in release notes/Git history rather than a competing report.
